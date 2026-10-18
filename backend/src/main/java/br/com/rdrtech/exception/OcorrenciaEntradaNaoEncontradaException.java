package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class OcorrenciaEntradaNaoEncontradaException extends BusinessException {

    public OcorrenciaEntradaNaoEncontradaException() {
        super("Ocorrência da entrada não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
