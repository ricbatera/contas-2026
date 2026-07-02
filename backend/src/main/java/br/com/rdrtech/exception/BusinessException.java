package br.com.rdrtech.exception;

import org.springframework.http.HttpStatus;

/**
 * Base para exceções de regra de negócio (409, 401, 404, 403 etc.), separada de falhas de
 * infraestrutura/sistema ({@link SystemException}). Cada subclasse concreta define seu próprio
 * HTTP status via {@link #getStatus()}, permitindo ao GlobalExceptionHandler ter um único
 * @ExceptionHandler para toda a hierarquia.
 */
public abstract class BusinessException extends RuntimeException {

    protected BusinessException(String message) {
        super(message);
    }

    public abstract HttpStatus getStatus();
}
