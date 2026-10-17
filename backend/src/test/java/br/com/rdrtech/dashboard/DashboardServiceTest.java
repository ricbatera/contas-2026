package br.com.rdrtech.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import br.com.rdrtech.bucket.BucketRepository;
import br.com.rdrtech.saida.SaidaResponsavelRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private SaidaResponsavelRepository saidaResponsavelRepository;
    @Mock
    private BucketRepository bucketRepository;

    private DashboardService service;
    private final UUID usuarioId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        service = new DashboardService(saidaResponsavelRepository, bucketRepository);
        Jwt jwt = Jwt.withTokenValue("test-token")
                .header("alg", "none")
                .subject(usuarioId.toString())
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt, List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void consultar_deveSomarContasFixasDoMesAnteriorACompetencia() {
        when(saidaResponsavelRepository.somarPartesUsuarioNoPeriodo(
                        usuarioId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(new BigDecimal("125.50"));
        when(bucketRepository.somarValorMensalDoUsuario(usuarioId)).thenReturn(new BigDecimal("200.00"));
        when(saidaResponsavelRepository.somarPartesFixasUsuarioNoPeriodo(
                        usuarioId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
                .thenReturn(new BigDecimal("150.00"));

        var response = service.consultar(10, 2026);

        assertThat(response.gastosLancados()).isEqualByComparingTo("125.50");
        assertThat(response.bucketsReservados()).isEqualByComparingTo("200.00");
        assertThat(response.contasFixasMesAnterior()).isEqualByComparingTo("150.00");
        assertThat(response.totalPrevisto()).isEqualByComparingTo("475.50");
        verify(saidaResponsavelRepository).somarPartesUsuarioNoPeriodo(
                usuarioId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1));
        verify(saidaResponsavelRepository).somarPartesFixasUsuarioNoPeriodo(
                usuarioId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1));
    }

    @Test
    void consultar_deveSomarSomenteMesAnteriorAoCruzarAno() {
        when(saidaResponsavelRepository.somarPartesUsuarioNoPeriodo(
                        usuarioId, LocalDate.of(2027, 1, 1), LocalDate.of(2027, 2, 1)))
                .thenReturn(BigDecimal.ZERO);
        when(bucketRepository.somarValorMensalDoUsuario(usuarioId)).thenReturn(BigDecimal.ZERO);
        when(saidaResponsavelRepository.somarPartesFixasUsuarioNoPeriodo(
                        usuarioId, LocalDate.of(2026, 12, 1), LocalDate.of(2027, 1, 1)))
                .thenReturn(new BigDecimal("10.00"));

        var response = service.consultar(1, 2027);

        assertThat(response.contasFixasMesAnterior()).isEqualByComparingTo("10.00");
        assertThat(response.totalPrevisto()).isEqualByComparingTo("10.00");
    }

    @Test
    void consultar_deveUsarZeroQuandoNaoHaContaFixaNoMesAnterior() {
        when(saidaResponsavelRepository.somarPartesUsuarioNoPeriodo(
                        usuarioId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1)))
                .thenReturn(BigDecimal.ZERO);
        when(bucketRepository.somarValorMensalDoUsuario(usuarioId)).thenReturn(BigDecimal.ZERO);
        when(saidaResponsavelRepository.somarPartesFixasUsuarioNoPeriodo(
                        usuarioId, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 1)))
                .thenReturn(BigDecimal.ZERO);

        var response = service.consultar(10, 2026);

        assertThat(response.contasFixasMesAnterior()).isEqualByComparingTo("0.00");
    }
}
