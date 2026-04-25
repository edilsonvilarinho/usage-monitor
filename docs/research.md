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

**Schema confirmado (resposta real do endpoint):**

```json
{
  "model_remains": [
    {
      "start_time": 1777075200000,
      "end_time": 1777093200000,
      "remains_time": 10320279,
      "current_interval_total_count": 4500,
      "current_interval_usage_count": 0,
      "model_name": "MiniMax-M*",
      "current_weekly_total_count": 45000,
      "current_weekly_usage_count": 2223,
      "weekly_start_time": 1776643200000,
      "weekly_end_time": 1777248000000,
      "weekly_remains_time": 165120279
    }
  ],
  "base_resp": {
    "status_code": 0,
    "status_msg": "success"
  }
}
```

**Descrição dos campos:**

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `start_time` | Long (epoch ms) | Início do intervalo atual |
| `end_time` | Long (epoch ms) | Fim do intervalo atual |
| `remains_time` | Long (ms) | Milissegundos restantes no intervalo |
| `current_interval_total_count` | Long | Total de requisições permitidas no intervalo |
| `current_interval_usage_count` | Long | Requisições usadas no intervalo |
| `model_name` | String | Nome do modelo (ex: "MiniMax-M*", "speech-hd") |
| `current_weekly_total_count` | Long | Total semanal permitido |
| `current_weekly_usage_count` | Long | Total semanal usado |
| `weekly_start_time` | Long (epoch ms) | Início da semana |
| `weekly_end_time` | Long (epoch ms) | Fim da semana |
| `weekly_remains_time` | Long (ms) | Milissegundos restantes na semana |

**Observações importantes:**
- A unidade de quota é **requisições** (request count), não tokens
- A resposta retorna múltiplos modelos (text, speech, video, music, image)
- `base_resp.status_code == 0` significa sucesso
- Timestamps em **milissegundos** (epoch), não segundos

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
