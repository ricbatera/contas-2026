package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class FaturaImportacaoNaoEncontradaException extends BusinessException {

    public FaturaImportacaoNaoEncontradaException() {
        super("Importação de fatura não encontrada");
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.NOT_FOUND;
    }
}
