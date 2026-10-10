package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PessoaNaoEncontradaException extends BusinessException {

    public PessoaNaoEncontradaException() {
        super("Pessoa não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
