package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class CategoriaNaoEncontradaException extends BusinessException {

    public CategoriaNaoEncontradaException() {
        super("Categoria não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
