package run.halo.alist;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
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
            .flatMap(token -> handler.webClients.get(properties.getSite()).get()
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
                            .switchIfEmpty(Mono.error(new AListException(
                                "The mount path does not exist")))
                            .all(volume -> !volume.isDisabled())
                            .flatMap(isValid -> {
                                if (isValid) {
                                    return Mono.empty();
                                }
                                return Mono.error(new AListException(
                                    "The storage is disabled"));
                            });
                    }
                    return Mono.error(new AListException(
                        "Wrong Username Or Password"));
                }));
    }
}

