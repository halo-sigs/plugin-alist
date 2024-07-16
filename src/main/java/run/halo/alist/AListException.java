package run.halo.alist;

public class AListException extends RuntimeException {
    public AListException(String message) {
        super("[AList Error] : " + message);
    }
}
