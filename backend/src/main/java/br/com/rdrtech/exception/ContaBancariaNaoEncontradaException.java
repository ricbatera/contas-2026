package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ContaBancariaNaoEncontradaException extends BusinessException {

    public ContaBancariaNaoEncontradaException() {
        super("Conta bancária não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
