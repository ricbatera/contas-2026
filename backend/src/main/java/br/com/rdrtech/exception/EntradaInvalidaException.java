package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class EntradaInvalidaException extends BusinessException {

    public EntradaInvalidaException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
