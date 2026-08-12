# API Research — Usage Monitor

Documento de descoberta das APIs Anthropic e MiniMax.
Gerado na Tarefa 1 com auxílio do servidor MCP Context7.

---

## 1. Anthropic — Endpoint de uso OAuth

### Estratégia de monitoramento

Existe um endpoint dedicado de uso para o token OAuth do Claude.ai. A estratégia
antiga — `POST /v1/messages` com payload mínimo só para ler os headers
`anthropic-ratelimit-*` — foi substituída no commit `d305af3` e **não é mais usada**:
consumia token a cada poll e não expunha os créditos de uso.

### Endpoint

```
GET https://api.anthropic.com/api/oauth/usage
```

### Autenticação

O token OAuth do Claude.ai é lido dinamicamente do ficheiro de credenciais local:

```
~/.claude/.credentials.json  →  claudeAiOauth.accessToken
```

Headers HTTP da requisição:
```
Authorization: Bearer <accessToken>
User-Agent: claude-code/1.0.0
anthropic-beta: oauth-2025-04-20
Accept: application/json
```

### Response JSON (campos consumidos pelo app)

```json
{
  "five_hour": { "utilization": 21.5, "resets_at": "2026-08-11T20:00:00Z" },
  "seven_day": { "utilization": 50.0, "resets_at": "2026-08-15T20:00:00Z" },
  "extra_usage": {
    "is_enabled": true,
    "monthly_limit": 55000,
    "used_credits": 32784.0,
    "utilization": 59.60727272727273,
    "currency": "BRL",
    "decimal_places": 2,
    "credits_ever_enabled": true
  },
  "spend": {
    "used":  { "amount_minor": 32784, "currency": "BRL", "exponent": 2 },
    "limit": { "amount_minor": 55000, "currency": "BRL", "exponent": 2 },
    "percent": 60,
    "enabled": true
  }
}
```

| Campo | Tipo | Descrição |
|-------|------|-----------|
| `five_hour.utilization` | Double | Percentual (0–100) usado na janela de 5h |
| `five_hour.resets_at` | ISO 8601 String ou null | Reinício da janela; nulo enquanto a Anthropic não materializa |
| `seven_day.*` | idem | Mesma semântica na janela semanal |
| `extra_usage.is_enabled` | Boolean | `false` zera todos os demais campos do bloco |
| `extra_usage.monthly_limit` | Long (unidade menor) | Limite mensal de créditos: 55000 = R$ 550,00 |
| `extra_usage.used_credits` | Double (unidade menor) | Créditos consumidos: 32784.0 = R$ 327,84 |
| `extra_usage.utilization` | Double | Percentual já pronto e mais preciso que `spend.percent` (arredondado) |
| `extra_usage.currency` | String | Moeda real da conta — **pode não ser USD** |
| `spend.used/limit` | Objeto | Reforço monetário (`amount_minor` + `exponent`); com o recurso desligado a moeda cai para o default "USD" |

A resposta ainda traz `seven_day_opus`, `seven_day_sonnet`, `seven_day_cowork`,
`limits[]` e outros blocos, ignorados pelo DTO (`ignoreUnknownKeys`).

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

- O poll é somente leitura: não consome token do plano
- O `accessToken` pode expirar — `expiresAt` deve ser verificado; se expirado, usar `refreshToken`
- Esta estratégia usa o tier do plano do usuário, não uma API key de developer
- Os valores de crédito são monetários em unidade menor; a moeda vem em `extra_usage.currency` e não pode ser assumida como USD

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
| `GET /api/oauth/usage` para Anthropic | Leitura direta das janelas e dos créditos, sem consumir token do plano |
| Polling a cada 10 minutos | Equilíbrio entre atualização e carga na API |
| Timezone `America/Sao_Paulo` | Requisito explícito do projeto; label "BRT" em PT-BR |

---

## 4. Próximos Passos

- [ ] Usuário executa o `curl` acima e cola o JSON de resposta
- [ ] Confirmar campos do `MiniMaxTokenPlanDto`
- [ ] Iniciar Task 2 após aprovação do schema
