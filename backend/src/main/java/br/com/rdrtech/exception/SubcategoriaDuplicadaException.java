package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class SubcategoriaDuplicadaException extends BusinessException {

    public SubcategoriaDuplicadaException() {
        super("Já existe uma subcategoria com esse nome nessa categoria");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
