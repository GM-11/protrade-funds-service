package protrader.funds.grpc;

/** No definite answer from the cash owner (timeout, connection error, unreadable answer). The transfer stays PENDING. */
public class CashOwnerUnavailableException extends RuntimeException {

    public CashOwnerUnavailableException(String message) {
        super(message);
    }

    public CashOwnerUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
