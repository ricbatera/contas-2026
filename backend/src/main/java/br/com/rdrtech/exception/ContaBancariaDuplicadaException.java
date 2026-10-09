package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ContaBancariaDuplicadaException extends BusinessException {

    public ContaBancariaDuplicadaException(String nome) {
        super("Já existe uma conta bancária cadastrada com o nome " + nome);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
