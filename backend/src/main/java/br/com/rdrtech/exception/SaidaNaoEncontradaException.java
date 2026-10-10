package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class SaidaNaoEncontradaException extends BusinessException {

    public SaidaNaoEncontradaException() {
        super("Saída não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
