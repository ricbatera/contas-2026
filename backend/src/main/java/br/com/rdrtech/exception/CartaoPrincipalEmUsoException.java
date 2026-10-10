package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class CartaoPrincipalEmUsoException extends BusinessException {

    public CartaoPrincipalEmUsoException() {
        super("Não é possível excluir um cartão principal que possui cartões adicionais");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
