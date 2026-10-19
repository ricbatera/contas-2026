package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class CartaoInvalidoException extends BusinessException {

    public CartaoInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
