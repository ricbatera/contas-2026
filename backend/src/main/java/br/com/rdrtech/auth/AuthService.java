package br.com.rdrtech.auth;

import br.com.rdrtech.auth.dto.LoginRequest;
import br.com.rdrtech.auth.dto.RefreshRequest;
import br.com.rdrtech.auth.dto.RegisterRequest;
import br.com.rdrtech.auth.dto.TokenResponse;
import br.com.rdrtech.exception.CredenciaisInvalidasException;
import br.com.rdrtech.exception.EmailJaCadastradoException;
import br.com.rdrtech.exception.RefreshTokenInvalidoException;
import br.com.rdrtech.role.NomePerfil;
import br.com.rdrtech.role.Role;
import br.com.rdrtech.role.RoleRepository;
import br.com.rdrtech.security.GeneratedToken;
import br.com.rdrtech.security.JwtService;
import br.com.rdrtech.usuario.Usuario;
import br.com.rdrtech.usuario.UsuarioRepository;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final RoleRepository roleRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @Transactional
    public TokenResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByEmail(request.email())) {
            throw new EmailJaCadastradoException(request.email());
        }
        Role roleUser = roleRepository.findByNome(NomePerfil.USER)
                .orElseThrow(() -> new IllegalStateException("Role USER não encontrada — RoleSeeder não rodou"));

        Usuario usuario = Usuario.builder()
                .nome(request.nome())
                .sobrenome(request.sobrenome())
                .email(request.email())
                .senha(passwordEncoder.encode(request.senha()))
                .telefone(request.telefone())
                .ativo(true)
                .emailVerificado(false)
                .roles(Set.of(roleUser))
                .build();
        usuarioRepository.save(usuario);
        return issueTokens(usuario);
    }

    @Transactional
    public TokenResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByEmail(request.email())
                .filter(u -> passwordEncoder.matches(request.senha(), u.getSenha()))
                .orElseThrow(CredenciaisInvalidasException::new);
        return issueTokens(usuario);
    }

    @Transactional
    public TokenResponse refresh(RefreshRequest request) {
        RefreshToken antigo = validarRefreshToken(request.refreshToken());
        antigo.setRevogado(true);
        antigo.setDataRevogacao(Instant.now());

        Usuario usuario = antigo.getUsuario();
        return issueTokens(usuario);
    }

    @Transactional
    public void logout(RefreshRequest request) {
        UUID jti;
        try {
            jti = UUID.fromString(jwtService.decode(request.refreshToken()).getId());
        } catch (JwtException | IllegalArgumentException ex) {
            return;
        }
        refreshTokenRepository.findById(jti).ifPresent(token -> {
            if (!token.isRevogado()) {
                token.setRevogado(true);
                token.setDataRevogacao(Instant.now());
            }
        });
    }

    private RefreshToken validarRefreshToken(String rawToken) {
        Jwt jwt;
        try {
            jwt = jwtService.decode(rawToken);
        } catch (JwtException ex) {
            throw new RefreshTokenInvalidoException();
        }
        if (!JwtService.TYPE_REFRESH.equals(jwt.getClaimAsString(JwtService.CLAIM_TYPE))) {
            throw new RefreshTokenInvalidoException();
        }
        UUID jti = UUID.fromString(jwt.getId());
        RefreshToken refreshToken = refreshTokenRepository.findById(jti)
                .orElseThrow(RefreshTokenInvalidoException::new);
        if (refreshToken.isRevogado() || refreshToken.getDataExpiracao().isBefore(Instant.now())) {
            throw new RefreshTokenInvalidoException();
        }
        return refreshToken;
    }

    private TokenResponse issueTokens(Usuario usuario) {
        GeneratedToken access = jwtService.generateAccessToken(usuario);
        GeneratedToken refresh = jwtService.generateRefreshToken(usuario);

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setId(refresh.jti());
        refreshToken.setUsuario(usuario);
        refreshToken.setDataCriacao(Instant.now());
        refreshToken.setDataExpiracao(refresh.expiresAt());
        refreshToken.setRevogado(false);
        refreshTokenRepository.save(refreshToken);

        return new TokenResponse(access.token(), refresh.token(), "Bearer", jwtService.accessTokenExpirationSeconds());
    }
}
