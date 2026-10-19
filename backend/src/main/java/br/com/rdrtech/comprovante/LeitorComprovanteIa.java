package br.com.rdrtech.comprovante;

import br.com.rdrtech.exception.ComprovanteInvalidoException;
import br.com.rdrtech.faturaimportacao.LeitorFaturaIa;
import br.com.rdrtech.faturaimportacao.LeitorFaturaIa.OpcaoCategoria;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.ImageContent;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.TextContent;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Envia a foto do comprovante ao provedor de IA (modelo com visão) e interpreta o JSON devolvido. */
@Component
@RequiredArgsConstructor
public class LeitorComprovanteIa {

    static final String INSTRUCOES = """
            Você lê fotos de comprovantes de maquininha de cartão (via do cliente ou do estabelecimento) \
            emitidos no Brasil. Responda APENAS com um objeto JSON válido, sem comentários, sem markdown \
            e sem texto antes ou depois, neste formato:
            {
              "legivel": true,
              "estabelecimento": "nome do estabelecimento como aparece no comprovante",
              "data": "AAAA-MM-DD",
              "valorTotal": 123.45,
              "modalidade": "CREDITO",
              "parcelas": 1,
              "ultimosDigitos": "1234",
              "bandeira": "VISA",
              "categoriaId": "uuid ou null",
              "subcategoriaId": "uuid ou null"
            }
            Regras:
            - "legivel": false quando a imagem não for um comprovante de cartão ou estiver ilegível; \
            nesse caso preencha o que conseguir e use null no resto.
            - "estabelecimento": o nome fantasia ou a razão social do lojista, sem CNPJ, endereço ou \
            números de terminal.
            - "data": a data da transação. Se o ano vier com 2 dígitos (ex.: 15/10/26), use 20AA.
            - "valorTotal": o valor total da compra em R$, positivo, com ponto decimal. Em compras \
            parceladas, é o valor total, não o da parcela.
            - "modalidade": CREDITO ou DEBITO. "CREDITO A VISTA", "CREDITO PARCELADO", "PARC LOJA", \
            "PARC EMISSOR" e "PARCELADO SEM/COM JUROS" são CREDITO; "DEBITO", "DÉBITO A VISTA" e \
            "ELECTRON"/"MAESTRO" são DEBITO. Voucher (refeição/alimentação) conta como DEBITO.
            - "parcelas": quantidade de parcelas (ex.: "PARCELADO LOJA 3X" ou "EM 3 PARCELAS" é 3). \
            Crédito à vista e débito são 1.
            - "ultimosDigitos": os dígitos finais visíveis do cartão (ex.: "************1234" ou \
            "XXXX 1234" é "1234"). Se só aparecerem 3, informe os 3; nunca invente dígitos. Sem número \
            visível, use null.
            - "bandeira": VISA, MASTERCARD, ELO, AMEX, HIPERCARD ou OUTRO (Maestro é MASTERCARD; \
            Electron é VISA). Sem bandeira visível, use null.
            - "categoriaId" e "subcategoriaId": escolha a subcategoria mais provável da lista fornecida \
            pelo tipo de estabelecimento e use os ids exatamente como estão. Se nenhuma servir, use null.
            """;

    private final ObjectMapper objectMapper;

    /** {@code respostaBruta} guarda as respostas da IA para auditoria. */
    public record Leitura(ComprovanteLidoIa comprovante, String respostaBruta, String modelo) {
    }

    public Leitura ler(ChatModel chatModel, byte[] imagem, String tipoMidia, List<OpcaoCategoria> categorias,
            LocalDate hoje) {
        String texto = "Categorias de despesa disponíveis:\n" + LeitorFaturaIa.descreverCategorias(categorias)
                + "\n\nData de hoje: " + hoje + ". Leia o comprovante da imagem.";
        UserMessage mensagem = UserMessage.from(
                TextContent.from(texto),
                ImageContent.from(Base64.getEncoder().encodeToString(imagem), tipoMidia, ImageContent.DetailLevel.HIGH));

        StringBuilder bruta = new StringBuilder();
        for (int tentativa = 1; ; tentativa++) {
            ChatResponse resposta = chatModel.chat(ChatRequest.builder()
                    .messages(SystemMessage.from(INSTRUCOES), mensagem)
                    .build());
            String conteudo = resposta.aiMessage() == null ? null : resposta.aiMessage().text();
            if (tentativa > 1) {
                bruta.append("=== nova tentativa ===\n");
            }
            bruta.append(conteudo).append('\n');
            try {
                return new Leitura(interpretar(conteudo), bruta.toString(), resposta.modelName());
            } catch (ComprovanteInvalidoException e) {
                if (tentativa >= 2) {
                    throw e;
                }
            }
        }
    }

    /** Lê o primeiro objeto JSON da resposta, ignorando cercas de markdown e texto ao redor. */
    public ComprovanteLidoIa interpretar(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            throw new ComprovanteInvalidoException("A IA não devolveu resposta para o comprovante.");
        }
        int inicio = resposta.indexOf('{');
        int fim = resposta.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) {
            throw new ComprovanteInvalidoException("A resposta da IA não contém um JSON com os dados do comprovante.");
        }
        try {
            return objectMapper.readValue(resposta.substring(inicio, fim + 1), ComprovanteLidoIa.class);
        } catch (JsonProcessingException e) {
            throw new ComprovanteInvalidoException(
                    "A resposta da IA não está no formato esperado: " + e.getOriginalMessage());
        }
    }
}
