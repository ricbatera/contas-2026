package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class LimiteContasExcedidoException extends BusinessException {

    public LimiteContasExcedidoException() {
        super("Limite máximo de 20 contas bancárias cadastradas foi atingido");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
