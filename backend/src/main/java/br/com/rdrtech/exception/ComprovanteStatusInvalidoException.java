package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ComprovanteStatusInvalidoException extends BusinessException {

    public ComprovanteStatusInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
