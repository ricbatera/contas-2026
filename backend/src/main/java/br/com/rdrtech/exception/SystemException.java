package br.com.rdrtech.exception;

/**
 * Base para erros de infraestrutura/inesperados que a aplicação queira modelar explicitamente
 * no futuro (ex.: falha de chamada a serviço externo). Sem subclasse concreta ainda — o
 * fallback genérico do GlobalExceptionHandler (@ExceptionHandler(Exception.class)) cobre
 * exceções verdadeiramente não previstas.
 */
public abstract class SystemException extends RuntimeException {

    protected SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
