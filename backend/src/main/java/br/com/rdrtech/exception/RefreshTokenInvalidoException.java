package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class RefreshTokenInvalidoException extends BusinessException {

    public RefreshTokenInvalidoException() {
        super("Refresh token inválido, expirado ou revogado");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
