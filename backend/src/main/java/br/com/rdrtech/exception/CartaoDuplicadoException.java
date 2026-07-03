package br.com.rdrtech.exception;

import br.com.rdrtech.cartao.Bandeira;
import org.springframework.http.HttpStatus;

public class CartaoDuplicadoException extends BusinessException {

    public CartaoDuplicadoException(Bandeira bandeira, String ultimosDigitos) {
        super("Já existe um cartão cadastrado com a bandeira " + bandeira + " e os últimos 4 dígitos "
                + ultimosDigitos);
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.CONFLICT;
    }
}
