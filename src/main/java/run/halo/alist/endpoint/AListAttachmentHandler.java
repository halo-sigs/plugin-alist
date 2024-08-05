package run.halo.alist.endpoint;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.netty.channel.ChannelOption;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.alist.config.AListProperties;
import run.halo.alist.dto.AListResult;
import run.halo.alist.dto.request.AListGetFileInfoReq;
import run.halo.alist.dto.request.AListLoginReq;
import run.halo.alist.dto.request.AListRemoveFileReq;
import run.halo.alist.dto.response.AListGetCurrentUserInfoRes;
import run.halo.alist.dto.response.AListGetFileInfoRes;
import run.halo.alist.dto.response.AListLoginRes;
import run.halo.alist.dto.response.AListUploadAsTaskRes;
import run.halo.alist.exception.AListIllegalArgumentException;
import run.halo.alist.exception.AListRequestErrorException;
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
 * AListAttachmentHandler
 *
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/3
 */
@Slf4j
@Extension
@Component
public class AListAttachmentHandler implements AttachmentHandler {

    private final ReactiveExtensionClient client;

    private AListProperties properties;

    @Getter
    private final Map<String, WebClient> webClients;

    private final Cache<String, String> tokenCache;

    public AListAttachmentHandler(ReactiveExtensionClient client) {
        this.client = client;
        this.webClients = new HashMap<>();
        this.tokenCache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.DAYS)
            .build();
    }

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        FilePart file = uploadContext.file();
        return Mono.just(uploadContext)
            .filter(context -> this.shouldHandle(context.policy()))
            .map(UploadContext::configMap)
            .map(this::getProperties)
            .flatMap(this::auth)
            .flatMap(token -> file.content()
                .map(dataBuffer -> (long) dataBuffer.readableByteCount())
                .reduce(Long::sum)
                .flatMap(fileSize -> webClients.get(properties.getSite())
                    .put()
                    .uri("/api/fs/put")
                    .header(HttpHeaders.AUTHORIZATION, token)
                    .header("File-Path", UriUtils.encodePath(
                        properties.getPath() + "/" + file.name(),
                        StandardCharsets.UTF_8))
                    .header("As-Task", "true")
                    .contentType(MediaType.APPLICATION_OCTET_STREAM)
                    .contentLength(fileSize)
                    .body(file.content(), DataBuffer.class)
                    .retrieve()
                    .bodyToMono(
                        new ParameterizedTypeReference<AListResult<AListUploadAsTaskRes>>() {
                        })
                    .flatMap(response -> {
                        if (response.getCode().equals("200")) {
                            log.info("[AList Info] :  Upload file {} successfully",
                                file.name());
                            return Mono.just(fileSize);
                        }
                        return Mono.error(new AListRequestErrorException(response.getMessage()));
                    }))
            )
            .map(fileSize -> {
                var metadata = new Metadata();
                metadata.setName(UUID.randomUUID().toString());
                metadata.setAnnotations(
                    Map.of(Constant.EXTERNAL_LINK_ANNO_KEY, ""));

                var spec = new Attachment.AttachmentSpec();
                SimpleFilePart simpleFilePart = (SimpleFilePart) file;
                spec.setDisplayName(simpleFilePart.filename());
                spec.setMediaType(simpleFilePart.mediaType().toString());
                spec.setSize(fileSize);

                var attachment = new Attachment();
                attachment.setMetadata(metadata);
                attachment.setSpec(spec);
                return attachment;
            });
    }

    public Mono<String> auth(AListProperties properties) {
        this.properties = properties;
        WebClient webClient = webClients.computeIfAbsent(properties.getSite(),
            k -> {
                // 创建一个HttpClient实例，设置连接和读取超时时间
                HttpClient httpClient = HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000) // 连接超时时间，单位毫秒
                    .responseTimeout(Duration.ofMinutes(10)); // 响应超时时间
                // 使用上面的HttpClient实例创建WebClient
                return WebClient.builder()
                    .baseUrl(properties.getSite())
                    .clientConnector(new ReactorClientHttpConnector(httpClient))
                    .build();
            });

        String secretName = properties.getSecretName();
        if (tokenCache.getIfPresent(properties.getTokenKey()) != null) {
            return Mono.just(
                Objects.requireNonNull(tokenCache.getIfPresent(properties.getTokenKey())));
        }

        return client.fetch(Secret.class, secretName)
            .switchIfEmpty(Mono.error(new AListIllegalArgumentException(
                "Secret " + secretName + " not found")))
            .flatMap(secret -> {
                var stringData = secret.getStringData();
                var usernameKey = "username";
                var passwordKey = "password";
                if (stringData == null
                    || !(stringData.containsKey(usernameKey) && stringData.containsKey(
                    passwordKey))) {
                    return Mono.error(new AListIllegalArgumentException(
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
                        })
                    .onErrorMap(WebClientRequestException.class,
                        e -> new AListIllegalArgumentException(e.getMessage()));
            }).flatMap(response -> {
                if (response.getCode().equals("200")) {
                    log.info("[AList Info] :  Login successfully");
                    return Mono.just(
                        tokenCache.get(properties.getTokenKey(),
                            k -> response.getData().getToken()));
                }
                return Mono.error(new AListIllegalArgumentException(
                    "Wrong Username Or Password"));
            });
    }

    private AListProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        AListProperties aListProperties =
            JsonUtils.jsonToObject(settingJson, AListProperties.class);
        if (aListProperties.getPath().equals("/")) {
            aListProperties.setPath("");
        }
        return aListProperties;
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext).filter(context -> this.shouldHandle(context.policy()))
            .map(DeleteContext::configMap)
            .map(this::getProperties)
            .flatMap(this::auth)
            .flatMap(token -> webClients.get(properties.getSite())
                .post()
                .uri("/api/fs/remove")
                .header(HttpHeaders.AUTHORIZATION, token)
                .body(Mono.just(AListRemoveFileReq.builder()
                    .dir(properties.getPath())
                    .names(List.of(deleteContext.attachment().getSpec().getDisplayName()))
                    .build()), AListGetFileInfoReq.class)
                .retrieve()
                .bodyToMono(
                    new ParameterizedTypeReference<AListResult<String>>() {
                    })
                .flatMap(response -> {
                    if (response.getCode().equals("200")) {
                        log.info("[AList Info] :  Delete file {} successfully",
                            deleteContext.attachment().getSpec().getDisplayName());
                        return Mono.just(token);
                    }
                    return Mono.error(new AListRequestErrorException(response.getMessage()));
                })
            )
            .map(token -> deleteContext.attachment());
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
        Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        return Mono.just(policy).filter(this::shouldHandle)
            .flatMap(p -> auth(getProperties(configMap)))
            .flatMap(token -> webClients.get(properties.getSite())
                .post()
                .uri("/api/fs/get")
                .header(HttpHeaders.AUTHORIZATION,
                    tokenCache.getIfPresent(properties.getTokenKey()))
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
                .flatMap(response -> {
                    if (response.getCode().equals("200")) {
                        log.info("[AList Info] :  Got file {} successfully",
                            attachment.getSpec().getDisplayName());
                        return Mono.just(response);
                    }
                    return Mono.error(new AListRequestErrorException(response.getMessage()));
                }))
            .flatMap(response -> webClients.get(properties.getSite())
                .get()
                .uri("/api/me")
                .header(HttpHeaders.AUTHORIZATION,
                    tokenCache.getIfPresent(properties.getTokenKey()))
                .retrieve()
                .bodyToMono(
                    new ParameterizedTypeReference<AListResult<AListGetCurrentUserInfoRes>>() {
                    })
                .map(res -> UriComponentsBuilder.fromHttpUrl(properties.getSite())
                    .path("/d{basePath}{path}/{name}")
                    .queryParamIfPresent("sign",
                        Optional.ofNullable(response.getData().getSign()).filter(s -> !s.isEmpty()))
                    .buildAndExpand(
                        res.getData().getBasePath(),
                        properties.getPath(),
                        response.getData().getName()
                    )
                    .toUri()));
    }

    boolean shouldHandle(Policy policy) {
        if (policy == null || policy.getSpec() == null ||
            policy.getSpec().getTemplateName() == null) {
            return false;
        }
        String templateName = policy.getSpec().getTemplateName();
        return "alist".equals(templateName);
    }

    public Mono<Void> removeTokenCache(AListProperties properties) {
        tokenCache.invalidate(properties.getTokenKey());
        return Mono.empty();
    }

}