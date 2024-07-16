package run.halo.alist.exception;

import org.springframework.web.server.ServerWebInputException;

/**
 * @author <a href="https://roozen.top">Roozen</a>
 * @version 1.0
 * 2024/7/16
 */
public class AListIllegalArgumentException extends ServerWebInputException {
    public AListIllegalArgumentException(String reason) {
        super(reason);
    }
}
