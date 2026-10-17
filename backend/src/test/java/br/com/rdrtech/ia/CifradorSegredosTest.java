package br.com.rdrtech.ia;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Base64;
import org.junit.jupiter.api.Test;

class CifradorSegredosTest {

    private static final String CHAVE = "dGVzdC1vbmx5LWNoYXZlLWlhLTMyLWJ5dGVzLTEyMzQ=";

    private final CifradorSegredos cifrador = new CifradorSegredos(new IaProperties(CHAVE));

    @Test
    void cifrar_deveRecuperarTextoOriginal_aoDecifrar() {
        String cifrado = cifrador.cifrar("sk-minha-chave-secreta");

        assertThat(cifrado).startsWith("v1:").doesNotContain("sk-minha-chave-secreta");
        assertThat(cifrador.decifrar(cifrado)).isEqualTo("sk-minha-chave-secreta");
    }

    @Test
    void cifrar_deveGerarValoresDiferentes_paraMesmoTexto() {
        assertThat(cifrador.cifrar("sk-abc")).isNotEqualTo(cifrador.cifrar("sk-abc"));
    }

    @Test
    void decifrar_deveFalhar_quandoTextoAdulterado() {
        String cifrado = cifrador.cifrar("sk-abc");
        byte[] bytes = Base64.getDecoder().decode(cifrado.substring(3));
        bytes[bytes.length - 1] ^= 1;
        String adulterado = "v1:" + Base64.getEncoder().encodeToString(bytes);

        assertThatThrownBy(() -> cifrador.decifrar(adulterado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void decifrar_deveFalhar_quandoChaveDiferente() {
        String cifrado = cifrador.cifrar("sk-abc");
        CifradorSegredos outro = new CifradorSegredos(
                new IaProperties(Base64.getEncoder().encodeToString(new byte[32])));

        assertThatThrownBy(() -> outro.decifrar(cifrado)).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void iaProperties_deveRejeitarChaveQueNaoTem32Bytes() {
        assertThatThrownBy(() -> new IaProperties("curta")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new IaProperties(null)).isInstanceOf(IllegalStateException.class);
    }
}
