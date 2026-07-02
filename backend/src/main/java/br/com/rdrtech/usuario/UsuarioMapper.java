package br.com.rdrtech.usuario;

import br.com.rdrtech.role.Role;
import br.com.rdrtech.usuario.dto.UsuarioResponse;
import java.util.stream.Collectors;

public final class UsuarioMapper {

    private UsuarioMapper() {
    }

    public static UsuarioResponse toResponse(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNome(),
                usuario.getSobrenome(),
                usuario.getEmail(),
                usuario.getTelefone(),
                usuario.getRoles().stream().map(Role::getNome).map(Enum::name).collect(Collectors.toSet()),
                usuario.getAuditoria().getCreatedAt());
    }
}
