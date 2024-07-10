package run.halo.alist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/8
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AListLoginRes {
    private String token;
}
