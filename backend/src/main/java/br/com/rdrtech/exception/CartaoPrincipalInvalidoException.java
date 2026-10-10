package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class CartaoPrincipalInvalidoException extends BusinessException {

    public CartaoPrincipalInvalidoException() {
        super("Cartão adicional deve estar associado a um cartão principal válido");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
