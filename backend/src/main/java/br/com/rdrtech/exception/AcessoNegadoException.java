package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

/**
 * Reservada para regras de autorização de negócio que não se encaixam em hasRole()/isolamento
 * por tenant (ex.: futuras checagens explícitas de propriedade de recurso). Sem ponto de
 * chamada concreto nesta entrega.
 */
public class AcessoNegadoException extends BusinessException {

    public AcessoNegadoException(String message) {
        super(message);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.FORBIDDEN;
    }
}
