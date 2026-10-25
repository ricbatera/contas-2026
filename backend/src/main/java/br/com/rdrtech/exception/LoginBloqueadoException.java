package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

/**
 * Login bloqueado temporariamente depois de várias senhas erradas. Vale também para e-mails não
 * cadastrados, para não revelar quais contas existem.
 */
public class LoginBloqueadoException extends BusinessException {

    public LoginBloqueadoException(long minutos) {
        super("Muitas tentativas de login. Tente novamente em " + minutos + (minutos == 1 ? " minuto." : " minutos."));
    }

    @Override
    public HttpStatus getStatus() {
        return HttpStatus.TOO_MANY_REQUESTS;
    }
}
