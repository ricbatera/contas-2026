package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.exception.FaturaImportacaoInvalidaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Envia o texto da fatura ao provedor de IA e interpreta o JSON devolvido. */
@Component
@RequiredArgsConstructor
public class LeitorFaturaIa {

    static final int TAMANHO_MAXIMO_TEXTO = 100_000;

    static final String INSTRUCOES = """
            Você extrai dados de faturas de cartão de crédito brasileiras. Responda APENAS com um \
            objeto JSON válido, sem comentários, sem markdown e sem texto antes ou depois, neste formato:
            {
              "vencimento": "AAAA-MM-DD",
              "total": 1234.56,
              "cartoes": [{"ultimosDigitos": "1234", "titular": "NOME"}],
              "itens": [{
                "ultimosDigitos": "1234",
                "data": "AAAA-MM-DD",
                "descricao": "texto exatamente como aparece na fatura",
                "valor": 99.90,
                "parcelaAtual": 2,
                "totalParcelas": 10,
                "tipo": "COMPRA",
                "categoriaId": "uuid ou null",
                "subcategoriaId": "uuid ou null"
              }]
            }
            Regras:
            - "vencimento" é a data de vencimento da fatura e "total" é o valor total a pagar.
            - Identifique cada cartão (titular e adicionais) pelos 4 últimos dígitos e informe em cada \
            item o cartão em que ele aparece. Use null se não houver.
            - Liste todos os lançamentos da fatura atual, um por linha. Não liste o resumo das próximas \
            faturas nem o saldo da fatura anterior.
            - "valor" é sempre positivo, com ponto decimal. Para compras parceladas, use o valor da parcela.
            - Para compras parceladas (ex.: "02/10" ou "PARC 02/10"), informe "parcelaAtual" e \
            "totalParcelas"; para compras à vista, use 1 e 1.
            - Quando a data da compra não trouxer o ano, deduza-o pelo vencimento da fatura. Nas \
            parceladas, "data" é a data da compra original.
            - "tipo": COMPRA, TARIFA (anuidade e tarifas), IOF, JUROS (juros, multa e encargos), \
            ESTORNO (estornos e créditos) ou PAGAMENTO (pagamentos da fatura anterior).
            - "categoriaId" e "subcategoriaId": escolha a subcategoria mais provável da lista fornecida \
            e use os ids exatamente como estão. Se nenhuma servir, use null.
            """;

    private final ObjectMapper objectMapper;

    /** Resposta da IA antes da interpretação, para ser guardada mesmo quando o JSON vier inválido. */
    public record Resposta(String texto, String modelo) {
    }

    /** Categoria de despesa disponível para a IA sugerir, com suas subcategorias. */
    public record OpcaoCategoria(UUID id, String nome, List<OpcaoSubcategoria> subcategorias) {
    }

    public record OpcaoSubcategoria(UUID id, String nome) {
    }

    public Resposta consultar(ChatModel chatModel, String textoFatura, List<OpcaoCategoria> categorias) {
        String texto = textoFatura.length() > TAMANHO_MAXIMO_TEXTO
                ? textoFatura.substring(0, TAMANHO_MAXIMO_TEXTO)
                : textoFatura;
        ChatResponse resposta = chatModel.chat(ChatRequest.builder()
                .messages(
                        SystemMessage.from(INSTRUCOES),
                        UserMessage.from("Categorias de despesa disponíveis:\n" + descreverCategorias(categorias)
                                + "\n\nTexto da fatura:\n" + texto))
                .build());
        return new Resposta(resposta.aiMessage() == null ? null : resposta.aiMessage().text(), resposta.modelName());
    }

    public FaturaLidaIa interpretar(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            throw new FaturaImportacaoInvalidaException("A IA não devolveu resposta para a fatura.");
        }
        int inicio = resposta.indexOf('{');
        int fim = resposta.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) {
            throw new FaturaImportacaoInvalidaException("A resposta da IA não contém um JSON com os dados da fatura.");
        }
        try {
            return objectMapper.readValue(resposta.substring(inicio, fim + 1), FaturaLidaIa.class);
        } catch (JsonProcessingException e) {
            throw new FaturaImportacaoInvalidaException(
                    "A resposta da IA não está no formato esperado: " + e.getOriginalMessage());
        }
    }

    private static String descreverCategorias(List<OpcaoCategoria> categorias) {
        if (categorias.isEmpty()) {
            return "(nenhuma)";
        }
        StringBuilder sb = new StringBuilder();
        for (OpcaoCategoria categoria : categorias) {
            sb.append("- categoriaId ").append(categoria.id()).append(" \"").append(categoria.nome()).append("\":");
            for (OpcaoSubcategoria subcategoria : categoria.subcategorias()) {
                sb.append("\n    subcategoriaId ").append(subcategoria.id())
                        .append(" \"").append(subcategoria.nome()).append('"');
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
