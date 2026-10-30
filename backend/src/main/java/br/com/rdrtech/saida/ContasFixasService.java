package br.com.rdrtech.saida;

import br.com.rdrtech.exception.SaidaInvalidaException;
import br.com.rdrtech.exception.SaidaNaoEncontradaException;
import br.com.rdrtech.saida.dto.ContaFixaPreviaResponse;
import br.com.rdrtech.saida.dto.GerarContaFixaItemRequest;
import br.com.rdrtech.saida.dto.GerarContasFixasRequest;
import br.com.rdrtech.saida.dto.GerarContasFixasResponse;
import br.com.rdrtech.saida.dto.SaidaParteRequest;
import br.com.rdrtech.saida.dto.SaidaRequest;
import br.com.rdrtech.saida.dto.SaidaResponsavelResponse;
import br.com.rdrtech.saida.dto.SaidaResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gera as contas fixas do mês a partir da última ocorrência de cada série, exceto as de cartão de crédito.
 *
 * <p>A prévia não grava nada; {@link #gerar} cria só o que o usuário aprovou. Uma série que já tem saída
 * no mês, ou uma conta fixa lançada à mão com a mesma descrição e subcategoria, não é gerada de novo.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContasFixasService {

    private final SaidaRepository saidaRepository;
    private final SaidaService saidaService;

    public List<ContaFixaPreviaResponse> previa(int mes, int ano) {
        YearMonth competencia = competencia(mes, ano);
        List<Saida> lancadas = lancadasNoMes(competencia);
        return ultimasOcorrencias(competencia).values().stream()
                .filter(origem -> !jaLancada(origem, lancadas))
                .map(origem -> toPrevia(origem, competencia))
                .sorted(Comparator.comparing(ContaFixaPreviaResponse::dataSugerida)
                        .thenComparing(ContaFixaPreviaResponse::descricao, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional
    public GerarContasFixasResponse gerar(GerarContasFixasRequest request) {
        YearMonth competencia = competencia(request.mes(), request.ano());
        Set<UUID> series = new HashSet<>();
        for (GerarContaFixaItemRequest item : request.itens()) {
            if (!series.add(item.serieId())) {
                throw new SaidaInvalidaException("Cada conta fixa pode aparecer apenas uma vez.");
            }
            if (!YearMonth.from(item.data()).equals(competencia)) {
                throw new SaidaInvalidaException("A data de cada conta fixa precisa estar no mês gerado.");
            }
        }

        Map<UUID, Saida> origens = ultimasOcorrencias(competencia);
        List<Saida> origensDosItens = request.itens().stream()
                .map(item -> {
                    Saida origem = origens.get(item.serieId());
                    if (origem == null) {
                        throw new SaidaNaoEncontradaException();
                    }
                    return origem;
                })
                .toList();
        // Trava as origens (em ordem fixa, sem deadlock) para que dois cliques não gerem a mesma conta duas vezes.
        origensDosItens.stream()
                .map(Saida::getId)
                .sorted()
                .forEach(saidaRepository::buscarPorIdParaAtualizar);

        List<SaidaResponse> criadas = new ArrayList<>();
        int ignoradas = 0;
        for (int index = 0; index < request.itens().size(); index++) {
            Saida origem = origensDosItens.get(index);
            if (jaLancada(origem, lancadasNoMes(competencia))) {
                ignoradas++;
                continue;
            }
            criadas.add(saidaService.criar(
                    requestPara(origem, request.itens().get(index)), origem.getSerieContaFixaId()));
        }
        return new GerarContasFixasResponse(criadas, ignoradas);
    }

    /** Última ocorrência de cada série antes do mês; a série acaba se ela deixou de ser fixa ou passou ao crédito. */
    private Map<UUID, Saida> ultimasOcorrencias(YearMonth competencia) {
        LocalDate inicio = competencia.atDay(1);
        Map<UUID, Saida> ultimas = new HashMap<>();
        for (Saida saida : saidaRepository.listarDeSeriesComParcelaAntesDe(inicio)) {
            ultimas.merge(saida.getSerieContaFixaId(), saida, (atual, candidata) ->
                    ultimoVencimentoAntes(candidata, inicio).isAfter(ultimoVencimentoAntes(atual, inicio))
                            ? candidata
                            : atual);
        }
        ultimas.values().removeIf(saida ->
                !saida.isContaFixa() || saida.getFormaPagamento() == FormaPagamento.CARTAO_CREDITO);
        return ultimas;
    }

    private List<Saida> lancadasNoMes(YearMonth competencia) {
        return saidaRepository.listarComParcelaNoPeriodo(
                competencia.atDay(1), competencia.plusMonths(1).atDay(1));
    }

    private boolean jaLancada(Saida origem, List<Saida> lancadas) {
        return lancadas.stream().anyMatch(saida ->
                origem.getSerieContaFixaId().equals(saida.getSerieContaFixaId())
                        || saida.isContaFixa()
                                && saida.getSubcategoria().getId().equals(origem.getSubcategoria().getId())
                                && saida.getDescricao().trim().equalsIgnoreCase(origem.getDescricao().trim()));
    }

    private ContaFixaPreviaResponse toPrevia(Saida origem, YearMonth competencia) {
        LocalDate ultimoVencimento = ultimoVencimentoAntes(origem, competencia.atDay(1));
        LocalDate dataSugerida = competencia.atDay(
                Math.min(ultimoVencimento.getDayOfMonth(), competencia.lengthOfMonth()));
        return new ContaFixaPreviaResponse(
                origem.getSerieContaFixaId(),
                origem.getId(),
                origem.getDescricao(),
                origem.getCategoria().getNome(),
                origem.getSubcategoria().getNome(),
                origem.getFormaPagamento(),
                origem.getModoResponsabilidade(),
                SaidaMapper.toResponse(origem).responsabilidades().stream()
                        .map(SaidaResponsavelResponse::nome)
                        .toList(),
                origem.getValorPorParcela(),
                dataSugerida,
                aviso(origem));
    }

    private String aviso(Saida origem) {
        if (!origem.getCategoria().isAtivo() || !origem.getSubcategoria().isAtivo()) {
            return "A categoria ou a subcategoria está inativa.";
        }
        boolean pessoaInativa = origem.getResponsabilidades().stream()
                .anyMatch(item -> item.getPessoa() != null && !item.getPessoa().isAtivo());
        return pessoaInativa ? "Uma pessoa responsável está inativa." : null;
    }

    private SaidaRequest requestPara(Saida origem, GerarContaFixaItemRequest item) {
        UUID responsavelPessoaId = origem.getModoResponsabilidade() == ModoResponsabilidade.PESSOA
                ? origem.getResponsabilidades().stream()
                        .filter(responsavel -> responsavel.getPessoa() != null)
                        .map(responsavel -> responsavel.getPessoa().getId())
                        .findFirst()
                        .orElse(null)
                : null;
        List<SaidaParteRequest> partes = origem.getModoResponsabilidade() == ModoResponsabilidade.DIVIDIR
                ? partesProporcionais(origem, item.valorPorParcela())
                : List.of();
        return new SaidaRequest(
                origem.getDescricao(),
                null,
                item.valorPorParcela(),
                1,
                origem.getFormaPagamento(),
                item.data(),
                null,
                null,
                origem.getCategoria().getId(),
                origem.getSubcategoria().getId(),
                origem.getModoResponsabilidade(),
                responsavelPessoaId,
                partes,
                true);
    }

    /** As partes das pessoas acompanham a mudança de valor na mesma proporção; o arredondamento nunca excede o total. */
    private List<SaidaParteRequest> partesProporcionais(Saida origem, BigDecimal valor) {
        BigDecimal valorOrigem = origem.getValorPorParcela();
        List<SaidaParteRequest> partes = new ArrayList<>();
        BigDecimal soma = BigDecimal.ZERO;
        for (SaidaResponsavel responsavel : origem.getResponsabilidades()) {
            if (responsavel.getPessoa() == null) {
                continue;
            }
            BigDecimal parte = responsavel.getValorPorParcela().multiply(valor)
                    .divide(valorOrigem, 2, RoundingMode.HALF_UP);
            partes.add(new SaidaParteRequest(responsavel.getPessoa().getId(), parte));
            soma = soma.add(parte);
        }
        BigDecimal excesso = soma.subtract(valor);
        if (excesso.signum() > 0 && !partes.isEmpty()) {
            SaidaParteRequest ultima = partes.removeLast();
            partes.add(new SaidaParteRequest(ultima.pessoaId(), ultima.valorPorParcela().subtract(excesso)));
        }
        return partes;
    }

    private LocalDate ultimoVencimentoAntes(Saida saida, LocalDate inicio) {
        return saida.getParcelas().stream()
                .map(SaidaParcela::getVencimento)
                .filter(vencimento -> vencimento.isBefore(inicio))
                .max(Comparator.naturalOrder())
                .orElseThrow();
    }

    private YearMonth competencia(int mes, int ano) {
        try {
            return YearMonth.of(ano, mes);
        } catch (DateTimeException exception) {
            throw new SaidaInvalidaException("Informe uma competência válida.");
        }
    }
}
