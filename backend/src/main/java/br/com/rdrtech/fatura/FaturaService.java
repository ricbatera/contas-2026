package br.com.rdrtech.fatura;

import br.com.rdrtech.cartao.Cartao;
import br.com.rdrtech.cartao.CartaoRepository;
import br.com.rdrtech.exception.CartaoNaoEncontradoException;
import br.com.rdrtech.exception.FaturaCompetenciaInvalidaException;
import br.com.rdrtech.exception.FaturaNaoEncontradaException;
import br.com.rdrtech.fatura.dto.FaturaCompetenciaResponse;
import br.com.rdrtech.fatura.dto.FaturaParcelaResponse;
import br.com.rdrtech.fatura.dto.FaturaResponse;
import br.com.rdrtech.saida.SaidaParcela;
import br.com.rdrtech.saida.SaidaResponsavel;
import br.com.rdrtech.saida.dto.SaidaResponsavelResponse;
import java.time.DateTimeException;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class FaturaService {

    private final FaturaRepository faturaRepository;
    private final CartaoRepository cartaoRepository;

    public List<FaturaCompetenciaResponse> listarCompetencias(UUID cartaoId) {
        validarCartaoPrincipal(cartaoId);
        return faturaRepository.listarCompetenciasPorCartao(cartaoId);
    }

    public FaturaResponse buscar(UUID cartaoId, int competencia) {
        validarCartaoPrincipal(cartaoId);
        validarCompetencia(competencia);
        Fatura fatura = faturaRepository.buscarPorCartaoECompetencia(cartaoId, competencia)
                .orElseThrow(FaturaNaoEncontradaException::new);
        Cartao cartao = fatura.getCartaoPrincipal();
        List<FaturaParcelaResponse> parcelas = fatura.getParcelas().stream()
                .sorted(Comparator.comparing(SaidaParcela::getVencimento)
                        .thenComparing(parcela -> parcela.getSaida().getDescricao())
                        .thenComparingInt(SaidaParcela::getNumero))
                .map(FaturaService::mapearParcela)
                .toList();
        return new FaturaResponse(
                cartao.getId(),
                cartao.getNome(),
                cartao.getBanco(),
                fatura.getCompetencia(),
                fatura.getVencimento(),
                fatura.getTotal(),
                parcelas);
    }

    private void validarCartaoPrincipal(UUID cartaoId) {
        cartaoRepository.buscarPrincipalPorId(cartaoId).orElseThrow(CartaoNaoEncontradoException::new);
    }

    private void validarCompetencia(int competencia) {
        int ano = competencia / 100;
        int mes = competencia % 100;
        try {
            if (String.valueOf(competencia).length() != 6 || ano < 1900) {
                throw new DateTimeException("Competência fora do intervalo permitido");
            }
            YearMonth.of(ano, mes);
        } catch (DateTimeException exception) {
            throw new FaturaCompetenciaInvalidaException();
        }
    }

    private static FaturaParcelaResponse mapearParcela(SaidaParcela parcela) {
        var saida = parcela.getSaida();
        Cartao cartaoUsado = saida.getCartao();
        List<SaidaResponsavelResponse> responsaveis = saida.getResponsabilidades().stream()
                .map(FaturaService::mapearResponsavel)
                .toList();
        return new FaturaParcelaResponse(
                saida.getId(),
                saida.getDescricao(),
                saida.getDescricaoFatura(),
                cartaoUsado != null ? cartaoUsado.getId() : null,
                cartaoUsado != null ? cartaoUsado.getNome() : null,
                parcela.getNumero(),
                saida.getQuantidadeParcelas(),
                parcela.getVencimento(),
                parcela.getValor(),
                responsaveis);
    }

    private static SaidaResponsavelResponse mapearResponsavel(SaidaResponsavel responsavel) {
        return new SaidaResponsavelResponse(
                responsavel.getPessoa() != null ? responsavel.getPessoa().getId() : null,
                responsavel.getUsuario() != null ? responsavel.getUsuario().getId() : null,
                responsavel.getPessoa() != null
                        ? responsavel.getPessoa().getNome()
                        : responsavel.getUsuario().getNome() + " " + responsavel.getUsuario().getSobrenome(),
                responsavel.getValorPorParcela());
    }
}
