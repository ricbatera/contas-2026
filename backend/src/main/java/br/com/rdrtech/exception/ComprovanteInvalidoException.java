package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ComprovanteInvalidoException extends BusinessException {

    public ComprovanteInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
