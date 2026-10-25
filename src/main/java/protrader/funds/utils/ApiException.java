package protrader.funds.utils;

import org.springframework.http.HttpStatus;

public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final String field;
    private final String shortfall;

    public ApiException(HttpStatus status, String code, String message, String field) {
        this(status, code, message, field, null);
    }

    public ApiException(HttpStatus status, String code, String message, String field, String shortfall) {
        super(message);
        this.status = status;
        this.code = code;
        this.field = field;
        this.shortfall = shortfall;
    }

    public static ApiException invalidField(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "invalid_field", message, field);
    }

    public static ApiException notSupportedYet(String field, String message) {
        return new ApiException(HttpStatus.UNPROCESSABLE_CONTENT, "not_supported_yet", message, field);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public String field() {
        return field;
    }

    public String shortfall() {
        return shortfall;
    }
}
