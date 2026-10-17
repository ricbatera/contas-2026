package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class ProvedorIaDuplicadoException extends BusinessException {

    public ProvedorIaDuplicadoException(String nome) {
        super("Já existe um provedor de IA cadastrado com o nome " + nome);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
