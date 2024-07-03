package run.halo.starter;

import java.net.URI;
import java.time.Duration;
import lombok.extern.slf4j.Slf4j;
import org.pf4j.Extension;
import reactor.core.publisher.Mono;
import run.halo.app.core.extension.attachment.Attachment;
import run.halo.app.core.extension.attachment.Policy;
import run.halo.app.core.extension.attachment.endpoint.AttachmentHandler;
import run.halo.app.extension.ConfigMap;

/**
 * 文件描述
 *
 * @author： <a href="https://roozen.top">Roozen</a>
 * @date: 2024/7/3
 */
@Slf4j
@Extension
public class AListAttachmentHandler implements AttachmentHandler {

    @Override
    public Mono<Attachment> upload(UploadContext uploadContext) {
        System.out.println("AListAttachmentHandler upload");
        Mono.just(uploadContext).filter(context -> this.shouldHandle(context.policy()))
            .subscribe(System.out::println);
        return Mono.empty();
    }

    @Override
    public Mono<Attachment> delete(DeleteContext deleteContext) {
        return null;
    }

    @Override
    public Mono<URI> getSharedURL(Attachment attachment, Policy policy, ConfigMap configMap,
        Duration ttl) {
        return AttachmentHandler.super.getSharedURL(attachment, policy, configMap, ttl);
    }

    @Override
    public Mono<URI> getPermalink(Attachment attachment, Policy policy, ConfigMap configMap) {
        return AttachmentHandler.super.getPermalink(attachment, policy, configMap);
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
