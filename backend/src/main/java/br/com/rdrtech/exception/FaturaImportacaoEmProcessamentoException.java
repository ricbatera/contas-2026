package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class FaturaImportacaoEmProcessamentoException extends BusinessException {

    public FaturaImportacaoEmProcessamentoException() {
        super("A fatura ainda está sendo processada. Aguarde a conclusão.");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
