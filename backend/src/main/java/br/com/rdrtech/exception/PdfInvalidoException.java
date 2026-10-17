package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

public class PdfInvalidoException extends BusinessException {

    public PdfInvalidoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.BAD_REQUEST;
    }
}
