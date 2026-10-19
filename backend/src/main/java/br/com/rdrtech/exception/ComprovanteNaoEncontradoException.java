package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ComprovanteNaoEncontradoException extends BusinessException {

    public ComprovanteNaoEncontradoException() {
        super("Comprovante não encontrado");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
