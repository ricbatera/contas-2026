package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class RegistroVinculadoException extends BusinessException {

    public RegistroVinculadoException(String tipo) {
        super("Não é possível excluir " + tipo + " vinculado a saídas ou entradas. Inative-o para preservar o histórico.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
