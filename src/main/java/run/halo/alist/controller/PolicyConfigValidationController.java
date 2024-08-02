package run.halo.alist.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;
import run.halo.alist.config.AListProperties;
import run.halo.alist.dto.AListResult;
import run.halo.alist.dto.request.AListGetFileInfoReq;
import run.halo.alist.dto.response.AListGetCurrentUserInfoRes;
import run.halo.alist.dto.response.AListGetFileInfoRes;
import run.halo.alist.endpoint.AListAttachmentHandler;
import run.halo.alist.exception.AListIllegalArgumentException;
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

    @PostMapping("/configs/-/verify")
    public Mono<Void> validatePolicyConfig(@RequestBody AListProperties properties) {
        return handler.removeTokenCache(properties)
            .then(
                handler.auth(properties)
                    .flatMap(token -> handler.getWebClients()
                        .get(properties.getSite())
                        .get()
                        .uri("/api/me")
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .retrieve()
                        .bodyToMono(
                            new ParameterizedTypeReference<AListResult<AListGetCurrentUserInfoRes>>() {
                            })
                        .flatMap(response -> {
                            if (response.getCode().equals("401")) {
                                return Mono.error(new AListIllegalArgumentException(
                                    "Current user is disabled"));
                            }
                            if (!response.getCode().equals("200")) {
                                return Mono.error(new AListIllegalArgumentException(
                                    "Wrong Username Or Password"));
                            }
                            AListGetCurrentUserInfoRes userInfo = response.getData();
                            return handler.getWebClients()
                                .get(properties.getSite())
                                .post()
                                .uri("/api/fs/get")
                                .header(HttpHeaders.AUTHORIZATION, token)
                                .body(Mono.just(AListGetFileInfoReq.builder()
                                        .path("/")
                                        .build()),
                                    AListGetFileInfoReq.class)
                                .retrieve()
                                .bodyToMono(
                                    new ParameterizedTypeReference<AListResult<AListGetFileInfoRes>>() {
                                    })
                                .flatMap(res -> {
                                    // 验证当前基本路径是否可用
                                    if (!res.getCode().equals("200")) {
                                        return Mono.error(
                                            new AListIllegalArgumentException(res.getMessage()));
                                    }
                                    // 管理员用户拥有所有权限
                                    if (userInfo.getRole() == 2) {
                                        return Mono.empty();
                                    }
                                    // 普通用户验证权限
                                    int permission = userInfo.getPermission();
                                    StringBuilder sb = new StringBuilder();
                                    if ((permission & 2) == 0) {
                                        sb.append("无需密码访问权限 ");
                                    }
                                    if ((permission & 8) == 0) {
                                        sb.append("创建目录或上传权限 ");
                                    }
                                    if ((permission & 128) == 0) {
                                        sb.append("删除权限 ");
                                    }
                                    if (!sb.isEmpty()) {
                                        sb.append("未开启");
                                        return Mono.error(
                                            new AListIllegalArgumentException(sb.toString()));
                                    }
                                    return Mono.empty();
                                });

                        })

                    )
            );
    }
}

