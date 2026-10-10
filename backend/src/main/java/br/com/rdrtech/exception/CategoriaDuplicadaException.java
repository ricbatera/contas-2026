package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class CategoriaDuplicadaException extends BusinessException {

    public CategoriaDuplicadaException() {
        super("Já existe uma categoria com esse nome para o tipo selecionado");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
