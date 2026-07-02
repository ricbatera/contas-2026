package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class EmailJaCadastradoException extends BusinessException {

    public EmailJaCadastradoException(String email) {
        super("E-mail já cadastrado: " + email);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
