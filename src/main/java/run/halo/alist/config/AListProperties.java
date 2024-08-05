package run.halo.alist.config;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AList 存储策略配置
 *
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/8
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AListProperties {
    /**
     * AList 站点地址.
     */
    private String site;
    /**
     * AList 基本路径下文件夹的路径.
     */
    private String path;
    /**
     * Secret name.
     */
    private String secretName;

    /**
     * 获取 token key.
     *
     * @return token key
     */
    public String getTokenKey() {
        return site + secretName;
    }
}
