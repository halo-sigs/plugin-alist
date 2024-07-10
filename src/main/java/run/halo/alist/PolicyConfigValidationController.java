package run.halo.alist;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.DefaultDataBufferFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import run.halo.app.infra.utils.PathUtils;
import run.halo.app.plugin.ApiVersion;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/10
 */
@ApiVersion("alist.halo.run/v1alpha1")
@RestController
@RequiredArgsConstructor
@Slf4j
public class PolicyConfigValidationController {
    private final AListAttachmentHandler handler;

    @PostMapping("/policies/alist/validation")
    public Mono<Void> validatePolicyConfig(@RequestBody AListProperties properties) {
        return handler.auth(properties)
            .flatMap(token -> handler.webClient.get()
                .uri("/api/admin/storage/list")
                .header("Authorization", token)
                .retrieve()
                .bodyToMono(
                    new ParameterizedTypeReference<AListResult<AListStorageListRes>>() {
                    })
                .flatMap(response -> {
                    if (response.getCode().equals("200")) {
                        return Flux.fromIterable(response.getData().getContent())
                            .filter(volume -> Objects.equals(volume.getMountPath(),
                                properties.getPath()))
                            .all(volume -> !volume.isDisabled())
                            .thenEmpty(Mono.error(new IllegalArgumentException("AList: The mount address does not exist")));
                    }
                    return Mono.error(new IllegalArgumentException(
                        "AList: Wrong Username Or Password"));
                }));
    }

    private Flux<DataBuffer> readImage() {
        DefaultResourceLoader resourceLoader = new DefaultResourceLoader(this.getClass()
            .getClassLoader());
        String path = PathUtils.combinePath("validation.jpg");
        String simplifyPath = StringUtils.cleanPath(path);
        Resource resource = resourceLoader.getResource(simplifyPath);
        return DataBufferUtils.read(resource, new DefaultDataBufferFactory(), 1024);
    }
}

