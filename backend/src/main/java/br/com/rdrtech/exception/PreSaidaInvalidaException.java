package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PreSaidaInvalidaException extends BusinessException {

    public PreSaidaInvalidaException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
