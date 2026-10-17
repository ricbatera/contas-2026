import { TipoProvedorIa } from '../../core/provedor-ia.service';

/** Textos e sugestões por tipo de provedor, compartilhados entre lista, formulário e teste. */
export const INFO_PROVEDOR: Record<
  TipoProvedorIa,
  { nome: string; baseUrlPadrao: string; modelos: string[]; ajuda: string }
> = {
  OPENAI: {
    nome: 'OpenAI / compatível',
    baseUrlPadrao: 'https://api.openai.com/v1',
    modelos: ['gpt-4o-mini', 'gpt-4o', 'gpt-4.1', 'gpt-4.1-mini'],
    ajuda: 'Para Groq, OpenRouter, DeepSeek ou LM Studio, informe a URL base da API compatível.',
  },
  ANTHROPIC: {
    nome: 'Anthropic (Claude)',
    baseUrlPadrao: 'https://api.anthropic.com/v1',
    modelos: ['claude-sonnet-5-5', 'claude-haiku-5-5', 'claude-opus-5-5'],
    ajuda: 'Gere a API key no console da Anthropic.',
  },
  GEMINI: {
    nome: 'Google Gemini',
    baseUrlPadrao: 'https://generativelanguage.googleapis.com/v1beta',
    modelos: ['gemini-2.5-flash', 'gemini-2.5-pro'],
    ajuda: 'Gere a API key no Google AI Studio.',
  },
  OLLAMA: {
    nome: 'Ollama (local)',
    baseUrlPadrao: 'http://localhost:11434',
    modelos: ['llama3.2', 'qwen2.5', 'mistral'],
    ajuda: 'Não usa API key. Informe a URL onde o Ollama está rodando, acessível pelo backend.',
  },
};

export function mensagemErroHttp(err: { status: number; error?: { message?: unknown } }, fallback: string): string {
  if (err.status === 0) {
    return 'Não foi possível conectar ao servidor. Verifique sua conexão e tente novamente.';
  }
  return typeof err.error?.message === 'string' ? err.error.message : `${fallback} (erro ${err.status}).`;
}
