package br.com.rdrtech.contabancaria;

import br.com.rdrtech.contabancaria.dto.ContaBancariaRequest;
import br.com.rdrtech.contabancaria.dto.ContaBancariaResponse;
import br.com.rdrtech.exception.ContaBancariaDuplicadaException;
import br.com.rdrtech.exception.ContaBancariaNaoEncontradaException;
import br.com.rdrtech.exception.LimiteContasExcedidoException;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ContaBancariaService {

    private static final int LIMITE_CONTAS = 20;

    private final ContaBancariaRepository contaBancariaRepository;

    public List<ContaBancariaResponse> listar() {
        return contaBancariaRepository.findAll().stream().map(ContaBancariaMapper::toResponse).toList();
    }

    public ContaBancariaResponse buscarPorId(UUID id) {
        return contaBancariaRepository.buscarPorId(id)
                .map(ContaBancariaMapper::toResponse)
                .orElseThrow(ContaBancariaNaoEncontradaException::new);
    }

    @Transactional
    public ContaBancariaResponse criar(ContaBancariaRequest request) {
        if (contaBancariaRepository.count() >= LIMITE_CONTAS) {
            throw new LimiteContasExcedidoException();
        }
        validarDuplicidade(request.nome(), null);

        ContaBancaria contaBancaria = ContaBancaria.builder()
                .nome(request.nome())
                .banco(request.banco())
                .tipoConta(request.tipoConta())
                .agencia(request.agencia())
                .numeroConta(request.numeroConta())
                .saldoInicial(request.saldoInicial())
                .cor(request.cor())
                .ativo(request.ativo())
                .build();
        return ContaBancariaMapper.toResponse(contaBancariaRepository.save(contaBancaria));
    }

    @Transactional
    public ContaBancariaResponse atualizar(UUID id, ContaBancariaRequest request) {
        ContaBancaria contaBancaria = contaBancariaRepository.buscarPorId(id)
                .orElseThrow(ContaBancariaNaoEncontradaException::new);
        validarDuplicidade(request.nome(), id);

        contaBancaria.setNome(request.nome());
        contaBancaria.setBanco(request.banco());
        contaBancaria.setTipoConta(request.tipoConta());
        contaBancaria.setAgencia(request.agencia());
        contaBancaria.setNumeroConta(request.numeroConta());
        contaBancaria.setSaldoInicial(request.saldoInicial());
        contaBancaria.setCor(request.cor());
        contaBancaria.setAtivo(request.ativo());
        return ContaBancariaMapper.toResponse(contaBancaria);
    }

    @Transactional
    public void excluir(UUID id) {
        ContaBancaria contaBancaria = contaBancariaRepository.buscarPorId(id)
                .orElseThrow(ContaBancariaNaoEncontradaException::new);
        contaBancariaRepository.delete(contaBancaria);
    }

    private void validarDuplicidade(String nome, UUID idAtual) {
        boolean duplicado = idAtual == null
                ? contaBancariaRepository.existsByNomeIgnoreCase(nome)
                : contaBancariaRepository.existsByNomeIgnoreCaseAndIdNot(nome, idAtual);
        if (duplicado) {
            throw new ContaBancariaDuplicadaException(nome);
        }
    }
}
