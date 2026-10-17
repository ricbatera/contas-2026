package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class BucketInvalidoException extends BusinessException {

    public BucketInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
