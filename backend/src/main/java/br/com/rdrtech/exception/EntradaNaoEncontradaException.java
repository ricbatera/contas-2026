package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class EntradaNaoEncontradaException extends BusinessException {

    public EntradaNaoEncontradaException() {
        super("Entrada não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
