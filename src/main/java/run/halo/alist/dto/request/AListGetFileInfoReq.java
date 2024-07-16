package run.halo.alist.dto.request;

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
public class AListGetFileInfoReq {
    private String path;
    private String password;
    private int page;
    private int perPage;
    private boolean refresh;
}
