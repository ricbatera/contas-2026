package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class LimiteCartoesExcedidoException extends BusinessException {

    public LimiteCartoesExcedidoException() {
        super("Limite máximo de 20 cartões cadastrados foi atingido");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
