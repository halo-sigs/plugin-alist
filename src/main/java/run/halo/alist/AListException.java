package run.halo.alist;

import org.springframework.web.server.ServerWebInputException;

public class AListException extends ServerWebInputException {
    public AListException(String message) {
        super("[AList Error] : " + message);
    }
}
