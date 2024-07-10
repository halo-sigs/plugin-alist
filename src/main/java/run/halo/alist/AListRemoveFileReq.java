package run.halo.alist;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.List;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/10
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AListRemoveFileReq {
    private String dir;
    private List<String> names;
}
