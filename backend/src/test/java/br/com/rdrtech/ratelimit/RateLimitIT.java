package br.com.rdrtech.ratelimit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "seguranca.rate-limit.habilitado=true",
        "seguranca.rate-limit.auth.capacidade=3",
        "seguranca.rate-limit.auth.janela=1m",
        "seguranca.rate-limit.api.capacidade=2",
        "seguranca.rate-limit.api.janela=1m"
})
class RateLimitIT {

    private static final String LOGIN = "{\"email\":\"rate-limit@example.com\",\"senha\":\"qualquer\"}";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deveResponder429_quandoIpExcedeLimiteDasRotasDeAutenticacao() throws Exception {
        String ip = "10.0.0.1";
        for (int i = 0; i < 3; i++) {
            login(ip).andExpect(status().isUnauthorized());
        }

        login(ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value("Muitas requisições. Tente novamente em instantes."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"));

        login("10.0.0.2").andExpect(status().isUnauthorized());
    }

    @Test
    void deveLimitarRotasAutenticadasMesmoSemToken() throws Exception {
        String ip = "10.0.0.3";
        for (int i = 0; i < 2; i++) {
            mockMvc.perform(post("/api/saidas").header("X-Real-IP", ip)).andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/saidas").header("X-Real-IP", ip)).andExpect(status().isTooManyRequests());
    }

    private ResultActions login(String ip) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .header("X-Real-IP", ip)
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN));
    }
}
