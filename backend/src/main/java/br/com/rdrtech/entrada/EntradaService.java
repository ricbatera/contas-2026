package br.com.rdrtech.entrada;

import br.com.rdrtech.categoria.Categoria;
import br.com.rdrtech.categoria.CategoriaRepository;
import br.com.rdrtech.categoria.TipoCategoria;
import br.com.rdrtech.contabancaria.ContaBancaria;
import br.com.rdrtech.contabancaria.ContaBancariaRepository;
import br.com.rdrtech.entrada.dto.EntradaOcorrenciaResponse;
import br.com.rdrtech.entrada.dto.EntradaRequest;
import br.com.rdrtech.entrada.dto.EntradaResponse;
import br.com.rdrtech.entrada.dto.ReceberOcorrenciaRequest;
import br.com.rdrtech.exception.CategoriaNaoEncontradaException;
import br.com.rdrtech.exception.ContaBancariaNaoEncontradaException;
import br.com.rdrtech.exception.EntradaInvalidaException;
import br.com.rdrtech.exception.EntradaNaoEncontradaException;
import br.com.rdrtech.exception.OcorrenciaEntradaNaoEncontradaException;
import br.com.rdrtech.exception.SubcategoriaNaoEncontradaException;
import br.com.rdrtech.subcategoria.Subcategoria;
import br.com.rdrtech.subcategoria.SubcategoriaRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Cadastro de entradas e das suas ocorrências mensais.
 *
 * <p>As ocorrências das recorrentes são geradas sob demanda: o cadastro gera até 12 meses à frente
 * e {@link #garantirOcorrenciasAte(YearMonth)} completa o que faltar quando um mês posterior é
 * consultado (lista do mês, dashboard e projeção), limitado a {@value #HORIZONTE_MAXIMO_MESES} meses.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EntradaService {

    static final int MESES_GERADOS_NO_CADASTRO = 12;
    static final int HORIZONTE_MAXIMO_MESES = 60;

    private final EntradaRepository entradaRepository;
    private final EntradaOcorrenciaRepository ocorrenciaRepository;
    private final CategoriaRepository categoriaRepository;
    private final SubcategoriaRepository subcategoriaRepository;
    private final ContaBancariaRepository contaBancariaRepository;

    public List<EntradaResponse> listar() {
        return entradaRepository.findAll(Sort.by(Sort.Direction.DESC, "auditoria.createdAt")).stream()
                .map(EntradaMapper::toResponse)
                .toList();
    }

    public EntradaResponse buscarPorId(UUID id) {
        return entradaRepository.buscarPorId(id)
                .map(EntradaMapper::toResponse)
                .orElseThrow(EntradaNaoEncontradaException::new);
    }

    @Transactional
    public EntradaResponse criar(EntradaRequest request) {
        Entrada entrada = new Entrada();
        aplicarRequest(entrada, request, null);
        gerarOcorrencias(entrada, horizonteDoCadastro(entrada));
        return EntradaMapper.toResponse(entradaRepository.save(entrada));
    }

    /**
     * As ocorrências previstas são refeitas com os novos dados; as recebidas e canceladas são
     * preservadas e precisam continuar dentro do período da entrada.
     */
    @Transactional
    public EntradaResponse atualizar(UUID id, EntradaRequest request) {
        Entrada entrada = travar(id);
        ReferenciasAnteriores anteriores = new ReferenciasAnteriores(
                entrada.getCategoria().getId(),
                entrada.getSubcategoria().getId(),
                entrada.getContaBancaria() != null ? entrada.getContaBancaria().getId() : null);
        YearMonth horizonteAnterior = entrada.getOcorrencias().stream()
                .map(ocorrencia -> YearMonth.from(ocorrencia.getCompetencia()))
                .max(Comparator.naturalOrder())
                .orElse(null);

        aplicarRequest(entrada, request, anteriores);
        validarOcorrenciasConfirmadas(entrada);
        entrada.getOcorrencias().removeIf(ocorrencia -> ocorrencia.getStatus() == StatusOcorrencia.PREVISTA);
        entradaRepository.flush();

        YearMonth horizonte = horizonteDoCadastro(entrada);
        if (horizonteAnterior != null && horizonteAnterior.isAfter(horizonte)) {
            horizonte = horizonteAnterior;
        }
        gerarOcorrencias(entrada, horizonte);
        return EntradaMapper.toResponse(entradaRepository.save(entrada));
    }

    @Transactional
    public void excluir(UUID id) {
        Entrada entrada = entradaRepository.buscarPorId(id).orElseThrow(EntradaNaoEncontradaException::new);
        entradaRepository.delete(entrada);
    }

    @Transactional
    public List<EntradaOcorrenciaResponse> listarOcorrencias(int mes, int ano) {
        YearMonth competencia = YearMonth.of(ano, mes);
        garantirOcorrenciasAte(competencia);
        return ocorrenciaRepository.listarDaCompetencia(competencia.atDay(1)).stream()
                .map(EntradaMapper::toResponse)
                .toList();
    }

    @Transactional
    public EntradaOcorrenciaResponse receber(UUID ocorrenciaId, ReceberOcorrenciaRequest request) {
        EntradaOcorrencia ocorrencia = buscarOcorrencia(ocorrenciaId);
        if (ocorrencia.getStatus() == StatusOcorrencia.CANCELADA) {
            throw new EntradaInvalidaException("Reabra a ocorrência cancelada antes de registrar o recebimento.");
        }
        ocorrencia.setStatus(StatusOcorrencia.RECEBIDA);
        ocorrencia.setValorRecebido(request.valorRecebido());
        ocorrencia.setDataRecebimento(request.dataRecebimento());
        return EntradaMapper.toResponse(ocorrencia);
    }

    @Transactional
    public EntradaOcorrenciaResponse cancelar(UUID ocorrenciaId) {
        EntradaOcorrencia ocorrencia = buscarOcorrencia(ocorrenciaId);
        if (ocorrencia.getStatus() != StatusOcorrencia.PREVISTA) {
            throw new EntradaInvalidaException("Só é possível cancelar uma ocorrência prevista.");
        }
        ocorrencia.setStatus(StatusOcorrencia.CANCELADA);
        return EntradaMapper.toResponse(ocorrencia);
    }

    @Transactional
    public EntradaOcorrenciaResponse reabrir(UUID ocorrenciaId) {
        EntradaOcorrencia ocorrencia = buscarOcorrencia(ocorrenciaId);
        if (ocorrencia.getStatus() == StatusOcorrencia.PREVISTA) {
            throw new EntradaInvalidaException("A ocorrência já está prevista.");
        }
        ocorrencia.setStatus(StatusOcorrencia.PREVISTA);
        ocorrencia.setValorRecebido(null);
        ocorrencia.setDataRecebimento(null);
        return EntradaMapper.toResponse(ocorrencia);
    }

    /**
     * Gera as ocorrências que faltam nas entradas recorrentes do usuário até {@code competencia}.
     * Precisa de transação de escrita: quem chama a partir de uma consulta não pode ser readOnly.
     */
    @Transactional
    public void garantirOcorrenciasAte(YearMonth competencia) {
        YearMonth maximo = YearMonth.now().plusMonths(HORIZONTE_MAXIMO_MESES);
        YearMonth limite = competencia.isAfter(maximo) ? maximo : competencia;
        List<UUID> pendentes = entradaRepository.listarHorizontesRecorrentes().stream()
                .filter(horizonte -> precisaGerar(horizonte, limite))
                .map(HorizonteEntrada::id)
                .toList();
        if (pendentes.isEmpty()) {
            return;
        }
        // A trava faz uma consulta simultânea esperar e enxergar o que esta gerou.
        entradaRepository.travarPorIds(pendentes).forEach(entrada -> gerarOcorrencias(entrada, limite));
    }

    private boolean precisaGerar(HorizonteEntrada horizonte, YearMonth limite) {
        YearMonth inicio = YearMonth.from(horizonte.dataInicial());
        YearMonth proxima = horizonte.ultimaCompetencia() == null
                ? inicio
                : YearMonth.from(horizonte.ultimaCompetencia()).plusMonths(1);
        if (proxima.isBefore(inicio)) {
            proxima = inicio;
        }
        return !proxima.isAfter(limite)
                && (horizonte.dataFinal() == null || !proxima.isAfter(YearMonth.from(horizonte.dataFinal())));
    }

    private void gerarOcorrencias(Entrada entrada, YearMonth ate) {
        Set<LocalDate> existentes = entrada.getOcorrencias().stream()
                .map(EntradaOcorrencia::getCompetencia)
                .collect(Collectors.toSet());
        int quantidadeMaxima = entrada.isRecorrente() ? Integer.MAX_VALUE : 1;
        for (int indice = 0; indice < quantidadeMaxima; indice++) {
            // plusMonths sobre a data inicial ajusta o dia 31 nos meses curtos sem perder o dia original.
            LocalDate dataPrevista = entrada.getDataInicial().plusMonths(indice);
            YearMonth competencia = YearMonth.from(dataPrevista);
            if (competencia.isAfter(ate)
                    || entrada.getDataFinal() != null && dataPrevista.isAfter(entrada.getDataFinal())) {
                break;
            }
            if (existentes.contains(competencia.atDay(1))) {
                continue;
            }
            entrada.getOcorrencias().add(EntradaOcorrencia.builder()
                    .entrada(entrada)
                    .competencia(competencia.atDay(1))
                    .dataPrevista(dataPrevista)
                    .valorPrevisto(entrada.getValor())
                    .status(StatusOcorrencia.PREVISTA)
                    .build());
        }
    }

    private YearMonth horizonteDoCadastro(Entrada entrada) {
        YearMonth inicio = YearMonth.from(entrada.getDataInicial());
        YearMonth padrao = YearMonth.now().plusMonths(MESES_GERADOS_NO_CADASTRO);
        return inicio.isAfter(padrao) ? inicio : padrao;
    }

    private void validarOcorrenciasConfirmadas(Entrada entrada) {
        YearMonth inicio = YearMonth.from(entrada.getDataInicial());
        YearMonth fim = entrada.isRecorrente()
                ? entrada.getDataFinal() != null ? YearMonth.from(entrada.getDataFinal()) : null
                : inicio;
        boolean foraDoPeriodo = entrada.getOcorrencias().stream()
                .filter(ocorrencia -> ocorrencia.getStatus() != StatusOcorrencia.PREVISTA)
                .map(ocorrencia -> YearMonth.from(ocorrencia.getCompetencia()))
                .anyMatch(competencia -> competencia.isBefore(inicio) || fim != null && competencia.isAfter(fim));
        if (foraDoPeriodo) {
            throw new EntradaInvalidaException(
                    "Há ocorrências recebidas ou canceladas fora do novo período. Reabra-as antes de alterar as datas.");
        }
    }

    private void aplicarRequest(Entrada entrada, EntradaRequest request, ReferenciasAnteriores anteriores) {
        if (!request.recorrente() && request.dataFinal() != null) {
            throw new EntradaInvalidaException("A data final só se aplica a entradas recorrentes.");
        }
        if (request.dataFinal() != null && request.dataFinal().isBefore(request.dataInicial())) {
            throw new EntradaInvalidaException("A data final não pode ser anterior à data inicial.");
        }

        Categoria categoria = categoriaRepository.buscarPorId(request.categoriaId())
                .orElseThrow(CategoriaNaoEncontradaException::new);
        boolean mesmaCategoria = anteriores != null && categoria.getId().equals(anteriores.categoriaId());
        if (categoria.getTipo() != TipoCategoria.RECEITA && !mesmaCategoria) {
            throw new EntradaInvalidaException("Selecione uma categoria de receita.");
        }
        if (!categoria.isAtivo() && !mesmaCategoria) {
            throw new EntradaInvalidaException("Selecione uma categoria ativa.");
        }
        Subcategoria subcategoria = subcategoriaRepository.buscarPorId(request.subcategoriaId())
                .filter(item -> item.getCategoria().getId().equals(categoria.getId()))
                .orElseThrow(SubcategoriaNaoEncontradaException::new);
        if (!subcategoria.isAtivo()
                && (anteriores == null || !subcategoria.getId().equals(anteriores.subcategoriaId()))) {
            throw new EntradaInvalidaException("Selecione uma subcategoria ativa.");
        }
        ContaBancaria conta = null;
        if (request.contaBancariaId() != null) {
            conta = contaBancariaRepository.buscarPorId(request.contaBancariaId())
                    .orElseThrow(ContaBancariaNaoEncontradaException::new);
            if (!conta.isAtivo()
                    && (anteriores == null || !conta.getId().equals(anteriores.contaBancariaId()))) {
                throw new EntradaInvalidaException("Selecione uma conta bancária ativa.");
            }
        }

        entrada.setDescricao(request.descricao().trim());
        entrada.setValor(request.valor());
        entrada.setRecorrente(request.recorrente());
        entrada.setDataInicial(request.dataInicial());
        entrada.setDataFinal(request.dataFinal());
        entrada.setCategoria(categoria);
        entrada.setSubcategoria(subcategoria);
        entrada.setContaBancaria(conta);
    }

    private Entrada travar(UUID id) {
        return entradaRepository.travarPorIds(List.of(id)).stream()
                .findFirst()
                .orElseThrow(EntradaNaoEncontradaException::new);
    }

    private EntradaOcorrencia buscarOcorrencia(UUID id) {
        return ocorrenciaRepository.buscarPorId(id).orElseThrow(OcorrenciaEntradaNaoEncontradaException::new);
    }

    private record ReferenciasAnteriores(UUID categoriaId, UUID subcategoriaId, UUID contaBancariaId) {
    }
}
