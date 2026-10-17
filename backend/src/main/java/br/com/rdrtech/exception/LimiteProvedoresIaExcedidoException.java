package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class LimiteProvedoresIaExcedidoException extends BusinessException {

    public LimiteProvedoresIaExcedidoException() {
        super("Limite máximo de 20 provedores de IA cadastrados foi atingido");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
