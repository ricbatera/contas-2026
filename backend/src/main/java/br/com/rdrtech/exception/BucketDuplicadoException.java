package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class BucketDuplicadoException extends BusinessException {

    public BucketDuplicadoException() {
        super("Já existe um bucket para esta subcategoria e responsável.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
