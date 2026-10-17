package br.com.rdrtech.presaida;

public enum StatusPreSaida {
    PENDENTE,
    APROVADA,
    REJEITADA,
    /** Confirmada pelo usuário como a mesma compra de uma saída já lançada. */
    VINCULADA
}
