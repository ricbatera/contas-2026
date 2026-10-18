package br.com.rdrtech.entrada;

import br.com.rdrtech.entrada.dto.EntradaOcorrenciaResponse;
import br.com.rdrtech.entrada.dto.EntradaResponse;

public final class EntradaMapper {

    private EntradaMapper() {
    }

    public static EntradaResponse toResponse(Entrada entrada) {
        return new EntradaResponse(
                entrada.getId(),
                entrada.getDescricao(),
                entrada.getValor(),
                entrada.isRecorrente(),
                entrada.getDataInicial(),
                entrada.getDataFinal(),
                entrada.getCategoria().getId(),
                entrada.getCategoria().getNome(),
                entrada.getSubcategoria().getId(),
                entrada.getSubcategoria().getNome(),
                entrada.getContaBancaria() != null ? entrada.getContaBancaria().getId() : null,
                entrada.getContaBancaria() != null ? entrada.getContaBancaria().getNome() : null,
                entrada.getAuditoria() != null ? entrada.getAuditoria().getCreatedAt() : null);
    }

    public static EntradaOcorrenciaResponse toResponse(EntradaOcorrencia ocorrencia) {
        Entrada entrada = ocorrencia.getEntrada();
        return new EntradaOcorrenciaResponse(
                ocorrencia.getId(),
                entrada.getId(),
                entrada.getDescricao(),
                entrada.isRecorrente(),
                entrada.getCategoria().getId(),
                entrada.getCategoria().getNome(),
                entrada.getSubcategoria().getId(),
                entrada.getSubcategoria().getNome(),
                entrada.getContaBancaria() != null ? entrada.getContaBancaria().getId() : null,
                entrada.getContaBancaria() != null ? entrada.getContaBancaria().getNome() : null,
                ocorrencia.getCompetencia(),
                ocorrencia.getDataPrevista(),
                ocorrencia.getValorPrevisto(),
                ocorrencia.getStatus(),
                ocorrencia.getValorRecebido(),
                ocorrencia.getDataRecebimento());
    }
}
