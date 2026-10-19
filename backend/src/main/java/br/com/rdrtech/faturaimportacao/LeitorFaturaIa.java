package br.com.rdrtech.faturaimportacao;

import br.com.rdrtech.exception.FaturaImportacaoInvalidaException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
            - Você recebe UMA página da fatura por vez. Liste TODOS os lançamentos desta página, um por \
            linha, sem pular nenhum: parcelamentos, despesas, IOF, tarifas e créditos, de todos os \
            cartões (titular e adicionais). Uma página pode ter dezenas de lançamentos.
            - "vencimento" é a data de vencimento da fatura e "total" é o valor total a pagar. Preencha \
            só se aparecerem nesta página; senão, use null.
            - Identifique cada cartão (titular e adicionais) pelos 4 últimos dígitos do cabeçalho da sua \
            seção e informe em cada item o cartão da seção em que ele aparece. Se a fatura mascarar \
            algum desses dígitos (ex.: "*117" ou "XXXX XXXX XXXX *117"), informe só os dígitos visíveis \
            ("117"); nunca invente o dígito que falta.
            - Não são lançamentos: totais ("VALOR TOTAL"), resumo da fatura, saldo anterior, limites, \
            cotação do dólar, saldo de obrigações futuras, simulações de juros, CET e opções de \
            parcelamento da fatura.
            - "valor" é sempre positivo, com ponto decimal. Para compras parceladas, use o valor da \
            parcela. Se a linha trouxer valor em R$ e em US$, use o valor em R$.
            - A parcela aparece depois da descrição, no formato NN/NN (ex.: "LOJA X 07/10 292,23" é a \
            parcela 7 de 10). Informe "parcelaAtual" e "totalParcelas"; para compras à vista, use 1 e 1. \
            Números soltos antes da data (ex.: "3 23/09 LOJA") NÃO são parcela.
            - Linhas sem data, como "IOF DESPESA NO EXTERIOR 2,04", também são lançamentos.
            - Quando a data da compra não trouxer o ano, deduza-o pelo vencimento da fatura. Nas \
            parceladas, "data" é a data da compra original.
            - "tipo": COMPRA, TARIFA (anuidade e tarifas), IOF, JUROS (juros, multa e encargos), \
            ESTORNO (estornos e créditos) ou PAGAMENTO (pagamentos da fatura anterior).
            - "categoriaId" e "subcategoriaId": escolha a subcategoria mais provável da lista fornecida \
            e use os ids exatamente como estão. Se nenhuma servir, use null.
            """;

    static final String INSTRUCOES_DESEMPATE = """
            Você concilia lançamentos de uma fatura de cartão de crédito com despesas já cadastradas \
            pelo usuário. Responda APENAS com um objeto JSON válido, sem markdown, neste formato:
            {"pares": [{"item": "I1", "parcela": "P3", "confianca": "ALTA", "motivo": "explicação curta"}]}
            Regras:
            - Cada lançamento (I...) é uma linha do PDF da fatura. Cada parcela (P...) é uma despesa \
            cadastrada, com o apelido dado pelo usuário (ex.: "Amazon Prime") e a descrição que veio \
            na fatura, que pode estar vazia quando a despesa foi lançada à mão antes da fatura.
            - Associe um lançamento a uma parcela somente quando forem a mesma compra ou assinatura. \
            Compare o apelido e a descrição com o texto do lançamento (ex.: "NETFLIX.COM" e "Netflix").
            - Assinaturas podem aparecer como 1/1 na fatura e parceladas no cadastro, ou em outro mês.
            - O valor normalmente é igual. Só associe valores diferentes quando for claramente a mesma \
            assinatura com reajuste, e diga isso no motivo.
            - Use cada lançamento e cada parcela no máximo uma vez. Na dúvida, não associe.
            - "confianca": ALTA, MEDIA ou BAIXA. "motivo" em português, até 150 caracteres.
            - Se não houver nenhuma associação, responda {"pares": []}.
            """;
    private static final int LIMITE_ITENS_DESEMPATE = 80;
    private static final int LIMITE_PARCELAS_DESEMPATE = 150;

    private final ObjectMapper objectMapper;

    record ParesLidos(List<ParLido> pares) {

        ParesLidos {
            pares = pares == null ? List.of() : pares;
        }

        record ParLido(String item, String parcela, String confianca, String motivo) {
        }
    }

    /**
     * Pede à IA para indicar quais lançamentos sem par correspondem a parcelas já cadastradas.
     * O resultado vira apenas sugestão para o usuário confirmar; pares de baixa confiança são descartados.
     */
    List<MotorConciliacao.ParIa> desempatar(ChatModel chatModel, MotorConciliacao.Desempate desempate) {
        Map<String, MotorConciliacao.Item> itens = new LinkedHashMap<>();
        Map<String, MotorConciliacao.Parcela> parcelas = new LinkedHashMap<>();
        StringBuilder texto = new StringBuilder("Lançamentos da fatura:\n");
        desempate.itens().stream().limit(LIMITE_ITENS_DESEMPATE).forEach(item -> {
            String codigo = "I" + (itens.size() + 1);
            itens.put(codigo, item);
            texto.append(codigo).append(" | ").append(item.descricao())
                    .append(" | R$ ").append(item.valor().toPlainString())
                    .append(" | parcela ").append(item.numero()).append('/').append(item.quantidade())
                    .append(item.digitos() == null ? "" : " | final " + item.digitos()).append('\n');
        });
        texto.append("\nParcelas cadastradas:\n");
        desempate.parcelas().stream().limit(LIMITE_PARCELAS_DESEMPATE).forEach(parcela -> {
            String codigo = "P" + (parcelas.size() + 1);
            parcelas.put(codigo, parcela);
            texto.append(codigo).append(" | apelido: ").append(parcela.descricao())
                    .append(" | descrição na fatura: ")
                    .append(parcela.descricaoFatura() == null || parcela.descricaoFatura().isBlank()
                            ? "(vazia)" : parcela.descricaoFatura())
                    .append(" | R$ ").append(parcela.valor().toPlainString())
                    .append(" | parcela ").append(parcela.numero()).append('/').append(parcela.quantidade())
                    .append(parcela.cartaoDigitos() == null ? "" : " | final " + parcela.cartaoDigitos())
                    .append(parcela.vizinha() ? " | outro mês" : "").append('\n');
        });

        ChatResponse resposta = chatModel.chat(ChatRequest.builder()
                .messages(SystemMessage.from(INSTRUCOES_DESEMPATE), UserMessage.from(texto.toString()))
                .build());
        String bruta = resposta.aiMessage() == null ? null : resposta.aiMessage().text();
        ParesLidos lidos = lerJson(bruta, ParesLidos.class, "A IA não indicou correspondências válidas.");

        List<MotorConciliacao.ParIa> pares = new ArrayList<>();
        for (ParesLidos.ParLido par : lidos.pares()) {
            MotorConciliacao.Item item = par.item() == null ? null : itens.get(par.item().trim());
            MotorConciliacao.Parcela parcela = par.parcela() == null ? null : parcelas.get(par.parcela().trim());
            if (item == null || parcela == null || "BAIXA".equalsIgnoreCase(String.valueOf(par.confianca()).trim())) {
                continue;
            }
            pares.add(new MotorConciliacao.ParIa(item.indice(), parcela.parcelaId(), par.motivo()));
        }
        return pares;
    }

    /** Abaixo desta fração das linhas de lançamento do PDF, a leitura gera aviso de itens faltando. */
    static final double PROPORCAO_MINIMA_LIDA = 0.9;
    /** Máximo de linhas de lançamento enviadas à IA numa mesma consulta. */
    static final int LANCAMENTOS_POR_TRECHO = 40;

    /**
     * Leitura juntada de todas as páginas. {@code respostaBruta} guarda a resposta de cada página
     * para auditoria; {@code avisos} vão para o resumo da importação.
     */
    public record Leitura(FaturaLidaIa fatura, String respostaBruta, String modelo, List<String> avisos) {
    }

    /** Categoria de despesa disponível para a IA sugerir, com suas subcategorias. */
    public record OpcaoCategoria(UUID id, String nome, List<OpcaoSubcategoria> subcategorias) {
    }

    public record OpcaoSubcategoria(UUID id, String nome) {
    }

    /**
     * Lê a fatura em trechos: uma página por vez, e páginas com muitos lançamentos divididas em
     * partes, porque modelos menores deixam itens de fora quando recebem listas longas. Cada trecho
     * recebe o último cartão visto antes dele, já que a seção de um cartão pode continuar no trecho
     * seguinte sem repetir o cabeçalho. A parcela de cada item é conferida contra a própria linha.
     */
    public Leitura ler(ChatModel chatModel, List<String> paginas, List<OpcaoCategoria> categorias) {
        String descricaoCategorias = descreverCategorias(categorias);
        boolean algumaComLancamentos = paginas.stream().anyMatch(ConferenciaTextoFatura::temLancamentos);
        String vencimento = null;
        BigDecimal total = null;
        Map<String, FaturaLidaIa.CartaoLido> cartoes = new LinkedHashMap<>();
        List<FaturaLidaIa.ItemLido> itens = new ArrayList<>();
        StringBuilder bruta = new StringBuilder();
        String modelo = null;
        String ultimoCartao = null;
        int esperados = 0;

        for (int indice = 0; indice < paginas.size(); indice++) {
            String pagina = paginas.get(indice);
            // A primeira página sempre vai (costuma trazer o vencimento); as demais, só com lançamentos.
            if (pagina.isBlank() || (indice > 0 && algumaComLancamentos && !ConferenciaTextoFatura.temLancamentos(pagina))) {
                continue;
            }
            esperados += ConferenciaTextoFatura.contarLancamentos(pagina);
            List<String> trechos = ConferenciaTextoFatura.dividir(pagina, LANCAMENTOS_POR_TRECHO);
            for (int parte = 0; parte < trechos.size(); parte++) {
                String rotulo = "Página " + (indice + 1) + " de " + paginas.size()
                        + (trechos.size() > 1 ? ", parte " + (parte + 1) + " de " + trechos.size() : "");
                String trecho = trechos.get(parte);
                String mensagem = "Categorias de despesa disponíveis:\n" + descricaoCategorias
                        + "\n\n" + rotulo + " da fatura."
                        + (ultimoCartao == null ? "" : " No texto anterior, o último cartão identificado foi o final "
                                + ultimoCartao + ": os lançamentos do início deste trecho, antes de um novo cabeçalho "
                                + "de cartão, pertencem a ele.")
                        + "\n\nTexto:\n"
                        + (trecho.length() > TAMANHO_MAXIMO_TEXTO ? trecho.substring(0, TAMANHO_MAXIMO_TEXTO) : trecho);

                ChatResponse resposta = lerTrecho(chatModel, mensagem, rotulo, bruta);
                FaturaLidaIa lida = interpretar(resposta.aiMessage().text());
                modelo = modelo != null ? modelo : resposta.modelName();
                vencimento = vencimento != null ? vencimento : lida.vencimento();
                total = total != null ? total : lida.total();
                lida.cartoes().stream()
                        .filter(cartao -> cartao.ultimosDigitos() != null)
                        .forEach(cartao -> cartoes.putIfAbsent(cartao.ultimosDigitos(), cartao));
                List<FaturaLidaIa.ItemLido> lidos = ConferenciaTextoFatura.corrigirParcelas(lida.itens(), trecho);
                itens.addAll(lidos);
                ultimoCartao = ultimoCartao(lidos, lida.cartoes(), ultimoCartao);
            }
        }

        List<String> avisos = new ArrayList<>();
        if (esperados > 0 && itens.size() < esperados * PROPORCAO_MINIMA_LIDA) {
            avisos.add("A IA leu " + itens.size() + " lançamentos, mas o PDF parece ter cerca de " + esperados
                    + " linhas de lançamento. Confira a fatura: alguns itens podem ter ficado de fora.");
        }
        return new Leitura(new FaturaLidaIa(vencimento, total, List.copyOf(cartoes.values()), itens),
                bruta.toString(), modelo, avisos);
    }

    /** Consulta a IA para um trecho, com uma nova tentativa quando a resposta não vem como JSON válido. */
    private ChatResponse lerTrecho(ChatModel chatModel, String mensagem, String rotulo, StringBuilder bruta) {
        for (int tentativa = 1; ; tentativa++) {
            ChatResponse resposta = chatModel.chat(ChatRequest.builder()
                    .messages(SystemMessage.from(INSTRUCOES), UserMessage.from(mensagem))
                    .build());
            String texto = resposta.aiMessage() == null ? null : resposta.aiMessage().text();
            bruta.append("=== ").append(rotulo).append(tentativa > 1 ? " (nova tentativa)" : "")
                    .append(" ===\n").append(texto).append('\n');
            try {
                interpretar(texto);
                return resposta;
            } catch (FaturaImportacaoInvalidaException e) {
                if (tentativa >= 2) {
                    throw new FaturaImportacaoInvalidaException(rotulo + ": " + e.getMessage());
                }
            }
        }
    }

    private static String ultimoCartao(List<FaturaLidaIa.ItemLido> itens, List<FaturaLidaIa.CartaoLido> cartoes,
            String anterior) {
        for (int i = itens.size() - 1; i >= 0; i--) {
            String digitos = MotorConciliacao.normalizarDigitos(itens.get(i).ultimosDigitos());
            if (digitos != null) {
                return digitos;
            }
        }
        for (int i = cartoes.size() - 1; i >= 0; i--) {
            String digitos = MotorConciliacao.normalizarDigitos(cartoes.get(i).ultimosDigitos());
            if (digitos != null) {
                return digitos;
            }
        }
        return anterior;
    }

    public FaturaLidaIa interpretar(String resposta) {
        if (resposta == null || resposta.isBlank()) {
            throw new FaturaImportacaoInvalidaException("A IA não devolveu resposta para a fatura.");
        }
        return lerJson(resposta, FaturaLidaIa.class, "A resposta da IA não contém um JSON com os dados da fatura.");
    }

    /** Lê o primeiro objeto JSON da resposta, ignorando cercas de markdown e texto ao redor. */
    private <T> T lerJson(String resposta, Class<T> tipo, String mensagemSemJson) {
        int inicio = resposta == null ? -1 : resposta.indexOf('{');
        int fim = resposta == null ? -1 : resposta.lastIndexOf('}');
        if (inicio < 0 || fim <= inicio) {
            throw new FaturaImportacaoInvalidaException(mensagemSemJson);
        }
        try {
            return objectMapper.readValue(resposta.substring(inicio, fim + 1), tipo);
        } catch (JsonProcessingException e) {
            throw new FaturaImportacaoInvalidaException(
                    "A resposta da IA não está no formato esperado: " + e.getOriginalMessage());
        }
    }

    public static String descreverCategorias(List<OpcaoCategoria> categorias) {
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
