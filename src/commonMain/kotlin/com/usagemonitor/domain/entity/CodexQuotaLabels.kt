package com.usagemonitor.domain.entity

/**
 * Rótulos das cotas do Codex que vêm de um limite por modelo (issue #324), lidos
 * do rollout local: o `wham/usage` só devolve o limite da conta, e o limite de um
 * modelo — `GPT-5.3-Codex-Spark` no relato do codenotch#286 — bloqueia o usuário
 * sem aparecer ali.
 *
 * O rótulo é chave de série do histórico, então é estável e em inglês:
 * "Codex limit <nome> (5h)". O título do card sai do `periodType` e diria
 * "Sessão 5h" duas vezes; [groupOf] devolve o nome do limite, como
 * `CursorQuotaLabels.groupOf` faz com as franquias.
 */
object CodexQuotaLabels {
    private const val PREFIX = "Codex limit "
    private val PATTERN = Regex("""^Codex limit (.+) \(([^()]+)\)$""")

    fun modelLimit(name: String, window: String): String = "$PREFIX$name ($window)"

    /** "Codex limit GPT-5.3-Codex-Spark (7d)" → "GPT-5.3-Codex-Spark"; outro rótulo → `null`. */
    fun groupOf(label: String): String? = PATTERN.matchEntire(label)?.groupValues?.get(1)
}
