package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class FaturaCompetenciaInvalidaException extends BusinessException {

    public FaturaCompetenciaInvalidaException() {
        super("Competência da fatura inválida");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
