package br.com.rdrtech.pessoa;

import br.com.rdrtech.exception.PessoaNaoEncontradaException;
import br.com.rdrtech.exception.RegistroVinculadoException;
import br.com.rdrtech.pessoa.dto.PessoaRequest;
import br.com.rdrtech.pessoa.dto.PessoaResponse;
import br.com.rdrtech.saida.SaidaRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PessoaService {

    private final PessoaRepository pessoaRepository;
    private final SaidaRepository saidaRepository;

    public List<PessoaResponse> listar() {
        return pessoaRepository.findAll().stream().map(PessoaMapper::toResponse).toList();
    }

    public PessoaResponse buscarPorId(UUID id) {
        return pessoaRepository.buscarPorId(id)
                .map(PessoaMapper::toResponse)
                .orElseThrow(PessoaNaoEncontradaException::new);
    }

    @Transactional
    public PessoaResponse criar(PessoaRequest request) {
        Pessoa pessoa = Pessoa.builder()
                .nome(request.nome().trim())
                .ativo(request.ativo() == null || request.ativo())
                .build();
        return PessoaMapper.toResponse(pessoaRepository.save(pessoa));
    }

    @Transactional
    public PessoaResponse atualizar(UUID id, PessoaRequest request) {
        Pessoa pessoa = pessoaRepository.buscarPorId(id)
                .orElseThrow(PessoaNaoEncontradaException::new);
        pessoa.setNome(request.nome().trim());
        if (request.ativo() != null) {
            pessoa.setAtivo(request.ativo());
        }
        return PessoaMapper.toResponse(pessoa);
    }

    @Transactional
    public void excluir(UUID id) {
        Pessoa pessoa = pessoaRepository.buscarPorId(id)
                .orElseThrow(PessoaNaoEncontradaException::new);
        if (saidaRepository.existsByResponsabilidades_Pessoa_Id(id)) {
            throw new RegistroVinculadoException("a pessoa");
        }
        pessoaRepository.delete(pessoa);
    }
}
