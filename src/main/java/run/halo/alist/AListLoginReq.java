package run.halo.alist;

import lombok.Builder;
import lombok.Data;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/8
 */
@Data
@Builder
public class AListLoginReq {
    private String password;
    private String username;
}
