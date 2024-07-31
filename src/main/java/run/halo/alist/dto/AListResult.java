package run.halo.alist.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * AList 接口返回结果
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/8
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AListResult <T>{
    private String code;
    private String message;
    private T data;
}
