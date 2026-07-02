# PRD — Sistema de Controle de Finanças Pessoais

**Versão:** 1.4  
**Data:** 2026-05-09  
**Status:** final

---

## 1. Visão Geral

Sistema web para controle de finanças pessoais que centraliza faturas de cartão, débitos automáticos, contas de serviço fixas e variáveis, comprovantes de pagamento e metas de orçamento. O objetivo é dar ao usuário visibilidade total dos gastos — passados e futuros — com o mínimo de trabalho manual, usando processamento automático de documentos, ajuste inteligente de previsões e uma rotina de aprovação leve.

---

## 2. Problema

Usuários que possuem múltiplos cartões de crédito e contas bancárias precisam acessar vários aplicativos para acompanhar seus gastos. Além disso, débitos automáticos e contas de serviço (luz, internet, água) ficam fora do radar dos cartões. As descrições dos lançamentos são crípticas, não há visão unificada e é impossível antecipar quanto será gasto nos próximos meses sem consolidar manualmente cartões + débitos + contas fixas.

---

## 3. Objetivos

- Unificar lançamentos de múltiplos cartões, contas bancárias e débitos automáticos em uma única interface.
- Reduzir o trabalho manual com processamento automático de faturas e comprovantes.
- Dar nomes legíveis (aliases) a estabelecimentos recorrentes.
- Alertar quando o orçamento por categoria estiver próximo do limite.
- Acompanhar contas de serviço fixas e variáveis com ajuste automático de previsão por média histórica.
- Oferecer um gráfico de previsão de gastos futuros combinando todas as fontes de despesa.

---

## 4. Fora de Escopo (v1)

- Integração direta com APIs de bancos (Open Finance).
- Controle de investimentos.
- Suporte multi-usuário / família.
- App mobile nativo.

---

## 5. Módulos e Funcionalidades

### 5.0 Autenticação e Acesso

Controla o acesso ao sistema. Todos os dados são estritamente isolados por usuário — nenhum dado de um usuário é visível ou acessível por outro.

**Tela de cadastro:**
- Campos: nome, e-mail e senha (mínimo 8 caracteres, ao menos 1 número e 1 caractere especial).
- Sistema envia e-mail de confirmação; o acesso é liberado somente após a confirmação.

**Tela de login:**
- Campos: e-mail e senha.
- Retorna JWT (acesso) com expiração de 1 hora + refresh token com expiração de 30 dias.
- Após 5 tentativas consecutivas com senha incorreta, a conta é bloqueada temporariamente por 15 minutos.

**Tela de recuperação de senha:**
- Usuário informa o e-mail cadastrado.
- Sistema envia link de redefinição com validade de 1 hora.
- Usuário define nova senha sem precisar informar a senha anterior.

**2FA — opcional, desejável na Fase 6:**
- TOTP via aplicativo autenticador (Google Authenticator, Authy).
- QR Code gerado na tela de configurações de conta.
- Código de 6 dígitos solicitado após autenticação bem-sucedida com senha.

**Regras:**
- Sessões inativas por mais de 30 dias exigem novo login completo.
- Todos os demais módulos filtram dados pelo usuário autenticado (isolamento total).
- Nenhum dado sensível de autenticação (senha em texto plano, segredo 2FA) é retornado pela API.

---

### 5.1 Cadastro de Cartões

O usuário registra seus cartões manualmente. Nenhum dado sensível é armazenado.

**Campos:**
| Campo | Tipo | Obrigatório |
|---|---|---|
| Nome do cartão | Texto livre | Sim |
| Banco emissor | Texto livre | Sim |
| Bandeira | Seleção (Visa, Mastercard, Elo, Amex, Hipercard, Outro) | Sim |
| Últimos 4 dígitos | Numérico (4 chars) | Sim |
| Cor de identificação | Color picker | Não |

**Regras:**
- Máximo de 20 cartões cadastrados.
- Os 4 dígitos + bandeira devem ser únicos para evitar duplicatas.

---

### 5.2 Importação de Faturas

O usuário faz upload de uma fatura (PDF ou imagem) e o sistema extrai os lançamentos automaticamente.

**Fluxo:**
1. Usuário seleciona o cartão e faz upload do arquivo (PDF ou imagem PNG/JPG).
2. Sistema processa o arquivo via OCR / parsing de PDF.
3. Sistema apresenta os lançamentos extraídos em uma tela de revisão.
4. Usuário confirma, edita ou descarta lançamentos individualmente.
5. Ao confirmar, os lançamentos são adicionados ao histórico.

**Dados extraídos por lançamento:**
- Data da transação
- Descrição original (como consta na fatura)
- Valor
- Parcela (ex: 2/6), se houver

**Regras:**
- O sistema deve detectar e avisar sobre lançamentos duplicados (mesma data, valor e descrição já existente).
- Suporte inicial: faturas em formato texto (PDFs não-escaneados). OCR para imagens é desejável mas pode ser fase 2.

---

### 5.3 Sistema de Alias

O objetivo dos aliases é dar um nome legível a qualquer descrição críptica que aparece nos lançamentos do cartão. As descrições geradas pelas operadoras muitas vezes não dizem nada sobre o que foi comprado — o alias é o que o usuário quer ver no lugar disso.

**Exemplos:**
| Descrição original (como vem na fatura) | Alias definido pelo usuário |
|---|---|
| `MaguluFCR` | Máquina de Lavar |
| `AMZN*MKTPLCE` | Amazon |
| `NETFLIX.COM` | Netflix |
| `UBER* TRIP` | Uber |
| `PSEG*00123456` | Conta de Luz (referência) |

O alias pode ser o **nome do estabelecimento**, o **nome do produto comprado**, ou qualquer rótulo que faça sentido para o usuário.

---

**Dois níveis de alias:**

| Nível | Quando usar | Comportamento |
|---|---|---|
| **Padrão (regra)** | Descrição aparece com frequência (ex: `NETFLIX.COM`) | Aplicado automaticamente a todos os lançamentos futuros e passados que baterem com o padrão |
| **Pontual (lançamento)** | Compra única onde o código não se repetirá (ex: `MaguluFCR` → Máquina de Lavar) | Aplicado apenas àquele lançamento específico; não cria regra para o futuro |

Ao criar um alias, o sistema pergunta: _"Aplicar como regra automática para futuras importações ou apenas neste lançamento?"_

---

**Fluxo:**
- O alias pode ser criado durante a revisão de uma fatura ou na tela de histórico ao editar qualquer lançamento.
- Para aliases do tipo **padrão**, o sistema pergunta se deseja aplicá-lo retroativamente aos lançamentos já existentes com a mesma descrição.
- O matching de padrões é feito por substring (case-insensitive) na descrição original.
- Se dois padrões conflitam (ambos batem com a mesma descrição), o mais específico (maior string de correspondência) vence.

**Gerenciamento:**
- Tela dedicada para listar, editar e excluir aliases (padrões e pontuais).
- Campo de teste: o usuário digita uma descrição e vê exatamente qual alias seria aplicado e por qual regra.

---

### 5.4 Leitura de Comprovante de Maquininha

Permite registrar um gasto antecipadamente a partir de um comprovante físico ou digital, antes da fatura fechar.

**Fluxo:**
1. Usuário faz upload de foto do comprovante ou PDF.
2. Sistema extrai: data, valor, últimos 4 dígitos do cartão, estabelecimento (quando disponível).
3. Sistema tenta vincular automaticamente ao cartão cadastrado pelos 4 dígitos.
4. Lançamento é criado com status **Pendente** (aguardando aprovação).
5. Usuário revisa e aprova, edita ou rejeita o lançamento pendente.
6. Aprovado, o lançamento entra no histórico com flag `origem: comprovante`.

**Regras:**
- Lançamentos pendentes aparecem em destaque no dashboard.
- Ao importar a fatura do mesmo cartão, o sistema tenta conciliar automaticamente lançamentos pendentes com os da fatura (por data ± 2 dias e valor exato), sugerindo a fusão.

---

### 5.5 Rotina de Aprovação e Edição

Tela centralizada para gerenciar todos os lançamentos com status **Pendente**.

**Ações disponíveis por lançamento:**
- Aprovar (move para histórico)
- Editar (descrição, valor, data, categoria, alias) e aprovar
- Rejeitar (descarta o lançamento)

**Campos editáveis em qualquer lançamento (inclusive aprovados):**
- Alias / nome amigável
- Categoria
- Observação livre
- Flag "ignorar no orçamento" (para estornos, transferências, etc.)

---

### 5.6 Categorias e Subcategorias

O sistema organiza os gastos em dois níveis hierárquicos: **categoria** (visão ampla) e **subcategoria** (detalhe granular). Isso permite tanto uma visão consolidada quanto filtros precisos.

**Estrutura padrão:**

| Categoria | Subcategorias padrão |
|---|---|
| Alimentação | Restaurante, Delivery, Mercado, Padaria, Lanchonete |
| Transporte | Combustível, Estacionamento, Aplicativo (Uber/99), Manutenção, Pedágio, Transporte Público |
| Saúde | Farmácia, Consulta, Exame, Plano de Saúde, Academia |
| Educação | Curso, Livro, Assinatura Educacional, Material Escolar |
| Lazer | Streaming, Cinema/Teatro, Viagem, Bar/Balada, Esporte, Jogo |
| Moradia | Aluguel, Condomínio, Conta de Luz, Água, Internet, Gás, Manutenção |
| Roupas | Vestuário, Calçados, Acessórios |
| Assinaturas | Streaming, Software, Clube de Assinatura |
| Outros | — (sem subcategoria padrão) |

**Regras:**
- Um lançamento é classificado com **categoria + subcategoria** (subcategoria é opcional).
- Usuário pode criar categorias e subcategorias customizadas.
- Não é possível criar uma subcategoria sem uma categoria pai.
- Cada alias pode ter categoria e subcategoria padrão associadas (ambas aplicadas automaticamente ao importar).
- Lançamentos sem categoria ficam como "Sem categoria".
- Ao excluir uma categoria que possui subcategorias, o sistema exige que o usuário reclassifique os lançamentos vinculados antes de confirmar a exclusão.

**Uso nos filtros:**
- Filtrar por categoria exibe todos os lançamentos daquela categoria, independente da subcategoria.
- Filtrar por subcategoria exibe apenas os lançamentos daquele nível específico.
- Buckets de orçamento podem ser definidos por categoria **ou** por subcategoria, permitindo tanto limites amplos ("Alimentação: R$ 1.500") quanto granulares ("Delivery: R$ 300").
- Gráficos suportam drill-down: clicando em uma fatia de categoria, o gráfico abre o detalhamento por subcategoria.

---

### 5.7 Buckets de Orçamento

O usuário define metas de gasto por categoria para um período (mês).

**Configuração de um bucket:**
| Campo | Descrição |
|---|---|
| Categoria | Vinculada a uma categoria existente |
| Limite mensal | Valor em R$ |
| Alerta em % | Percentual para disparar alerta (ex: 80%) |

**Comportamento:**
- O dashboard exibe o progresso de cada bucket (gasto atual vs. limite).
- Ao atingir o percentual de alerta, o bucket muda para status **Atenção** (amarelo).
- Ao atingir 100%, muda para **Estourado** (vermelho).
- O sistema notifica via banner no app quando um bucket é ultrapassado.
- Buckets são configurados por mês; o usuário pode copiar a configuração do mês anterior.

---

### 5.8 Dashboard e Gráficos

Visão geral dos gastos e saúde do orçamento.

**Componentes do dashboard:**

| Componente | Descrição |
|---|---|
| Resumo do mês | Total gasto no mês atual vs. mês anterior |
| Lançamentos pendentes | Contador com atalho para tela de aprovação |
| Progresso dos buckets | Barras de progresso por categoria |
| Gastos por cartão | Breakdown por cartão cadastrado |

**Gráficos disponíveis:**

| Gráfico | Tipo | Filtros |
|---|---|---|
| Gastos por categoria | Pizza / Donut com drill-down em subcategoria | Mês, período customizado |
| Evolução mensal de despesas | Linha / Barra | Últimos 3, 6, 12 meses |
| Gastos por cartão | Barra empilhada | Mês, período |
| Detalhamento de categoria | Barra horizontal com lançamentos | Mês, categoria |
| Receita vs Despesa | Barra agrupada (verde × vermelho) | Mês, período |
| Saldo líquido mensal | Linha / Área (verde/vermelho) | Últimos 3, 6, 12 meses |
| Breakdown de receitas | Pizza / Donut por tipo de receita | Mês, período |
| Previsto × Realizado (receita) | Barra agrupada com delta | Mês, período |

### 5.9 Cadastro de Contas Bancárias

O usuário registra suas contas bancárias como referência de origem dos débitos. Nenhuma conexão real com o banco é realizada.

**Campos:**
| Campo | Tipo | Obrigatório |
|---|---|---|
| Nome da conta | Texto livre (ex: "Nubank Conta", "Bradesco PF") | Sim |
| Banco | Texto livre | Sim |
| Tipo | Seleção (Corrente, Poupança, Pagamento, Outro) | Sim |
| Cor de identificação | Color picker | Não |

**Uso:**
- Débitos automáticos e contas de serviço referenciam uma conta cadastrada para indicar de onde o valor será debitado.
- Permite ao usuário ter uma visão por conta: "quanto sairá da minha conta X este mês".

---

### 5.10 Gastos Avulsos e Débito Automático

Lançamentos de gastos não vinculados a cartão de crédito (ex: débitos automáticos, pagamentos via PIX, boletos, saques).

**Fluxo — registro manual:**
1. Usuário clica em "Novo gasto avulso".
2. Preenche os campos e seleciona a conta bancária que será debitada (opcional).
3. Lançamento é salvo diretamente como aprovado ou como pendente, conforme escolha do usuário.

**Fluxo — débito automático recorrente:**
1. Usuário marca o gasto avulso como "recorrente" e define a periodicidade (mensal, semanal, anual) e o dia de vencimento.
2. O sistema gera automaticamente o próximo lançamento como **Pendente** na data prevista.
3. Usuário confirma o valor real quando o débito ocorrer (pode ser diferente do valor previsto).

**Campos do lançamento avulso:**
| Campo | Tipo |
|---|---|
| Descrição | Texto livre |
| Valor | Numérico |
| Data | Data |
| Conta bancária | Referência (opcional) |
| Categoria | Referência |
| Recorrente | Boolean |
| Periodicidade | Mensal / Semanal / Anual (se recorrente) |
| Dia de vencimento | Numérico (se recorrente) |
| Observação | Texto livre |

**Regras:**
- O lançamento avulso entra no cálculo dos buckets e da previsão futura normalmente.
- Flag "ignorar no orçamento" disponível (igual aos lançamentos de cartão).

---

### 5.11 Contas de Serviço (Fixas e Fixas Variáveis)

Módulo para gerenciar despesas recorrentes com valor total ou parcialmente previsível, como contas de luz, água, internet, gás, streaming e planos de saúde.

**Tipos:**

| Tipo | Descrição | Exemplos |
|---|---|---|
| **Fixa** | Valor não muda mês a mês | Netflix, plano de celular, academia |
| **Fixa Variável** | Valor oscila mas é estimável por histórico | Conta de luz, água, gás |

**Campos de cadastro de uma conta de serviço:**
| Campo | Tipo | Obrigatório |
|---|---|---|
| Nome | Texto livre | Sim |
| Tipo | Fixa / Fixa Variável | Sim |
| Conta bancária debitada | Referência (opcional) | Não |
| Cartão vinculado | Referência (opcional, alternativo à conta) | Não |
| Categoria | Referência | Sim |
| Dia de vencimento | Numérico (dia do mês) | Sim |
| Valor previsto inicial | Numérico | Sim |

**Fluxo mensal — conta Fixa:**
1. O sistema gera automaticamente um lançamento **Pendente** todo mês no dia de vencimento com o valor previsto.
2. Usuário aprova (valor bate) ou edita o valor real antes de aprovar.
3. O valor previsto não muda para contas fixas.

**Fluxo mensal — conta Fixa Variável:**
1. O sistema gera automaticamente um lançamento **Pendente** com o valor previsto calculado (média das últimas N ocorrências, padrão: 3 meses).
2. Quando a conta chega, usuário registra o valor real e aprova.
3. O sistema recalcula automaticamente o valor previsto futuro com base na média atualizada.
4. O novo valor previsto é exibido ao usuário com indicação de variação (↑ / ↓ em relação ao mês anterior).

**Ajuste da média:**
- Usa média móvel simples das últimas **3 ocorrências** por padrão.
- Usuário pode ajustar a janela da média (1, 3 ou 6 meses) por conta de serviço.
- Valores atípicos (ex: conta de luz no verão muito acima da média) podem ser marcados como "exceção" para não distorcer a previsão futura.

**Visualização na tela de contas de serviço:**
- Lista de todas as contas com: nome, tipo, valor previsto do próximo mês, status do mês atual (pendente / pago / atrasado).
- Histórico de valores pagos com gráfico de linha para contas variáveis.

---

### 5.12 Gráfico de Previsão de Gastos Futuros

Gráfico que consolida todas as fontes de despesa para projetar o gasto total dos próximos meses.

**Fontes consolidadas na previsão:**

| Fonte | Como entra na previsão |
|---|---|
| Buckets de orçamento | Limite configurado como teto esperado da categoria |
| Contas de serviço fixas | Valor fixo cadastrado |
| Contas de serviço fixas variáveis | Valor previsto calculado pela média histórica |
| Parcelas futuras (cartão) | Parcelas já lançadas e aprovadas com vencimento futuro |
| Débitos recorrentes | Valor configurado no gasto avulso recorrente |

**Tipos de visualização:**

| Gráfico | Descrição |
|---|---|
| Previsão mensal empilhada | Barra empilhada por categoria/fonte para os próximos 3, 6 ou 12 meses |
| Linha de tendência | Linha sobre as barras mostrando o total previsto vs. média histórica real |
| Detalhamento do mês | Ao clicar num mês futuro, lista os itens que compõem a previsão |

**Regras:**
- Meses passados exibem o valor **real** gasto; meses futuros exibem o valor **previsto**.
- O mês atual exibe o valor parcial real + projeção do restante.
- O gráfico diferencia visualmente real (opaco) de previsto (hachurado / transparente).
- Parcelas de cartão já registradas são consideradas valores certos (não estimativas) na previsão.

### 5.13 Divisão de Gastos (Split)

Permite ao usuário dividir qualquer lançamento de cartão com outras pessoas, rastrear o que cada uma deve e acompanhar os recebimentos mês a mês.

---

#### 5.13.1 Cadastro de Contatos (Devedores)

Lista de pessoas com quem o usuário costuma dividir gastos. Não requer cadastro delas no sistema.

**Campos:**
| Campo | Tipo | Obrigatório |
|---|---|---|
| Nome | Texto livre | Sim |
| Apelido | Texto livre | Não |
| Telefone / e-mail | Texto livre (apenas referência) | Não |
| Cor de identificação | Color picker | Não |

---

#### 5.13.2 Dividindo um Lançamento

O usuário pode acionar "Dividir" em qualquer lançamento aprovado de cartão.

**Modos de divisão:**

| Modo | Descrição |
|---|---|
| **Igualitário** | O valor total é dividido em partes iguais entre o usuário + os convidados selecionados |
| **Personalizado** | O usuário define manualmente o valor que cada pessoa deve |

**Fluxo:**
1. Usuário abre um lançamento e clica em "Dividir".
2. Seleciona um ou mais contatos da lista.
3. Escolhe o modo (igualitário ou personalizado) e confirma os valores.
4. O sistema calcula a **parte do usuário** (valor total − soma das partes dos outros).
5. A parte do usuário substitui o valor original do lançamento nos cálculos de bucket e previsão — o restante é dívida a receber.

**Divisão em lançamentos parcelados:**

Quando o lançamento é parcelado (ex: jantar R$ 300 em 3x de R$ 100), o usuário escolhe:

- **Dividir o total:** cada devedor deve R$ X de uma vez, sem vínculo com as parcelas. Simples, mas o usuário "adianta" o dinheiro ao longo das parcelas.
- **Dividir por parcela:** cada parcela mensal é dividida individualmente. O sistema cria uma cobrança proporcional por mês, alinhada com o que o cartão está debitando do usuário.

> _Exemplo:_ Viagem de R$ 900 em 6x com um amigo, 50/50. Modo "por parcela": o amigo deve R$ 75/mês por 6 meses. O sistema mostra essa expectativa de recebimento no gráfico futuro.

---

#### 5.13.3 Registro de Recebimentos

Quando o devedor paga, o usuário registra o recebimento manualmente.

**Ações disponíveis por dívida:**
- **Registrar pagamento parcial** — informa o valor recebido e a data; o saldo devedor é atualizado.
- **Quitar** — marca a dívida como totalmente paga (preenche o valor restante automaticamente).
- **Cancelar cobrança** — descarta a dívida (ex: o usuário decidiu presentear).

**Regras:**
- Um devedor pode ter múltiplos pagamentos parciais registrados.
- Ao quitar uma dívida parcelada antes do prazo, as cobranças futuras das parcelas restantes são encerradas.

---

#### 5.13.4 Visão por Devedor

Tela dedicada que exibe o saldo de cada contato com o usuário.

**Para cada contato:**
- Total devido ao usuário (soma de todas as dívidas em aberto)
- Total já recebido (histórico)
- Lista de dívidas individuais com: descrição do lançamento, valor original, valor devido, status (pendente / parcial / quitado)
- Botão de acesso rápido para registrar recebimento

**Filtros disponíveis:** todos os devedores / apenas com saldo em aberto / período de data.

---

#### 5.13.5 Visão Futura de Recebimentos (Mês a Mês)

Gráfico e tabela que mostram quanto o usuário espera receber de volta nos próximos meses.

**Composição:**
- Dívidas de parcelas futuras (modo "por parcela"): valor certo, mês certo.
- Dívidas sem data de vencimento (modo "total"): agrupadas em "a receber — sem data" e não projetadas em meses futuros até que o usuário defina uma data esperada de recebimento.

**Visualização:**

| Componente | Descrição |
|---|---|
| Gráfico de barras mensal | Valor total a receber por mês (próximos 6 meses) |
| Detalhamento ao clicar | Lista dos devedores e valores que compõem aquele mês |
| Tabela consolidada | Um linha por devedor × mês, com valores esperados |

**Regras:**
- O gráfico de previsão de gastos futuros (módulo 5.12) pode exibir, de forma sobreposta ou em linha separada, os recebimentos previstos para mostrar o **fluxo de caixa líquido** do mês (gastos previstos − recebimentos esperados).
- Recebimentos efetivados saem do gráfico futuro e entram no histórico.

---

### 5.14 Receitas — Cadastro e Previsão

Módulo para registrar e projetar todas as fontes de entrada de dinheiro do usuário, com controle de **previsto × realizado** e integração com os gráficos de fluxo financeiro.

---

#### 5.14.1 Tipos de Receita

O sistema oferece tipos padrão e permite que o usuário crie tipos customizados.

**Tipos padrão:**

| Tipo | Descrição | Natureza |
|---|---|---|
| Salário | Renda fixa mensal de vínculo empregatício | Recorrente fixa |
| Pró-labore | Retirada de sócio ou MEI | Recorrente fixa |
| Bônus | Gratificação eventual do empregador | Pontual |
| Freelance | Renda de trabalho avulso / projeto | Pontual ou recorrente variável |
| Aluguel recebido | Renda de imóvel ou bem alugado | Recorrente fixa |
| Dividendo | Rendimento de investimento | Recorrente variável |
| Reembolso | Devolução de despesa (ex: empresa paga conta) | Pontual |
| Outro | Renda não categorizada | — |

---

#### 5.14.2 Receitas Recorrentes

Para rendas que entram todo mês com valor fixo ou previsível (salário, pró-labore, aluguel).

**Campos de cadastro:**
| Campo | Tipo | Obrigatório |
|---|---|---|
| Nome / Descrição | Texto livre (ex: "Salário empresa X") | Sim |
| Tipo de receita | Referência | Sim |
| Conta de destino | Referência (ContaBancaria) | Não |
| Valor previsto | Numérico | Sim |
| Dia esperado de recebimento | Numérico (dia do mês) | Sim |
| Natureza | Fixa / Variável | Sim |
| Ativa | Boolean | Sim |

**Fluxo mensal:**
1. O sistema gera automaticamente um registro de receita com status **Previsto** no dia esperado.
2. Quando o valor cair na conta, o usuário confirma e informa o **valor real recebido**.
3. O sistema registra o delta (diferença previsto × realizado) e atualiza o histórico.
4. Para receitas do tipo **Variável** (ex: dividendo, aluguel com reajuste), o valor previsto do próximo mês é recalculado pela média das últimas 3 ocorrências — mesmo mecanismo das contas de serviço variáveis.

---

#### 5.14.3 Receitas Avulsas

Para entradas não recorrentes: bônus, freelances, reembolsos, vendas pontuais.

**Fluxo:**
1. Usuário clica em "Nova receita avulsa".
2. Preenche os campos e define se já foi recebida ou se é uma **expectativa futura**.
3. Receitas com status **Esperada** entram no gráfico de previsão de fluxo de caixa.
4. Ao receber, usuário confirma a data real e o valor real (que pode diferir do esperado).

**Campos:**
| Campo | Tipo |
|---|---|
| Descrição | Texto livre (ex: "Freelance projeto Y", "Bônus anual") |
| Tipo de receita | Referência |
| Conta de destino | Referência (opcional) |
| Valor esperado | Numérico |
| Data esperada | Data |
| Valor real recebido | Numérico (preenchido ao confirmar) |
| Data real | Data (preenchida ao confirmar) |
| Status | Esperada / Recebida / Cancelada |

---

#### 5.14.4 Previsto × Realizado

Visão consolidada que compara, mês a mês, o que o usuário esperava receber versus o que efetivamente entrou.

**Tabela mensal:**

| Coluna | Descrição |
|---|---|
| Mês | Referência |
| Receita prevista | Soma de todas as receitas com status Previsto/Esperada no mês |
| Receita realizada | Soma das receitas confirmadas no mês |
| Diferença (Δ) | Realizado − Previsto (positivo = entrou mais; negativo = entrou menos) |
| Despesa prevista | Total de gastos previstos (buckets + contas fixas + parcelas) |
| Despesa realizada | Total de gastos confirmados no mês |
| Saldo líquido | Receita realizada − Despesa realizada |

**Filtros:** período (últimos 3, 6, 12 meses ou customizado), tipo de receita.

---

#### 5.14.5 Gráficos de Receita × Despesa

Novos gráficos adicionados ao dashboard e à tela de relatórios:

| Gráfico | Tipo | Descrição |
|---|---|---|
| Receita vs Despesa mensal | Barra agrupada | Duas barras por mês: total de receitas (verde) e total de despesas (vermelho). Permite ver visualmente os meses no positivo ou negativo. |
| Saldo líquido mensal | Linha / Área | Linha mostrando (receita − despesa) por mês. Área preenchida: verde quando positivo, vermelho quando negativo. |
| Breakdown de receitas | Pizza / Donut | Composição das receitas do mês por tipo (salário, freelance, bônus, etc.). |
| Previsão de fluxo de caixa | Barra empilhada | Evolução futura consolidando receitas esperadas (entrada) e despesas previstas (saída) nos próximos meses. Linha de saldo líquido previsto sobreposta. |
| Previsto × Realizado (receita) | Barra agrupada | Para cada mês: barra de receita prevista ao lado da barra de receita realizada, com indicação do delta. |

**Integração com o gráfico 5.12 (Previsão de Gastos Futuros):**
- O gráfico de previsão existente é expandido para exibir também as receitas esperadas em cada mês futuro.
- Uma linha de **saldo líquido previsto** (receitas − despesas) é adicionada como sobreposição.
- O usuário pode alternar entre visualização de "apenas despesas" e "fluxo de caixa completo".

---

## 6. Modelo de Dados (Conceitual)

```
Cartão
  id, nome, banco, bandeira, ultimos_digitos, cor

ContaBancaria
  id, nome, banco, tipo (corrente | poupanca | pagamento | outro), cor

Lançamento
  id, cartao_id?, conta_id?,          -- pelo menos um ou nenhum (gasto sem vínculo)
  data, descricao_original, alias, valor,
  categoria_id, subcategoria_id?, parcela_atual, total_parcelas,
  status (pendente | aprovado | rejeitado),
  origem (fatura | comprovante | manual | recorrente | conta_servico),
  ignorar_orcamento, observacao

Alias
  id, padrao_match, nome_amigavel, categoria_id, subcategoria_id?

Categoria
  id, nome, cor, icone, is_custom

Subcategoria
  id, categoria_id, nome, is_custom

Bucket
  id, categoria_id, subcategoria_id?,   -- bucket pode ser em categoria ou subcategoria
  mes_referencia, limite, percentual_alerta

GastoRecorrente
  id, descricao, conta_id?, cartao_id?, categoria_id,
  valor_previsto, periodicidade (mensal | semanal | anual),
  dia_vencimento, ativo, observacao

ContaServico
  id, nome, tipo (fixa | fixa_variavel), conta_id?, cartao_id?,
  categoria_id, dia_vencimento, valor_previsto,
  janela_media (1 | 3 | 6), ativo

ContaServicoOcorrencia
  id, conta_servico_id, mes_referencia, valor_previsto,
  valor_real?, lancamento_id?, is_excecao,
  status (pendente | pago | atrasado)

Contato
  id, nome, apelido?, telefone?, email?, cor

DivisaoGasto
  id, lancamento_id, modo (igualitario | personalizado),
  modo_parcela (total | por_parcela),
  valor_total_dividido, valor_usuario

DivisaoParticipante
  id, divisao_id, contato_id,
  valor_devido_total,    -- total acordado
  valor_pago,            -- soma dos recebimentos registrados
  status (pendente | parcial | quitado),
  data_quitacao?

DivisaoParcelaMensal     -- criada apenas no modo "por_parcela"
  id, divisao_participante_id, mes_referencia,
  valor_esperado, valor_recebido?,
  status (pendente | recebido | cancelado)

Recebimento
  id, divisao_participante_id, divisao_parcela_id?,
  valor, data, observacao?

TipoReceita
  id, nome, natureza (fixa | variavel | pontual), is_custom

ReceitaRecorrente
  id, descricao, tipo_receita_id, conta_id?,
  valor_previsto, dia_esperado, natureza (fixa | variavel),
  janela_media (1 | 3 | 6), ativa

ReceitaOcorrencia         -- instância mensal de uma ReceitaRecorrente
  id, receita_recorrente_id, mes_referencia,
  valor_previsto, valor_real?,
  data_real?, status (previsto | recebido | cancelado)

ReceitaAvulsa
  id, descricao, tipo_receita_id, conta_id?,
  valor_esperado, data_esperada,
  valor_real?, data_real?,
  status (esperada | recebida | cancelada)
```

---

## 7. Requisitos Não Funcionais

| Requisito | Detalhe |
|---|---|
| Plataforma | Web (responsivo — funcionar bem no mobile via browser) |
| Autenticação | Login com e-mail + senha; suporte a 2FA desejável |
| Armazenamento de arquivos | Faturas e comprovantes em base64 no PostgreSQL; limite de 10 MB por arquivo; possibilidade de excluir o original após extração |
| Performance | Processamento de fatura deve retornar resultado em até 30s |
| Privacidade | Dados do usuário isolados; nenhum dado de cartão sensível armazenado |
| Exportação | Exportar lançamentos filtrados para CSV |

---

## 8. User Stories Prioritárias

### Épico 1 — Autenticação
- **US01:** Como novo usuário, quero criar uma conta com nome, e-mail e senha para ter acesso ao sistema.
- **US02:** Como usuário, quero fazer login com e-mail e senha para acessar meus dados financeiros com segurança.
- **US03:** Como usuário, quero recuperar minha senha por e-mail para retomar o acesso caso a esqueça.
- **US04:** Como usuário, quero ativar autenticação em dois fatores (2FA via TOTP) para aumentar a segurança da minha conta. _(Fase 6)_

### Épico 2 — Cartões
- **US05:** Como usuário, quero cadastrar meus cartões com bandeira, banco, nome e 4 últimos dígitos para identificá-los no sistema.

### Épico 3 — Contas Bancárias
- **US06:** Como usuário, quero cadastrar minhas contas bancárias (sem conexão real) para saber de qual conta cada débito sairá.

### Épico 4 — Faturas
- **US07:** Como usuário, quero fazer upload de uma fatura em PDF para que o sistema extraia os lançamentos automaticamente.
- **US08:** Como usuário, quero revisar os lançamentos extraídos antes de confirmá-los para corrigir erros de leitura.

### Épico 5 — Alias
- **US09:** Como usuário, quero criar um alias para uma descrição críptica para que futuros lançamentos apareçam com nome legível.
- **US10:** Como usuário, quero aplicar um alias retroativamente para corrigir lançamentos anteriores de uma vez.

### Épico 6 — Lançamentos e Débitos Avulsos
- **US11:** Como usuário, quero registrar um débito automático ou pagamento avulso vinculado a uma conta bancária para que ele entre no controle de gastos.
- **US12:** Como usuário, quero marcar um gasto avulso como recorrente para que o sistema gere automaticamente os próximos lançamentos sem precisar relançar manualmente.

### Épico 7 — Comprovantes
- **US13:** Como usuário, quero enviar uma foto de comprovante de maquininha para registrar o gasto antes da fatura fechar.
- **US14:** Como usuário, quero que o sistema sugira a fusão entre um comprovante pendente e um lançamento da fatura para evitar duplicatas.

### Épico 8 — Contas de Serviço
- **US15:** Como usuário, quero cadastrar minha conta de luz como "fixa variável" com valor previsto inicial, para que o sistema já considere esse gasto na previsão mensal.
- **US16:** Como usuário, quero registrar o valor real da conta quando ela chegar e ter a previsão do próximo mês atualizada automaticamente pela média histórica.
- **US17:** Como usuário, quero marcar uma ocorrência como "exceção" (ex: conta altíssima no verão) para que ela não distorça a média dos meses seguintes.
- **US18:** Como usuário, quero ver um histórico com gráfico de linha dos valores pagos em cada conta variável para entender a tendência de consumo.

### Épico 9 — Orçamento
- **US19:** Como usuário, quero definir um limite mensal por categoria para controlar meus gastos.
- **US20:** Como usuário, quero ser alertado quando um bucket atingir 80% do limite para tomar uma ação antes de estourar.

### Épico 10 — Receitas
- **US21:** Como usuário, quero cadastrar meu salário como receita recorrente fixa para que o sistema gere automaticamente a expectativa de entrada todo mês.
- **US22:** Como usuário, quero confirmar o valor real do salário quando ele cair na conta, mesmo que seja diferente do previsto, para manter o histórico fiel.
- **US23:** Como usuário, quero registrar um freelance como receita avulsa esperada para que ele apareça na previsão de fluxo de caixa antes de ser recebido.
- **US24:** Como usuário, quero ver a tabela de previsto × realizado mês a mês para entender se minhas receitas estão chegando conforme esperado.
- **US25:** Como usuário, quero ver um gráfico de barras com receitas e despesas lado a lado por mês para identificar rapidamente os meses em que gastei mais do que ganhei.
- **US26:** Como usuário, quero ver a linha de saldo líquido mensal com área colorida (verde/vermelho) para ter uma leitura instantânea da saúde financeira de cada período.
- **US27:** Como usuário, quero ver no gráfico de previsão futura tanto as despesas esperadas quanto as receitas esperadas, com uma linha de saldo líquido previsto, para planejar os próximos meses.

### Épico 11 — Divisão de Gastos
- **US28:** Como usuário, quero dividir um lançamento de cartão com um ou mais contatos, definindo o valor de cada um, para rastrear o que me devem.
- **US29:** Como usuário, quero dividir uma compra parcelada "por parcela", para que o sistema saiba que o amigo me deve um valor por mês ao longo das parcelas, não de uma vez só.
- **US30:** Como usuário, quero registrar um recebimento parcial de um devedor para manter o saldo correto sem precisar quitar a dívida toda de uma vez.
- **US31:** Como usuário, quero ver uma tela com o saldo de cada contato devedor e o histórico de dívidas e recebimentos, para saber exatamente quem ainda me deve algo.
- **US32:** Como usuário, quero ver um gráfico mês a mês dos recebimentos futuros esperados de divisões parceladas, para saber quanto voltará para o meu bolso nos próximos meses.
- **US33:** Como usuário, quero ver o fluxo de caixa líquido futuro (gastos previstos menos recebimentos esperados) para ter uma visão realista do meu orçamento à frente.

### Épico 12 — Previsão de Gastos Futuros
- **US34:** Como usuário, quero ver um gráfico de barras empilhadas com a previsão de gastos dos próximos 6 meses, consolidando buckets, contas fixas, contas variáveis e parcelas futuras de cartão.
- **US35:** Como usuário, quero clicar em um mês futuro no gráfico de previsão e ver o detalhamento de cada item que compõe aquele valor estimado.
- **US36:** Como usuário, quero que o gráfico diferencie visualmente o que já é valor real (passado) do que é previsão (futuro) para não confundir os dados.

### Épico 13 — Dashboard e Gráficos
- **US37:** Como usuário, quero ver um gráfico de gastos por categoria do mês atual para entender onde estou gastando mais.
- **US38:** Como usuário, quero ver a evolução dos meus gastos mensais nos últimos 12 meses para identificar tendências.

---

## 9. Stack Definida

### 9.1 Visão Geral

| Camada | Tecnologia | Notas |
|---|---|---|
| Frontend | Angular 21 + Angular Material | UI components nativos, design system consistente |
| Backend / API | Java 21 + Spring Boot | REST API, tipagem forte, ecossistema maduro |
| Autenticação | Spring Security | JWT + sessão; 2FA via TOTP configurável |
| Banco de dados | PostgreSQL | Relacional; armazena também arquivos em base64 |
| Storage de arquivos | PostgreSQL (base64) | Faturas e comprovantes salvos diretamente no banco |
| OCR / Parsing | Apache PDFBox + Tess4J | PDFBox para PDFs texto; Tess4J (Tesseract) para imagens |
| IA / Fallback OCR | LangChain4j | Fallback inteligente quando OCR convencional falha |
| Provedores de IA | Gemini, OpenAI, Claude (Anthropic) | Configuráveis via LangChain4j; prioridade definível |
| Gráficos | Apache ECharts (ngx-echarts) | Rico em tipos de gráfico; boa integração com Angular |
| Containerização | Docker + Docker Compose | Ambiente reproduzível; deploy simplificado |

---

### 9.2 Estratégia de OCR e IA

O processamento de documentos (faturas e comprovantes) segue uma pipeline em camadas com fallback progressivo:

```
Documento recebido (PDF ou imagem)
        │
        ▼
┌───────────────────────┐
│ 1. Apache PDFBox      │  Extração de texto de PDFs nativos (não-escaneados)
│    (texto direto)     │  → rápido, gratuito, sem custo de API
└───────────┬───────────┘
            │ falha ou PDF escaneado
            ▼
┌───────────────────────┐
│ 2. Tess4J / Tesseract │  OCR local para imagens e PDFs escaneados
│    (OCR local)        │  → sem custo de API, privacidade total
└───────────┬───────────┘
            │ confiança baixa (< threshold configurável)
            ▼
┌───────────────────────┐
│ 3. LangChain4j + IA   │  Extração via modelo de linguagem:
│   Gemini / OpenAI /   │  → interpreta layouts complexos, corrige OCR ruim
│   Claude (Anthropic)  │  → retorna JSON estruturado com os lançamentos
└───────────────────────┘
```

**Configurações do fallback de IA:**
- Provedor preferencial configurável pelo usuário (Gemini, OpenAI ou Claude).
- Chave de API fornecida pelo próprio usuário (sem dependência de conta da aplicação).
- Threshold de confiança do OCR local ajustável (padrão: 70%).
- Log de qual camada processou cada documento para auditoria.

**Prompt enviado à IA (modo fallback):**
A imagem/PDF é enviado junto com um prompt estruturado solicitando a extração de lançamentos em JSON com campos padronizados (data, descrição, valor, parcela). A resposta é validada antes de exibir ao usuário na tela de revisão.

---

### 9.3 Armazenamento de Arquivos em Banco

Faturas e comprovantes são armazenados como `TEXT` (base64) ou `BYTEA` diretamente no PostgreSQL.

**Justificativa:** elimina dependência de serviço de storage externo (S3, R2), simplifica backup e mantém os dados do usuário em um único lugar.

**Considerações:**
- Arquivos são comprimidos antes de armazenar para reduzir tamanho.
- Coluna `arquivo_original` separada da coluna `texto_extraido` para não misturar binário com dados indexáveis.
- Limite por arquivo: 10 MB (configurável). Acima disso, o sistema recusa o upload com mensagem orientativa.
- Possibilidade de excluir o arquivo original após extração confirmada, mantendo apenas o texto extraído.

---

### 9.4 Arquitetura Geral (Backend)

```
Angular 21 (SPA)
      │  HTTP/REST (JSON)
      ▼
Spring Boot (Java 21)
  ├── Controllers (REST)
  ├── Services (regras de negócio)
  ├── Repositories (Spring Data JPA)
  ├── Spring Security (JWT)
  └── OCR Service
        ├── PDFBoxExtractor
        ├── TesseractExtractor (Tess4J)
        └── LangChain4jExtractor
              ├── GeminiProvider
              ├── OpenAIProvider
              └── ClaudeProvider
      │
      ▼
PostgreSQL
```

---

### 9.5 Containerização (Docker)

#### Serviços

O sistema é composto por **3 containers**, orquestrados via `docker-compose.yml`:

| Container | Imagem base | Responsabilidade |
|---|---|---|
| `frontend` | `node:22-alpine` (build) → `nginx:alpine` (serve) | Serve o bundle Angular via Nginx |
| `backend` | `eclipse-temurin:21-jre-alpine` | API Spring Boot |
| `db` | `postgres:16-alpine` | Banco de dados PostgreSQL |

---

#### Estrutura de arquivos Docker

```
/
├── frontend/
│   ├── Dockerfile
│   └── nginx.conf
├── backend/
│   └── Dockerfile
├── docker-compose.yml
└── docker-compose.override.yml   ← configurações locais (gitignored)
```

---

#### Dockerfile — Frontend (multi-stage)

```dockerfile
# Stage 1: build
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# Stage 2: serve
FROM nginx:alpine
COPY --from=build /app/dist/finance-app/browser /usr/share/nginx/html
COPY nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
```

**nginx.conf** — garante que o roteamento do Angular (SPA) funcione:
```nginx
server {
  listen 80;
  root /usr/share/nginx/html;
  index index.html;

  location / {
    try_files $uri $uri/ /index.html;
  }

  location /api/ {
    proxy_pass http://backend:8080/;
  }
}
```

---

#### Dockerfile — Backend

```dockerfile
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> O JAR é gerado localmente via `mvn package -DskipTests` antes do `docker compose up --build`, ou via pipeline de CI.

---

#### docker-compose.yml

```yaml
services:

  db:
    image: postgres:16-alpine
    container_name: finance_db
    restart: unless-stopped
    environment:
      POSTGRES_DB: ${DB_NAME}
      POSTGRES_USER: ${DB_USER}
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - pgdata:/var/lib/postgresql/data
    networks:
      - finance_net

  backend:
    build: ./backend
    container_name: finance_backend
    restart: unless-stopped
    depends_on:
      - db
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/${DB_NAME}
      SPRING_DATASOURCE_USERNAME: ${DB_USER}
      SPRING_DATASOURCE_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      OCR_CONFIDENCE_THRESHOLD: ${OCR_THRESHOLD:-70}
      AI_PROVIDER: ${AI_PROVIDER:-gemini}         # gemini | openai | claude
      AI_API_KEY: ${AI_API_KEY}
    networks:
      - finance_net

  frontend:
    build: ./frontend
    container_name: finance_frontend
    restart: unless-stopped
    ports:
      - "80:80"
    depends_on:
      - backend
    networks:
      - finance_net

volumes:
  pgdata:

networks:
  finance_net:
    driver: bridge
```

> O frontend é o único container com porta exposta externamente (`80:80`). O backend e o banco são acessíveis apenas pela rede interna `finance_net`.

---

#### Variáveis de Ambiente (.env)

Arquivo `.env` na raiz (não comitado no repositório):

```env
# Banco
DB_NAME=finance
DB_USER=finance_user
DB_PASSWORD=troque_esta_senha

# JWT
JWT_SECRET=chave_secreta_longa_e_aleatoria

# OCR
OCR_THRESHOLD=70

# IA (provedor e chave)
AI_PROVIDER=gemini
AI_API_KEY=sua_chave_aqui
```

`.env.example` comitado no repositório com valores de placeholder para documentar as variáveis necessárias.

---

#### Comandos do dia a dia

```bash
# Subir tudo (primeira vez ou após mudança de código)
docker compose up --build

# Subir em background
docker compose up -d

# Ver logs do backend em tempo real
docker compose logs -f backend

# Rodar migrations manualmente (se necessário)
docker compose exec backend java -jar app.jar --migrate

# Acessar o banco via psql
docker compose exec db psql -U finance_user -d finance

# Parar e remover containers (mantém volume do banco)
docker compose down

# Parar e remover tudo incluindo dados do banco
docker compose down -v
```

---

#### Healthchecks

O `backend` aguarda o `db` estar pronto antes de iniciar via `depends_on` + healthcheck no serviço `db`:

```yaml
  db:
    ...
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ${DB_USER} -d ${DB_NAME}"]
      interval: 5s
      timeout: 5s
      retries: 10

  backend:
    depends_on:
      db:
        condition: service_healthy
```

---

#### Considerações de Produção

| Aspecto | Recomendação |
|---|---|
| HTTPS | Adicionar container `nginx-proxy` ou `Traefik` como reverse proxy com TLS |
| Secrets | Usar Docker Secrets ou variável de ambiente injetada pelo CI/CD em vez de `.env` em produção |
| Backup do banco | Volume `pgdata` deve ser incluído na rotina de backup do host |
| Build do JAR | Pipeline de CI (GitHub Actions / GitLab CI) gera o JAR antes de construir a imagem |
| Deploy | `docker compose pull && docker compose up -d` no servidor; ou migrar para Docker Swarm / Kubernetes conforme necessidade de escala |

---

## 10. Fases de Entrega Sugeridas

### Fase 1 — MVP (4–6 semanas)
- **Autenticação:** cadastro de conta, login e recuperação de senha
- Cadastro de cartões e contas bancárias
- Importação de faturas PDF (texto)
- Sistema de alias
- Categorias
- Gastos avulsos manuais (com vínculo a conta bancária)
- Histórico de lançamentos com filtros
- Exportação CSV

### Fase 2 — Comprovantes, Orçamento e Débitos Recorrentes (3–4 semanas)
- Upload e leitura de comprovantes de maquininha
- Rotina de aprovação (lançamentos pendentes)
- Conciliação comprovante × fatura
- Buckets de orçamento com alertas
- Gastos avulsos recorrentes (débito automático)

### Fase 3 — Contas de Serviço e Previsão (3–4 semanas)
- Cadastro de contas de serviço (fixas e fixas variáveis)
- Geração automática de lançamentos pendentes por vencimento
- Ajuste de valor previsto por média histórica
- Marcação de exceção em ocorrências atípicas
- Gráfico de previsão de gastos futuros (consolidado)

### Fase 4 — Divisão de Gastos (2–3 semanas)
- Cadastro de contatos (devedores)
- Split de lançamento (igualitário e personalizado)
- Split por parcela em compras parceladas
- Registro de recebimentos (total e parcial)
- Visão por devedor com saldo consolidado
- Gráfico de recebimentos futuros mês a mês
- Fluxo de caixa líquido (gastos − recebimentos) no gráfico de previsão

### Fase 5 — Receitas e Fluxo de Caixa (2–3 semanas)
- Tipos de receita (padrão e customizados)
- Cadastro de receitas recorrentes (salário, pró-labore, aluguel)
- Receitas avulsas com status esperada / recebida
- Confirmação de recebimento com valor real
- Tabela previsto × realizado (receitas e despesas)
- Gráficos: Receita vs Despesa, Saldo Líquido, Breakdown de receitas
- Integração do gráfico de previsão com receitas esperadas + linha de saldo líquido

### Fase 6 — Gráficos Avançados e Polimento (2–3 semanas)
- Dashboard completo com todos os gráficos
- Detalhamento do mês no gráfico de previsão
- OCR para faturas escaneadas (imagem)
- 2FA

---

## 11. Métricas de Sucesso

- Taxa de acerto do parser de faturas ≥ 90% dos lançamentos extraídos corretamente.
- Tempo médio de revisão de uma fatura < 2 minutos.
- Usuário consegue visualizar todos os gastos do mês sem abrir nenhum app de banco.
