package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ProvedorIaNaoEncontradoException extends BusinessException {

    public ProvedorIaNaoEncontradoException() {
        super("Provedor de IA não encontrado");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
