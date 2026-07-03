package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class CartaoNaoEncontradoException extends BusinessException {

    public CartaoNaoEncontradoException() {
        super("Cartão não encontrado");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
