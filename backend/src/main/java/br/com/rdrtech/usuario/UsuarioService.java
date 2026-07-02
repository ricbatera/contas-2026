package br.com.rdrtech.usuario;

import br.com.rdrtech.exception.UsuarioNaoEncontradoException;
import br.com.rdrtech.security.SecurityUtils;
import br.com.rdrtech.usuario.dto.UsuarioResponse;
import br.com.rdrtech.usuario.dto.UsuarioUpdateRequest;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UsuarioService {

    private final UsuarioRepository usuarioRepository;

    public UsuarioResponse getMe() {
        return UsuarioMapper.toResponse(buscarUsuarioAutenticado());
    }

    @Transactional
    public UsuarioResponse updateMe(UsuarioUpdateRequest request) {
        Usuario usuario = buscarUsuarioAutenticado();
        usuario.setNome(request.nome());
        usuario.setSobrenome(request.sobrenome());
        usuario.setTelefone(request.telefone());
        return UsuarioMapper.toResponse(usuario);
    }

    public UsuarioResponse getById(UUID id) {
        return usuarioRepository.findById(id)
                .map(UsuarioMapper::toResponse)
                .orElseThrow(UsuarioNaoEncontradoException::new);
    }

    private Usuario buscarUsuarioAutenticado() {
        UUID id = SecurityUtils.getCurrentUserId().orElseThrow(UsuarioNaoEncontradoException::new);
        return usuarioRepository.findById(id).orElseThrow(UsuarioNaoEncontradoException::new);
    }
}
