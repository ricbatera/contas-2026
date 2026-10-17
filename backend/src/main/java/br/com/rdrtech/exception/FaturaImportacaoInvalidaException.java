package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class FaturaImportacaoInvalidaException extends BusinessException {

    public FaturaImportacaoInvalidaException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
