package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PreSaidaNaoEncontradaException extends BusinessException {

    public PreSaidaNaoEncontradaException() {
        super("Pré-saída não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
