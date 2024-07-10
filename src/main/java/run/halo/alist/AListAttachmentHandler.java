package run.halo.alist;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Constant;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.core.extension.attachment.endpoint.SimpleFilePart;
import run.halo.app.extension.ConfigMap;
import run.halo.app.extension.Metadata;
import run.halo.app.extension.ReactiveExtensionClient;
import run.halo.app.extension.Secret;
import run.halo.app.infra.utils.JsonUtils;

/**
 * 文件描述
 *
 * @author： <a href="https://roozen.top">Roozen</a>
 * @date: 2024/7/3
 */
@Slf4j
@Extension
@Component
public class AListAttachmentHandler implements AttachmentHandler {

    @Autowired
    ReactiveExtensionClient client;

    WebClient webClient = null;
    AListProperties properties = null;
    String token = "";

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext).filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                properties = getProperties(context.configMap());
                if (webClient == null) {
                    webClient = WebClient.builder()
                        .baseUrl(properties.getSite())
                        .build();
                }

                var secretName = properties.getSecretName();
                return client.fetch(Secret.class, secretName)
                    .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Secret " + secretName + " not found")))
                    .flatMap(secret -> {
                        var stringData = secret.getStringData();
                        var usernameKey = "username";
                        var passwordKey = "password";
                        if (stringData == null
                            || !(stringData.containsKey(usernameKey) && stringData.containsKey(
                            passwordKey))) {
                            return Mono.error(new IllegalArgumentException(
                                "Secret " + secretName
                                    + " does not have username or password key"));
                        }
                        var username = stringData.get(usernameKey);
                        var password = stringData.get(passwordKey);
                        return webClient.post()
                            .uri("/api/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .body(Mono.just(
                                AListLoginReq.builder()
                                    .username(username)
                                    .password(password)
                                    .build()), AListLoginReq.class)
                            .retrieve()
                            .bodyToMono(
                                new ParameterizedTypeReference<AListResult<AListLoginRes>>() {
                                });
                    });
            }).flatMap(response -> {
                if (response.getCode().equals("200")) {
                    log.info("AList 登录成功");
                    this.token = response.getData().getToken();
                }
                return webClient.put()
                    .uri("/api/fs/put")
                    .header("Authorization", token)
                    .header("File-Path", URLEncoder.encode(
                        properties.getPath() + "/" + uploadContext.file().name(),
                        StandardCharsets.UTF_8))
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .body(uploadContext.file().content().cache(), DataBuffer.class)
                    .retrieve()
                    .bodyToMono(
                        new ParameterizedTypeReference<AListResult<String>>() {
                        });
            })
            .flatMap(response -> {
                if (response.getCode().equals("200")) {
                    log.info("AList 上传成功");
                }
                return webClient.post()
                    .uri("/api/fs/get")
                    .header("Authorization", token)
                    .body(Mono.just(
                            AListGetFileInfoReq
                                .builder()
                                .path(properties.getPath() + "/" + uploadContext.file().name())
                                .build()),
                        AListGetFileInfoReq.class)
                    .retrieve()
                    .bodyToMono(
                        new ParameterizedTypeReference<AListResult<AListGetFileInfoRes>>() {
                        });
            })
            .map(response -> {
                if (response.getCode().equals("200")) {
                    log.info("AList 上传成功");
                }
                log.info(response.getData().toString());
                var metadata = new Metadata();
                metadata.setName(UUID.randomUUID().toString());
                metadata.setAnnotations(
                    Map.of(Constant.EXTERNAL_LINK_ANNO_KEY,
                        UriUtils.encodePath(
                            properties.getSite() + "/d" + properties.getPath() + "/"
                                + response.getData().getName(),
                            StandardCharsets.UTF_8)));
                var spec = new Attachment.AttachmentSpec();
                SimpleFilePart file = (SimpleFilePart) uploadContext.file();
                spec.setDisplayName(file.filename());
                spec.setMediaType(file.mediaType().toString());
                spec.setSize(response.getData().getSize());

                var attachment = new Attachment();
                attachment.setMetadata(metadata);
                attachment.setSpec(spec);
                return attachment;
            });
    }

    private AListProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, AListProperties.class);
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext).filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                    properties = getProperties(context.configMap());
                    if (webClient == null) {
                        webClient = WebClient.builder()
                            .baseUrl(properties.getSite())
                            .build();
                    }
                    return webClient.post()
                        .uri("/api/fs/remove")
                        .header("Authorization", token)
                        .body(Mono.just(AListRemoveFileReq.builder()
                            .dir(properties.getPath())
                            .names(List.of(deleteContext.attachment().getSpec().getDisplayName()))
                            .build()), AListGetFileInfoReq.class)
                        .retrieve()
                        .bodyToMono(
                            new ParameterizedTypeReference<AListResult<String>>() {
                            });
                }
            )
            .map(response -> {
                if (response.getCode().equals("200")) {
                    log.info("AList 删除成功");
                }
                return deleteContext.attachment();
            });
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
        Duration ttl) {
        if (!this.shouldHandle(policy)) {
            return Mono.empty();
        }
        return webClient.post()
            .uri("/api/fs/get")
            .header("Authorization", token)
            .body(Mono.just(
                    AListGetFileInfoReq
                        .builder()
                        .path(properties.getPath() + "/" + attachment.getSpec().getDisplayName())
                        .build()),
                AListGetFileInfoReq.class)
            .retrieve()
            .bodyToMono(
                new ParameterizedTypeReference<AListResult<AListGetFileInfoRes>>() {
                })
            .map(response -> URI.create(UriUtils.encodePath(
                properties.getSite() + "/d" + properties.getPath() + "/"
                    + response.getData().getName(),
                StandardCharsets.UTF_8)));
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        if (!this.shouldHandle(policy)) {
            return Mono.empty();
        }
        return webClient.post()
            .uri("/api/fs/get")
            .header("Authorization", token)
            .body(Mono.just(
                    AListGetFileInfoReq
                        .builder()
                        .path(properties.getPath() + "/" + attachment.getSpec().getDisplayName())
                        .build()),
                AListGetFileInfoReq.class)
            .retrieve()
            .bodyToMono(
                new ParameterizedTypeReference<AListResult<AListGetFileInfoRes>>() {
                })
            .map(response -> {
                URI uri = URI.create(UriUtils.encodePath(
                    properties.getSite() + "/d" + properties.getPath() + "/"
                        + response.getData().getName(),
                    StandardCharsets.UTF_8));
                log.info("alist getPermalink:" + uri.toString());
                return uri;
            });
    }

    boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        return "alist".equals(templateName);
    }
}