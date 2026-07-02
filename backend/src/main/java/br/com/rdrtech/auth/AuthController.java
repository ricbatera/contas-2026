package br.com.rdrtech.auth;

import br.com.rdrtech.api.response.SuccessResponse;
import br.com.rdrtech.auth.dto.LoginRequest;
import br.com.rdrtech.auth.dto.RefreshRequest;
import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.auth.dto.TokenResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Autenticação")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public SuccessResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest request) {
        return SuccessResponse.of(authService.register(request));
    }

    @PostMapping("/login")
    public SuccessResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return SuccessResponse.of(authService.login(request));
    }

    @PostMapping("/refresh")
    public SuccessResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return SuccessResponse.of(authService.refresh(request));
    }

    @PostMapping("/logout")
    public SuccessResponse<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request);
        return SuccessResponse.of(null, "Logout realizado com sucesso");
    }
}
