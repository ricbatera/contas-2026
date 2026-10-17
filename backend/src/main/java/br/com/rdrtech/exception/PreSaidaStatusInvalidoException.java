package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PreSaidaStatusInvalidoException extends BusinessException {

    public PreSaidaStatusInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
