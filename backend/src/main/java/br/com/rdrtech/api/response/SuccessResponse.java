package br.com.rdrtech.api.response;

import java.time.LocalDateTime;

public record SuccessResponse<T>(
        T data,
        String message,
        LocalDateTime timestamp
) {

    public static <T> SuccessResponse<T> of(T data) {
        return new SuccessResponse<>(data, null, LocalDateTime.now());
    }

    public static <T> SuccessResponse<T> of(T data, String message) {
        return new SuccessResponse<>(data, message, LocalDateTime.now());
    }
}
