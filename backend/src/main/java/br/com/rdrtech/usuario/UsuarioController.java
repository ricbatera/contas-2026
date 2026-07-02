package br.com.rdrtech.usuario;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.usuario.dto.UsuarioResponse;
import br.com.rdrtech.usuario.dto.UsuarioUpdateRequest;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/usuarios")
@RequiredArgsConstructor
@Tag(name = "Usuários")
public class UsuarioController {

    private final UsuarioService usuarioService;

    @GetMapping("/me")
    public SuccessResponse<UsuarioResponse> getMe() {
        return SuccessResponse.of(usuarioService.getMe());
    }

    @PutMapping("/me")
    public SuccessResponse<UsuarioResponse> updateMe(@Valid @RequestBody UsuarioUpdateRequest request) {
        return SuccessResponse.of(usuarioService.updateMe(request));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public SuccessResponse<UsuarioResponse> getById(@PathVariable UUID id) {
        return SuccessResponse.of(usuarioService.getById(id));
    }
}
