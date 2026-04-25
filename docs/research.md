# API Research — Usage Monitor

Documento de descoberta das APIs Anthropic e MiniMax.
Gerado na Tarefa 1 com auxílio do servidor MCP Context7.

---

## 1. Anthropic — Rate Limit Headers

### Estratégia de monitoramento

Não existe um endpoint dedicado de "saldo de tokens" para o OAuth token do Claude.ai.
A abordagem adotada é fazer uma chamada mínima à API e ler os headers de rate limit
que a Anthropic retorna em **toda** resposta da API.

### Endpoint

```
POST https://api.anthropic.com/v1/messages
```

### Autenticação

O token OAuth do Claude.ai é lido dinamicamente do ficheiro de credenciais local:

```
~/.claude/.credentials.json  →  claudeAiOauth.accessToken
```

Header HTTP da requisição:
```
Authorization: Bearer <accessToken>
anthropic-version: 2023-06-01
Content-Type: application/json
```

### Payload mínimo (consome ~1 token)

```json
{
  "model": "claude-haiku-4-5-20251001",
  "max_tokens": 1,
  "messages": [
    { "role": "user", "content": "hi" }
  ]
}
```

### Headers de rate limit retornados na resposta

| Header | Tipo | Descrição |
|--------|------|-----------|
| `anthropic-ratelimit-tokens-limit` | Long | Limite total de tokens na janela atual |
| `anthropic-ratelimit-tokens-remaining` | Long | Tokens restantes na janela atual |
| `anthropic-ratelimit-tokens-reset` | ISO 8601 String | Timestamp de reset da janela |
| `anthropic-ratelimit-requests-limit` | Long | Limite de requisições na janela |
| `anthropic-ratelimit-requests-remaining` | Long | Requisições restantes na janela |
| `anthropic-ratelimit-requests-reset` | ISO 8601 String | Timestamp de reset de requisições |

### Estrutura do `.credentials.json`

```
~/.claude/.credentials.json
```

```json
{
  "claudeAiOauth": {
    "accessToken": "...",
    "refreshToken": "...",
    "expiresAt": 1234567890000,
    "scopes": ["..."],
    "subscriptionType": "...",
    "rateLimitTier": "..."
  }
}
```

O caminho do ficheiro é resolvido dinamicamente:
```kotlin
val home = System.getProperty("user.home")  // Linux: /home/user | Windows: C:\Users\user
val path = "$home/.claude/.credentials.json"
```

### ⚠️ Pontos de atenção

- Cada poll consome ~1 token de input (chamada mínima ao claude-haiku)
- A janela de rate limit é tipicamente de **1 minuto** para tokens
- O `accessToken` pode expirar — `expiresAt` deve ser verificado; se expirado, usar `refreshToken`
- Esta estratégia usa o tier do plano do usuário, não uma API key de developer

---

## 2. MiniMax — Token Plan Remains

### Endpoint (confirmado via Context7 / documentação oficial MiniMax)

```
GET https://www.minimax.io/v1/token_plan/remains
```

### Autenticação

A API Key é lida **exclusivamente** de variável de ambiente (segurança — nunca hardcoded):
```kotlin
val apiKey = System.getenv("MINIMAX_API_KEY")
    ?: error("Variável de ambiente MINIMAX_API_KEY não configurada")
```

Header HTTP:
```
Authorization: Bearer <MINIMAX_API_KEY>
Content-Type: application/json
```

### Response JSON

⚠️ **PENDENTE VERIFICAÇÃO MANUAL**

O Context7 confirmou o endpoint e o método de autenticação, mas **não retornou**
o schema JSON da resposta deste endpoint específico.

**Ação necessária antes do Task 2:**
Execute o comando abaixo e cole o JSON de resposta neste documento:

```bash
curl --location 'https://www.minimax.io/v1/token_plan/remains' \
  --header "Authorization: Bearer $MINIMAX_API_KEY" \
  --header 'Content-Type: application/json'
```

**Campos prováveis baseados em padrões da API MiniMax** (a confirmar):

```json
{
  "total_tokens": 1000000,
  "remaining_tokens": 750000,
  "used_tokens": 250000,
  "expires_at": "2025-12-31T23:59:59Z"
}
```

Os DTOs e Mappers da camada `data` só serão criados após confirmação do schema real.

---

## 3. Decisões Tomadas

| Decisão | Justificativa |
|---------|---------------|
| OAuth token para Anthropic | Único token disponível no `.credentials.json`; não é uma API key de developer |
| MINIMAX_API_KEY via env var | Segurança — proibido hardcode por protocolo do projeto |
| Modelo `claude-haiku-4-5-20251001` para poll | Menor custo de tokens para chamada de sondagem |
| Polling a cada 10 minutos | Equilíbrio entre atualização e consumo de tokens |
| Timezone `America/Sao_Paulo` | Requisito explícito do projeto; label "BRT" em PT-BR |

---

## 4. Próximos Passos

- [ ] Usuário executa o `curl` acima e cola o JSON de resposta
- [ ] Confirmar campos do `MiniMaxTokenPlanDto`
- [ ] Iniciar Task 2 após aprovação do schema
