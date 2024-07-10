package run.halo.alist;

import java.util.Date;
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
    static class Volume {
        private int id;
        private String mountPath;
        private int order;
        private String driver;
        private int cacheExpiration;
        private String status;
        private String addition;
        private String remark;
        private Date modified;
        private boolean disabled;
        private boolean enableSign;
        private String orderBy;
        private String orderDirection;
        private String extractFolder;
        private boolean webProxy;
        private String webdavPolicy;
        private String downProxyUrl;
    }
}
