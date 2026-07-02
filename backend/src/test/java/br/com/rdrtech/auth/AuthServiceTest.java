package br.com.rdrtech.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private RoleRepository roleRepository;
    @Mock
    private RefreshTokenRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private JwtService jwtService;

    private AuthService authService;

    private Role roleUser;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        authService = new AuthService(usuarioRepository, roleRepository, refreshTokenRepository, passwordEncoder, jwtService);

        roleUser = Role.builder().nome(NomePerfil.USER).build();
        usuario = Usuario.builder()
                .nome("Rodrigo").sobrenome("Dias").email("rodrigo@example.com")
                .senha("hash-armazenado").roles(java.util.Set.of(roleUser)).build();
        usuario.setId(UUID.randomUUID());
    }

    private void stubEmissaoDeTokens() {
        UUID accessJti = UUID.randomUUID();
        UUID refreshJti = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        when(jwtService.generateAccessToken(any(Usuario.class)))
                .thenReturn(new GeneratedToken("access-token", accessJti, expiresAt));
        when(jwtService.generateRefreshToken(any(Usuario.class)))
                .thenReturn(new GeneratedToken("refresh-token", refreshJti, expiresAt.plusSeconds(1000)));
        when(jwtService.accessTokenExpirationSeconds()).thenReturn(3600L);
    }

    @Test
    void register_deveCriarUsuarioComRoleUserESenhaCriptografada() {
        RegisterRequest request = new RegisterRequest("Novo", "Usuario", "novo@example.com", "Senha123!", null);
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(false);
        when(roleRepository.findByNome(NomePerfil.USER)).thenReturn(Optional.of(roleUser));
        when(passwordEncoder.encode(request.senha())).thenReturn("hash-gerado");
        stubEmissaoDeTokens();

        TokenResponse result = authService.register(request);

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(captor.capture());
        Usuario salvo = captor.getValue();
        assertThat(salvo.getSenha()).isEqualTo("hash-gerado");
        assertThat(salvo.getRoles()).containsExactly(roleUser);
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.refreshToken()).isEqualTo("refresh-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    void register_deveLancarEmailJaCadastradoException_quandoEmailJaExiste() {
        RegisterRequest request = new RegisterRequest("Novo", "Usuario", "existente@example.com", "Senha123!", null);
        when(usuarioRepository.existsByEmail(request.email())).thenReturn(true);

        assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EmailJaCadastradoException.class);
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void login_deveLancarCredenciaisInvalidasException_quandoSenhaIncorreta() {
        LoginRequest request = new LoginRequest(usuario.getEmail(), "senha-errada");
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(eq("senha-errada"), anyString())).thenReturn(false);

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void login_deveLancarCredenciaisInvalidasException_quandoEmailNaoExiste() {
        LoginRequest request = new LoginRequest("naoexiste@example.com", "qualquer");
        when(usuarioRepository.findByEmail(request.email())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(request)).isInstanceOf(CredenciaisInvalidasException.class);
    }

    @Test
    void login_deveRetornarTokens_quandoCredenciaisValidas() {
        LoginRequest request = new LoginRequest(usuario.getEmail(), "senha-correta");
        when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
        when(passwordEncoder.matches(eq("senha-correta"), anyString())).thenReturn(true);
        stubEmissaoDeTokens();

        TokenResponse result = authService.login(request);

        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.expiresIn()).isEqualTo(3600L);
    }

    @Test
    void refresh_deveLancarRefreshTokenInvalidoException_quandoTokenRevogado() {
        UUID jti = UUID.randomUUID();
        Jwt refreshJwt = Jwt.withTokenValue("raw-refresh")
                .header("alg", "HS256")
                .claim(JwtService.CLAIM_TYPE, JwtService.TYPE_REFRESH)
                .jti(jti.toString())
                .subject(usuario.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtService.decode("raw-refresh")).thenReturn(refreshJwt);

        RefreshToken tokenRevogado = new RefreshToken();
        tokenRevogado.setId(jti);
        tokenRevogado.setUsuario(usuario);
        tokenRevogado.setDataCriacao(Instant.now());
        tokenRevogado.setDataExpiracao(Instant.now().plusSeconds(3600));
        tokenRevogado.setRevogado(true);
        when(refreshTokenRepository.findById(jti)).thenReturn(Optional.of(tokenRevogado));

        assertThatThrownBy(() -> authService.refresh(new RefreshRequest("raw-refresh")))
                .isInstanceOf(RefreshTokenInvalidoException.class);
    }

    @Test
    void refresh_deveRevogarTokenAntigoEEmitirNovoPar() {
        UUID jti = UUID.randomUUID();
        Jwt refreshJwt = Jwt.withTokenValue("raw-refresh")
                .header("alg", "HS256")
                .claim(JwtService.CLAIM_TYPE, JwtService.TYPE_REFRESH)
                .jti(jti.toString())
                .subject(usuario.getId().toString())
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        when(jwtService.decode("raw-refresh")).thenReturn(refreshJwt);

        RefreshToken tokenValido = new RefreshToken();
        tokenValido.setId(jti);
        tokenValido.setUsuario(usuario);
        tokenValido.setDataCriacao(Instant.now());
        tokenValido.setDataExpiracao(Instant.now().plusSeconds(3600));
        tokenValido.setRevogado(false);
        when(refreshTokenRepository.findById(jti)).thenReturn(Optional.of(tokenValido));
        stubEmissaoDeTokens();

        TokenResponse result = authService.refresh(new RefreshRequest("raw-refresh"));

        assertThat(tokenValido.isRevogado()).isTrue();
        assertThat(result.accessToken()).isEqualTo("access-token");
        verify(refreshTokenRepository, times(1)).save(any(RefreshToken.class));
    }
}
