package run.halo.alist.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/26
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AListUploadAsTaskRes {

    private Task task;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public static class Task {
        private String id;
        private String name;
        private int state;
        private String status;
        private int progress;
        private String error;
    }

}
