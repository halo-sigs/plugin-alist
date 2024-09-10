package run.halo.alist.endpoint;

import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.web.util.UriComponentsBuilder.fromHttpUrl;

import io.netty.channel.ChannelOption;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.pf4j.Extension;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebInputException;
import org.springframework.web.util.UriComponentsBuilder;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import run.halo.alist.config.AListProperties;
import run.halo.alist.dto.AListResult;
import run.halo.alist.dto.request.AListGetFileInfoReq;
import run.halo.alist.dto.request.AListLoginReq;
import run.halo.alist.dto.request.AListRemoveFileReq;
import run.halo.alist.dto.response.AListGetFileInfoRes;
import run.halo.alist.dto.response.AListLoginRes;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
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

    public static final String FILE_PATH_ANNO = "alist.storage.halo.run/file-path";

    private final ReactiveExtensionClient client;

    @Getter
    private final WebClient webClient;

    public AListAttachmentHandler(ReactiveExtensionClient client) {
        this.client = client;
        var httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 5000)
            .responseTimeout(Duration.ofHours(1));
        this.webClient = WebClient.builder()
            .clientConnector(new ReactorClientHttpConnector(httpClient))
            .build();
    }

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        return Mono.just(uploadContext)
            .filter(context -> this.shouldHandle(context.policy()))
            .map(context -> getProperties(context.configMap()))
            .flatMap(properties -> {
                var file = uploadContext.file();
                var sizeOfContent = file.content()
                    .map(dataBuffer -> (long) dataBuffer.readableByteCount())
                    .reduce(Long::sum);
                return Mono.zip(sizeOfContent, getToken(properties)).flatMap(tuple2 -> {
                    var contentLength = tuple2.getT1();
                    var token = tuple2.getT2();
                    var uploadUri = fromHttpUrl(properties.getSite().toString())
                        .path("/api/fs/put")
                        .build()
                        .toUri();

                    return renameFilename(token, properties, file.filename()).flatMap(filename -> {
                        var filePathBuilder =
                            UriComponentsBuilder.fromPath(properties.getPath())
                                .pathSegment(filename);
                        var encodedFilePath = filePathBuilder.toUriString();
                        var rawFilePath = filePathBuilder.build().toString();
                        log.info("Uploading file {} to {}", filename, uploadUri);
                        // check the file is already exists
                        return webClient.put().uri(uploadUri)
                            .header(HttpHeaders.AUTHORIZATION, token)
                            .header("File-Path", encodedFilePath)
                            .contentType(MediaType.APPLICATION_OCTET_STREAM)
                            .contentLength(contentLength)
                            .body(file.content(), DataBuffer.class)
                            .retrieve()
                            .bodyToMono(
                                new ParameterizedTypeReference<AListResult<Void>>() {
                                })
                            .flatMap(result -> {
                                if (!Objects.equals(OK.value(), result.getCode())) {
                                    return Mono.error(new ServerWebInputException(
                                        "Failed to upload file: " + result.getMessage())
                                    );
                                }
                                log.info("Uploaded file {} to {}", filename, uploadUri);
                                var metadata = new Metadata();
                                metadata.setGenerateName("alist-");
                                var annotations = new HashMap<String, String>();
                                metadata.setAnnotations(annotations);
                                annotations.put(FILE_PATH_ANNO, rawFilePath);

                                var spec = new Attachment.AttachmentSpec();
                                spec.setDisplayName(filename);

                                Optional.ofNullable(file.headers().getContentType())
                                    .map(Objects::toString)
                                    .ifPresent(spec::setMediaType);
                                spec.setSize(contentLength);

                                var attachment = new Attachment();
                                attachment.setMetadata(metadata);
                                attachment.setSpec(spec);
                                return Mono.just(attachment);
                            });
                    });
                });
            });
    }

    public Mono<String> getToken(AListProperties properties) {
        return client.get(Secret.class, properties.getSecretName())
            .flatMap(secret -> {
                var data = secret.getStringData();
                Mono<String> credentialNotFound = Mono.error(
                    new ServerWebInputException(
                        "Username or password not found in secret "
                            + secret.getMetadata().getName()
                    )
                );
                if (data == null) {
                    return credentialNotFound;
                }
                var username = data.get("username");
                var password = data.get("password");
                if (StringUtils.isAnyBlank(username, password)) {
                    return credentialNotFound;
                }

                return Mono.just(AListLoginReq.builder()
                    .username(username)
                    .password(password)
                    .build());
            })
            .flatMap(loginBody -> {
                var site = properties.getSite();
                var loginUri = fromHttpUrl(site.toString())
                    .path("/api/auth/login")
                    .build()
                    .toUri();
                return webClient.post().uri(loginUri)
                    .bodyValue(loginBody)
                    .retrieve()
                    .bodyToMono(new ParameterizedTypeReference<AListResult<AListLoginRes>>() {
                    })
                    .flatMap(result -> {
                        if (!Objects.equals(OK.value(), result.getCode())) {
                            return Mono.error(new ServerWebInputException(
                                "Failed to login to " + site + ": " + result.getMessage()
                            ));
                        }
                        return Mono.just(result.getData().getToken());
                    });
            });
    }

    private AListProperties getProperties(ConfigMap configMap) {
        var settingJson = configMap.getData().getOrDefault("default", "{}");
        return JsonUtils.jsonToObject(settingJson, AListProperties.class);
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return Mono.just(deleteContext)
            .filter(context -> this.shouldHandle(context.policy()))
            .flatMap(context -> {
                var properties = getProperties(context.configMap());
                var attachment = context.attachment();
                var filePath = Optional.ofNullable(attachment.getMetadata().getAnnotations())
                    .map(annotations -> annotations.get(FILE_PATH_ANNO))
                    .orElse(null);
                if (StringUtils.isBlank(filePath)) {
                    return Mono.error(new IllegalArgumentException(
                        "Invalid AList attachment: Missing file path annotation")
                    );
                }

                var deleteUri = fromHttpUrl(properties.getSite().toString())
                    .path("/api/fs/remove")
                    .toUriString();
                var body = AListRemoveFileReq.builder()
                    .names(List.of(filePath))
                    .build();

                return getToken(properties)
                    .flatMap(token -> webClient.post().uri(deleteUri)
                        .header(HttpHeaders.AUTHORIZATION, token)
                        .bodyValue(body)
                        .retrieve()
                        .bodyToMono(new ParameterizedTypeReference<AListResult<Void>>() {
                        })
                        .flatMap(result -> {
                            if (!Objects.equals(OK.value(), result.getCode())) {
                                return Mono.error(new ServerWebInputException(
                                    "Failed to delete file: " + result.getMessage())
                                );
                            }
                            return Mono.just(attachment);
                        })
                    );
            });
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
        Duration ttl) {
        return getPermalink(attachment, policy, configMap);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        return Mono.just(policy)
            .filter(this::shouldHandle)
            .map(p -> getProperties(configMap))
            .flatMap(properties -> {
                var filePath = Optional.ofNullable(attachment.getMetadata().getAnnotations())
                    .map(annotations -> annotations.get(FILE_PATH_ANNO))
                    .filter(StringUtils::isNotBlank)
                    .orElse(null);
                if (filePath == null) {
                    return Mono.error(new IllegalArgumentException(
                        "Invalid AList attachment: Missing file path annotation")
                    );
                }
                return getToken(properties)
                    .flatMap(token -> getFile(token, filePath, properties, false))
                    .map(AListGetFileInfoRes::getRawUrl)
                    .map(URI::create);
            });
    }

    private Mono<AListGetFileInfoRes> getFile(String token,
        String filePath,
        AListProperties properties,
        boolean ignoreNotFound) {
        var body = AListGetFileInfoReq.builder()
            .path(filePath)
            .build();
        var fsGetUri = fromHttpUrl(properties.getSite().toString())
            .path("/api/fs/get")
            .toUriString();
        return webClient.post().uri(fsGetUri)
            .header(HttpHeaders.AUTHORIZATION, token)
            .bodyValue(body)
            .retrieve()
            .bodyToMono(
                new ParameterizedTypeReference<AListResult<AListGetFileInfoRes>>() {
                })
            .flatMap(result -> {
                if (!Objects.equals(OK.value(), result.getCode())) {
                    if (ignoreNotFound
                        && Objects.equals(INTERNAL_SERVER_ERROR.value(), result.getCode())
                        && "object not found".equals(result.getMessage())) {
                        return Mono.empty();
                    }
                    return Mono.error(new ServerWebInputException(
                        "Failed to get file info: " + result.getMessage())
                    );
                }
                return Mono.just(result.getData());
            });
    }

    private Mono<String> renameFilename(String token, AListProperties properties,
        String originalFilename) {
        return renameFilename(token, properties, originalFilename, 0);
    }

    private Mono<String> renameFilename(String token,
        AListProperties properties,
        String originalFilename,
        int index) {
        var filename = renameFilename(originalFilename, index);
        var filePath = UriComponentsBuilder.fromPath(properties.getPath())
            .pathSegment(filename)
            .build()
            .toString();

        return getFile(token, filePath, properties, true)
            .flatMap(file -> renameFilename(token, properties, originalFilename, index + 1))
            .switchIfEmpty(Mono.just(filename));
    }

    private static String renameFilename(String filename, int index) {
        if (index <= 0) {
            return filename;
        }
        var lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || filename.startsWith(".")) {
            return filename + "_" + index;
        }
        var baseName = filename.substring(0, lastDotIndex);
        var extension = filename.substring(lastDotIndex);
        return baseName + "_" + index + extension;
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