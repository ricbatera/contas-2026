package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class SaidaInvalidaException extends BusinessException {

    public SaidaInvalidaException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
