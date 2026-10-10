package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class SubcategoriaNaoEncontradaException extends BusinessException {

    public SubcategoriaNaoEncontradaException() {
        super("Subcategoria não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
