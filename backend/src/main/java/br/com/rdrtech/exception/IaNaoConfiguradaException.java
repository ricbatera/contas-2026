package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

/** Recurso que depende de IA usado sem provedor padrão ativo cadastrado. */
public class IaNaoConfiguradaException extends BusinessException {

    public IaNaoConfiguradaException() {
        super("Configure uma integração de IA padrão e ativa para usar este recurso.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
