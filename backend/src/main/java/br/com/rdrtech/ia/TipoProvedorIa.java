package br.com.rdrtech.ia;

public enum TipoProvedorIa {
    /** OpenAI e APIs compatíveis (Groq, OpenRouter, DeepSeek, LM Studio...) via baseUrl. */
    OPENAI,
    ANTHROPIC,
    GEMINI,
    OLLAMA;

    public boolean exigeApiKey() {
        return this != OLLAMA;
    }

    public boolean exigeBaseUrl() {
        return this == OLLAMA;
    }
}
