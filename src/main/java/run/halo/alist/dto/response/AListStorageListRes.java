package run.halo.alist.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateTimeDeserializer;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AListStorageListRes {
    private List<Volume> content;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Volume {
        @JsonProperty("id")
        private int id;

        @JsonProperty("mount_path")
        private String mountPath;

        @JsonProperty("order")
        private int order;

        @JsonProperty("driver")
        private String driver;

        @JsonProperty("cache_expiration")
        private int cacheExpiration;

        @JsonProperty("status")
        private String status;

        @JsonProperty("addition")
        private String addition;

        @JsonProperty("remark")
        private String remark;

        @JsonProperty("modified")
        @JsonDeserialize(using = LocalDateTimeDeserializer.class)
        private LocalDateTime modified;

        @JsonProperty("disabled")
        private boolean disabled;

        @JsonProperty("enable_sign")
        private boolean enableSign;

        @JsonProperty("order_by")
        private String orderBy;

        @JsonProperty("order_direction")
        private String orderDirection;

        @JsonProperty("extract_folder")
        private String extractFolder;

        @JsonProperty("web_proxy")
        private boolean webProxy;

        @JsonProperty("webdav_policy")
        private String webdavPolicy;

        @JsonProperty("down_proxy_url")
        private String downProxyUrl;
    }
}
