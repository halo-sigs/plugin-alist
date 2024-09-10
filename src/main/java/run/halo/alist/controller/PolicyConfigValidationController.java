package run.halo.alist.controller;

import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import run.halo.alist.config.AListProperties;
import run.halo.alist.dto.AListResult;
import run.halo.alist.dto.response.AListGetCurrentUserInfoRes;
import run.halo.alist.endpoint.AListAttachmentHandler;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.plugin.ApiVersion;

/**
 * 存储策略验证控制器
 *
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/10
 */
@ApiVersion("alist.storage.halo.run/v1alpha1")
@RestController
@RequiredArgsConstructor
@Slf4j
public class PolicyConfigValidationController {

    private final AListAttachmentHandler handler;

    private final ReactiveExtensionClient client;

    @PostMapping("/configs/-/verify")
    public Mono<AListGetCurrentUserInfoRes> validatePolicyConfig(
        @RequestBody AListProperties properties) {
        // check if the current user has permissions
        return handler.getToken(properties)
            .flatMap(token -> {
                var getMeUri = UriComponentsBuilder.fromHttpUrl(properties.getSite().toString())
                    .path("/api/me")
                    .toUriString();
                return handler.getWebClient().get().uri(getMeUri)
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .retrieve()
                    .bodyToMono(
                        new ParameterizedTypeReference<AListResult<AListGetCurrentUserInfoRes>>() {
                        })
                    .flatMap(result -> {
                        if (!Objects.equals(HttpStatus.OK.value(), result.getCode())) {
                            return Mono.error(
                                new ServerWebInputException(
                                    "Failed to get AList user info: " + result.getMessage()
                                )
                            );
                        }
                        return Mono.just(result.getData());
                    })
                    .flatMap(userInfo -> {
                        if (userInfo.isDisabled()) {
                            return Mono.error(
                                new ServerWebInputException("Current user is disabled")
                            );
                        }
                        // role = 2 means admin which permissions is 0.
                        if (userInfo.getRole() != 2) {
                            int perm = userInfo.getPermission();
                            // Permission "Make dir or upload": 0b1000 = 8
                            // Permission "Delete": 0b1000_0000 = 128
                            if ((perm & 0b1000_0000) == 0 || (perm & 0b1000) == 0) {
                                return Mono.error(
                                    new ServerWebInputException("""
                                        Current user has insufficient permissions. \
                                        Make sure select "Make dir or upload" and "Delete" \
                                        permissions for the user in AList.\
                                        """
                                    )
                                );
                            }
                        }
                        return Mono.just(userInfo);
                    });
            });
    }
}

