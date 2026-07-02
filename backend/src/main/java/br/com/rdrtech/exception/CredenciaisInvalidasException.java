package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

/**
 * Mensagem sempre genérica (não distingue e-mail inexistente de senha incorreta) para evitar
 * enumeração de contas cadastradas.
 */
public class CredenciaisInvalidasException extends BusinessException {

    public CredenciaisInvalidasException() {
        super("E-mail ou senha inválidos");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNAUTHORIZED;
    }
}
