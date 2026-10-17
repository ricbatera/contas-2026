package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ProvedorIaInvalidoException extends BusinessException {

    public ProvedorIaInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
