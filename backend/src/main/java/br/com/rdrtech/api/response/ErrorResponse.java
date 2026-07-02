package br.com.rdrtech.api.response;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        int status,
        String message,
        List<FieldError> errors,
        LocalDateTime timestamp,
        String path
) {

    public record FieldError(String field, String message) {}

    public static ErrorResponse of(int status, String message, String path) {
        return new ErrorResponse(status, message, List.of(), LocalDateTime.now(), path);
    }

    public static ErrorResponse of(int status, String message, List<FieldError> errors, String path) {
        return new ErrorResponse(status, message, List.copyOf(errors), LocalDateTime.now(), path);
    }
}
