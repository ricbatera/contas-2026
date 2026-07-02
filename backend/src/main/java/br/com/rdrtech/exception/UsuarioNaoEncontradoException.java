package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class UsuarioNaoEncontradoException extends BusinessException {

    public UsuarioNaoEncontradoException() {
        super("Usuário não encontrado");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
