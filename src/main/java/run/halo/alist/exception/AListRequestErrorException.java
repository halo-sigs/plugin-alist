package run.halo.alist.exception;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/16
 */
public class AListRequestErrorException extends RuntimeException {
    public AListRequestErrorException(String message) {
        super("[AList Error] : " + message);
    }
}
