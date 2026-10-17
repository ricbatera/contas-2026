package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class BucketNaoEncontradoException extends BusinessException {

    public BucketNaoEncontradoException() {
        super("Bucket não encontrado.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
