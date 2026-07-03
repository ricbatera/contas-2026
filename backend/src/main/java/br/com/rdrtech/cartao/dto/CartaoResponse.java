package br.com.rdrtech.cartao.dto;

import br.com.rdrtech.cartao.Bandeira;
import java.time.LocalDateTime;
import java.util.UUID;

public record CartaoResponse(
        UUID id,
        String nome,
        String banco,
        Bandeira bandeira,
        String ultimosDigitos,
        String cor,
        LocalDateTime criadoEm) {
}
