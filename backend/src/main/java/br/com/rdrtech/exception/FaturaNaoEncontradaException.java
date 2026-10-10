package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class FaturaNaoEncontradaException extends BusinessException {

    public FaturaNaoEncontradaException() {
        super("Fatura não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
