package br.com.rdrtech.faturaimportacao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import br.com.rdrtech.exception.FaturaImportacaoInvalidaException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import org.junit.jupiter.api.Test;

class LeitorFaturaIaTest {

    private static final String PAGINA_1 = """
            Vencimento 10/10/2026  Total R$ 400,00
            CLIENTE -  5228 XXXX XXXX 5464
            Despesas
            3 30/08 POSTO EXEMPLO 100,00
            """;
    /** A seção do cartão 4726 começa na página 2 e continua na 3 sem repetir o cabeçalho. */
    private static final String PAGINA_2 = """
            ADICIONAL -  5480 XXXX XXXX 4726
            Parcelamentos
            1 06/02 MAGALU FL 989 08/10 287,72
            """;
    private static final String PAGINA_3 = """
            Despesas
            05/09 NETFLIX ENTRETENIMENTO 12,28
            """;
    private static final String PAGINA_4 = "Juros e CET para o próximo período: 9,99% a.m.";

    private final LeitorFaturaIa leitor = new LeitorFaturaIa(new ObjectMapper());

    @Test
    void deveLerPaginaAPaginaJuntarOsItensEPassarOCartaoAdiante() {
        List<String> mensagens = new ArrayList<>();
        ChatModel chatModel = responder(mensagem -> {
            mensagens.add(mensagem);
            if (mensagem.contains("Página 1 de 4")) {
                return """
                        {"vencimento": "2026-10-10", "total": 400.00, "cartoes": [{"ultimosDigitos": "5464"}],
                         "itens": [{"ultimosDigitos": "5464", "descricao": "POSTO EXEMPLO", "valor": 100.00,
                                    "parcelaAtual": 3, "totalParcelas": 3, "tipo": "COMPRA"}]}""";
            }
            if (mensagem.contains("Página 2 de 4")) {
                return """
                        ```json
                        {"vencimento": null, "total": null, "cartoes": [{"ultimosDigitos": "4726"}],
                         "itens": [{"ultimosDigitos": "4726", "descricao": "MAGALU FL 989", "valor": 287.72,
                                    "parcelaAtual": 1, "totalParcelas": 1, "tipo": "COMPRA"}]}
                        ```""";
            }
            return """
                    {"vencimento": null, "total": null, "cartoes": [],
                     "itens": [{"ultimosDigitos": "4726", "descricao": "NETFLIX ENTRETENIMENTO", "valor": 12.28,
                                "parcelaAtual": 1, "totalParcelas": 1, "tipo": "COMPRA"}]}""";
        });

        LeitorFaturaIa.Leitura leitura = leitor.ler(chatModel, List.of(PAGINA_1, PAGINA_2, PAGINA_3, PAGINA_4), List.of());

        // A página 4 não tem lançamentos e não é enviada.
        assertThat(mensagens).hasSize(3);
        assertThat(mensagens.get(0)).doesNotContain("último cartão");
        assertThat(mensagens.get(2)).contains("último cartão identificado foi o final 4726");
        assertThat(leitura.fatura().vencimento()).isEqualTo("2026-10-10");
        assertThat(leitura.fatura().cartoes()).extracting(FaturaLidaIa.CartaoLido::ultimosDigitos)
                .containsExactly("5464", "4726");
        assertThat(leitura.fatura().itens()).extracting(FaturaLidaIa.ItemLido::descricao)
                .containsExactly("POSTO EXEMPLO", "MAGALU FL 989", "NETFLIX ENTRETENIMENTO");
        // Parcelas conferidas contra a linha: o "3" antes da data não é parcela; MAGALU é 8/10.
        assertThat(leitura.fatura().itens().get(0).totalParcelas()).isEqualTo(1);
        assertThat(leitura.fatura().itens().get(1).parcelaAtual()).isEqualTo(8);
        assertThat(leitura.fatura().itens().get(1).totalParcelas()).isEqualTo(10);
        assertThat(leitura.avisos()).isEmpty();
        assertThat(leitura.respostaBruta()).contains("=== Página 1 de 4 ===", "=== Página 3 de 4 ===");
    }

    @Test
    void deveAvisarQuandoAIaDeixaLancamentosDeFora() {
        String pagina = """
                Vencimento 10/10/2026
                CLIENTE -  5228 XXXX XXXX 5464
                30/08 LOJA A 10,00
                31/08 LOJA B 20,00
                01/09 LOJA C 30,00
                """;
        ChatModel chatModel = responder(mensagem -> """
                {"vencimento": "2026-10-10", "cartoes": [{"ultimosDigitos": "5464"}],
                 "itens": [{"ultimosDigitos": "5464", "descricao": "LOJA A", "valor": 10.00, "tipo": "COMPRA"}]}""");

        LeitorFaturaIa.Leitura leitura = leitor.ler(chatModel, List.of(pagina), List.of());

        assertThat(leitura.avisos()).singleElement().asString().contains("leu 1 lançamentos", "cerca de 3");
    }

    @Test
    void deveTentarDeNovoUmaVezQuandoAPaginaNaoVemEmJson() {
        AtomicInteger chamadas = new AtomicInteger();
        ChatModel chatModel = responder(mensagem -> chamadas.incrementAndGet() == 1
                ? "Desculpe, não entendi."
                : "{\"vencimento\": \"2026-10-10\", \"itens\": []}");

        LeitorFaturaIa.Leitura leitura = leitor.ler(chatModel, List.of(PAGINA_1), List.of());

        assertThat(chamadas).hasValue(2);
        assertThat(leitura.fatura().vencimento()).isEqualTo("2026-10-10");
        assertThat(leitura.respostaBruta()).contains("nova tentativa");
    }

    @Test
    void deveFalharIndicandoAPaginaQuandoAsDuasTentativasFalham() {
        ChatModel chatModel = responder(mensagem -> "sem json");

        assertThatThrownBy(() -> leitor.ler(chatModel, List.of(PAGINA_1, PAGINA_2), List.of()))
                .isInstanceOf(FaturaImportacaoInvalidaException.class)
                .hasMessageStartingWith("Página 1 de 2:");
    }

    @Test
    void deveDividirPaginaComMuitosLancamentosMantendoOCartaoEntreAsPartes() {
        StringBuilder pagina = new StringBuilder("ADICIONAL -  5480 XXXX XXXX 4726\nDespesas\n");
        for (int i = 1; i <= LeitorFaturaIa.LANCAMENTOS_POR_TRECHO + 5; i++) {
            pagina.append(String.format(" 10/09 LOJA %02d 1%02d,00%n", i, i));
        }
        List<String> mensagens = new ArrayList<>();
        ChatModel chatModel = responder(mensagem -> {
            mensagens.add(mensagem);
            StringBuilder itens = new StringBuilder();
            mensagem.lines().filter(l -> l.contains(" LOJA ")).forEach(l -> {
                String loja = l.substring(l.indexOf("LOJA"), l.indexOf("LOJA") + 7);
                itens.append(itens.isEmpty() ? "" : ",").append("{\"ultimosDigitos\": ")
                        .append(mensagem.contains("parte 1 de") ? "\"4726\"" : "null")
                        .append(", \"descricao\": \"").append(loja).append("\", \"valor\": 1.00, \"tipo\": \"COMPRA\"}");
            });
            return "{\"vencimento\": \"2026-10-10\", \"itens\": [" + itens + "]}";
        });

        LeitorFaturaIa.Leitura leitura = leitor.ler(chatModel, List.of(pagina.toString()), List.of());

        assertThat(mensagens).hasSize(2);
        assertThat(mensagens.get(0)).contains("Página 1 de 1, parte 1 de 2");
        assertThat(mensagens.get(1)).contains("parte 2 de 2", "último cartão identificado foi o final 4726");
        assertThat(leitura.fatura().itens()).hasSize(LeitorFaturaIa.LANCAMENTOS_POR_TRECHO + 5);
        assertThat(leitura.avisos()).isEmpty();
    }

    private static ChatModel responder(Function<String, String> resposta) {
        ChatModel chatModel = mock(ChatModel.class);
        when(chatModel.chat(any(ChatRequest.class))).thenAnswer(invocacao -> {
            ChatRequest request = invocacao.getArgument(0);
            String mensagem = ((UserMessage) request.messages().get(1)).singleText();
            return ChatResponse.builder().aiMessage(AiMessage.from(resposta.apply(mensagem))).modelName("teste").build();
        });
        return chatModel;
    }
}
