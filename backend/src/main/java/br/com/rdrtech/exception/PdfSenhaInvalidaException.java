package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PdfSenhaInvalidaException extends BusinessException {

    public PdfSenhaInvalidaException() {
        super("Senha do PDF inválida. Informe a senha correta para abrir a fatura.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.UNPROCESSABLE_ENTITY;
    }
}
