package run.halo.alist;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.OffsetDateTime;
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
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AListGetFileInfoRes {
    private String name;
    private long size;
    private boolean isDir;
    private OffsetDateTime modified;
    private OffsetDateTime created;
    private String sign;
    private String thumb;
    private int type;
    @JsonProperty("raw_url")
    private String rawUrl;
    private String readme;
    private String header;
    private String provider;
    private Object related;
}
