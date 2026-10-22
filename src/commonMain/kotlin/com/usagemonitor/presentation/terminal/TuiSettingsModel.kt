package com.usagemonitor.presentation.terminal

import com.usagemonitor.domain.entity.redactedSecret

/**
 * O que uma tecla da aba 5 pede ao laço: o id do comando da 4.3 que a aplica (quem monta o
 * comando é o `desktopMain`) e o valor. A aba não tem regra própria de gravação.
 */
data class TuiSettingAction(val id: String, val value: String? = null, val secret: Boolean = false) {
    // O texto de um campo secreto passa por aqui: nunca no `toString` (log, falha não tratada).
    override fun toString(): String = "TuiSettingAction(id=$id, value=${if (secret) redactedSecret(value) else value})"
}

/** Uma edição de texto: o comando que recebe o texto, o texto inicial e se é segredo (sem eco). */
data class TuiSettingEdit(val id: String, val initial: String = "", val secret: Boolean = false) {
    override fun toString(): String = "TuiSettingEdit(id=$id, initial=${if (secret) redactedSecret(initial) else initial}, secret=$secret)"
}

/** Um campo da aba 5 (`cli/readme.md`, "Campo de configuração"). [note] vai em muted à direita. */
sealed interface TuiSettingField {
    val label: String
    val note: String?

    /** `[x]`/`[ ]`: espaço manda [action], que já leva o valor oposto. [rename]: `enter` edita o apelido. */
    data class Toggle(
        override val label: String,
        val on: Boolean,
        val action: TuiSettingAction,
        override val note: String? = null,
        val rename: TuiSettingEdit? = null
    ) : TuiSettingField

    /** `‹ 70 ›`: `← →` mandam o vizinho, quando há. */
    data class Stepper(
        override val label: String,
        val shown: String,
        val previous: TuiSettingAction?,
        val next: TuiSettingAction?,
        override val note: String? = null
    ) : TuiSettingField

    /** `[ valor ]`: `enter` abre a edição; [shown] nulo é "desligado" ou "vazio". */
    data class Text(override val label: String, val shown: String?, val edit: TuiSettingEdit, override val note: String? = null) : TuiSettingField

    /** Uma ação de `enter`. [confirm]: apaga ou invalida algo, e pede um segundo `enter`. */
    data class Action(override val label: String, val action: TuiSettingAction, override val note: String? = null, val confirm: Boolean = false) : TuiSettingField

    /** Só leitura: o estado de algo que a aba não muda. */
    data class Info(override val label: String, val value: String, override val note: String? = null) : TuiSettingField
}

/**
 * Uma seção das Configurações. [restartNeeded]: os campos só valem depois de reiniciar quem
 * coleta (o proxy). [hint]: o que a seção não faz, com o comando que faz.
 */
data class TuiSettingsSection(
    val title: String,
    val fields: List<TuiSettingField>,
    val restartNeeded: Boolean = false,
    val hint: String? = null
)

data class TuiSettings(val sections: List<TuiSettingsSection>)

/** O texto em edição de um campo. */
data class TuiTextEdit(val edit: TuiSettingEdit, val text: String) {
    override fun toString(): String = "TuiTextEdit(edit=$edit, text=${if (edit.secret) redactedSecret(text) else text})"
}

/**
 * A seção e o campo da aba 5. [inFields]: a seleção está nos campos, e não na lista de seções.
 * [editing]: um campo de texto aberto, que recebe toda tecla menos Ctrl+C. [confirming]: a
 * ação que espera o segundo `enter`.
 */
data class TuiSettingsState(
    val section: Int = 0,
    val field: Int = 0,
    val inFields: Boolean = false,
    val editing: TuiTextEdit? = null,
    val confirming: TuiSettingAction? = null
) {
    fun reduce(key: TuiKey, sections: List<TuiSettingsSection>): Pair<TuiSettingsState, List<TuiEffect>>? {
        editing?.let { edit -> return reduceEdit(key, edit) }
        if (sections.isEmpty()) {
            return null
        }
        val sectionIndex = section.coerceIn(0, sections.lastIndex)
        return if (inFields) reduceFields(key, sections, sectionIndex) else reduceList(key, sections, sectionIndex)
    }

    private fun reduceList(key: TuiKey, sections: List<TuiSettingsSection>, current: Int): Pair<TuiSettingsState, List<TuiEffect>>? =
        when (key) {
            TuiKey.Up -> copy(section = (current - 1).coerceAtLeast(0), field = 0) to emptyList()
            TuiKey.Down -> copy(section = (current + 1).coerceAtMost(sections.lastIndex), field = 0) to emptyList()
            TuiKey.Enter, TuiKey.Right, TuiKey.Tab -> {
                val first = selectable(sections[current]).firstOrNull()
                if (first == null) null else copy(section = current, field = first, inFields = true) to emptyList()
            }
            else -> null
        }

    private fun reduceFields(key: TuiKey, sections: List<TuiSettingsSection>, current: Int): Pair<TuiSettingsState, List<TuiEffect>>? {
        val fields = sections[current].fields
        val choices = selectable(sections[current])
        val selected = field.takeIf { index -> index in choices } ?: choices.firstOrNull() ?: return copy(inFields = false) to emptyList()
        val target = fields[selected]
        // O segundo `enter` confirma; qualquer outra tecla desiste.
        val pending = confirming
        if (pending != null) {
            return if (key == TuiKey.Enter) copy(confirming = null) to listOf(TuiEffect.ApplySetting(pending)) else copy(confirming = null) to emptyList()
        }
        return when (key) {
            TuiKey.Up -> copy(field = choices.lastOrNull { index -> index < selected } ?: selected) to emptyList()
            TuiKey.Down -> copy(field = choices.firstOrNull { index -> index > selected } ?: selected) to emptyList()
            TuiKey.Escape -> copy(inFields = false) to emptyList()
            TuiKey.Tab -> {
                val next = (current + 1) % sections.size
                copy(section = next, field = selectable(sections[next]).firstOrNull() ?: 0, inFields = selectable(sections[next]).isNotEmpty()) to emptyList()
            }
            TuiKey.Space -> (target as? TuiSettingField.Toggle)?.let { toggle -> copy(field = selected) to listOf(TuiEffect.ApplySetting(toggle.action)) }
            TuiKey.Left -> (target as? TuiSettingField.Stepper)?.previous?.let { action -> copy(field = selected) to listOf(TuiEffect.ApplySetting(action)) }
            TuiKey.Right -> (target as? TuiSettingField.Stepper)?.next?.let { action -> copy(field = selected) to listOf(TuiEffect.ApplySetting(action)) }
            TuiKey.Enter -> enter(target, selected)
            else -> null
        }
    }

    private fun enter(target: TuiSettingField, selected: Int): Pair<TuiSettingsState, List<TuiEffect>>? =
        when (target) {
            is TuiSettingField.Text -> copy(field = selected, editing = TuiTextEdit(target.edit, if (target.edit.secret) "" else target.edit.initial)) to emptyList()
            is TuiSettingField.Toggle -> target.rename?.let { edit -> copy(field = selected, editing = TuiTextEdit(edit, edit.initial)) to emptyList() }
            is TuiSettingField.Action ->
                if (target.confirm) copy(field = selected, confirming = target.action) to emptyList() else copy(field = selected) to listOf(TuiEffect.ApplySetting(target.action))
            is TuiSettingField.Stepper, is TuiSettingField.Info -> null
        }

    /** Em edição, o campo recebe tudo: `enter` grava, `esc` desiste e nada muda. */
    private fun reduceEdit(key: TuiKey, edit: TuiTextEdit): Pair<TuiSettingsState, List<TuiEffect>> =
        when (key) {
            TuiKey.Enter -> copy(editing = null) to listOf(TuiEffect.ApplySetting(TuiSettingAction(edit.edit.id, edit.text, secret = edit.edit.secret)))
            TuiKey.Escape -> copy(editing = null) to emptyList()
            TuiKey.Backspace -> copy(editing = edit.copy(text = edit.text.dropLast(1))) to emptyList()
            TuiKey.Space -> copy(editing = edit.copy(text = edit.text + " ")) to emptyList()
            is TuiKey.Text -> copy(editing = edit.copy(text = (edit.text + key.char).take(MAX_TEXT))) to emptyList()
            else -> this to emptyList()
        }

    companion object {
        /** Um token de bot tem 46 caracteres; uma URL de servidor raramente passa de 100. */
        const val MAX_TEXT = 200

        /** Os campos que a seleção visita: tudo menos o que é só leitura. */
        fun selectable(section: TuiSettingsSection): List<Int> =
            section.fields.indices.filter { index -> section.fields[index] !is TuiSettingField.Info }
    }
}
