package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PadraoCategoriaInativoException extends BusinessException {

    public PadraoCategoriaInativoException() {
        super("Somente subcategorias ativas em categorias ativas podem ser padrão");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
