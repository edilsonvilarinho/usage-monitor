package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import com.usagemonitor.presentation.ui.theme.AppSpacing

/** Pausa de digitação que caracteriza uma edição terminada. */
const val DEFAULT_COMMIT_DEBOUNCE_MILLIS = 800L

/**
 * Campo de texto que só propaga o valor quando a edição termina.
 *
 * **Por que não propagar a cada tecla:** o valor sobe para quem persiste, e
 * gravar por caractere escreve em disco dezenas de vezes por edição e faria o
 * aviso de "salvo" piscar a cada letra. Aqui o commit sai numa pausa de
 * [debounceMillis], ao perder o foco, ou quando o campo é descartado — fechar o
 * diálogo logo depois de digitar não pode perder a alteração.
 *
 * **Por que o estado é local:** controlar o campo pelo valor que volta de fora
 * atrasa o cursor em um caractere, porque a seleção é recalculada a partir de um
 * texto que ainda não chegou (issue #19). O texto de fora só re-semeia o campo
 * quando muda por conta própria — uma normalização de quem grava, por exemplo.
 *
 * [validate] devolve a mensagem de erro, ou `null` quando o texto serve. Um
 * texto inválido nunca é propagado: durante a digitação o campo só mostra o
 * erro, e ao perder o foco volta para o último valor aceito.
 */
@Composable
fun DebouncedTextField(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    validate: (String) -> String? = { null },
    debounceMillis: Long = DEFAULT_COMMIT_DEBOUNCE_MILLIS
) {
    DebouncedTextFieldCore(
        value = value,
        label = label,
        onCommit = onCommit,
        modifier = modifier,
        placeholder = placeholder,
        validate = validate,
        debounceMillis = debounceMillis,
        visualTransformation = VisualTransformation.None,
        trailingIcon = null
    )
}

/**
 * Variante mascarada, com alternância de visibilidade.
 *
 * O olho existe para conferir o que foi colado: uma chave de servidor errada só
 * se descobre pelo erro do "Testar conexão" se não der para ler o campo.
 */
@Composable
fun DebouncedSecretField(
    value: String,
    label: String,
    revealLabel: String,
    hideLabel: String,
    onCommit: (String) -> Unit,
    modifier: Modifier = Modifier,
    validate: (String) -> String? = { null },
    debounceMillis: Long = DEFAULT_COMMIT_DEBOUNCE_MILLIS
) {
    var revealed by remember { mutableStateOf(false) }

    DebouncedTextFieldCore(
        value = value,
        label = label,
        onCommit = onCommit,
        modifier = modifier,
        placeholder = null,
        validate = validate,
        debounceMillis = debounceMillis,
        visualTransformation = if (revealed) {
            VisualTransformation.None
        } else {
            PasswordVisualTransformation()
        },
        trailingIcon = {
            // Quadrado de 26dp: o círculo de 40dp do Material era mais alto que
            // o campo de 28 ao lado do qual ele fica.
            AppIconButton(
                contentDescription = if (revealed) hideLabel else revealLabel,
                onClick = { revealed = !revealed }
            ) {
                Icon(
                    imageVector = if (revealed) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun DebouncedTextFieldCore(
    value: String,
    label: String,
    onCommit: (String) -> Unit,
    modifier: Modifier,
    placeholder: String?,
    validate: (String) -> String?,
    debounceMillis: Long,
    visualTransformation: VisualTransformation,
    trailingIcon: (@Composable () -> Unit)?
) {
    // Lidas dentro de efeitos de ciclo longo; sem isto o onDispose chamaria a
    // lambda da primeira composição.
    val currentOnCommit by rememberUpdatedState(onCommit)
    val currentValidate by rememberUpdatedState(validate)

    var fieldValue by remember { mutableStateOf(TextFieldValue(value, TextRange(value.length))) }
    var committedValue by remember { mutableStateOf(value) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // `commit` roda também no onDispose, fora de composição: nada aqui pode ler
    // parâmetros diretamente, só os estados e os `rememberUpdatedState`.
    fun commit(text: String, revertWhenInvalid: Boolean) {
        if (text == committedValue) {
            errorMessage = null
            return
        }

        val error = currentValidate(text)
        if (error != null) {
            if (revertWhenInvalid) {
                // Saiu do campo com texto inválido: o valor bom volta e o erro
                // sai junto, senão sobraria um aviso sobre um texto que não
                // está mais na tela.
                fieldValue = TextFieldValue(committedValue, TextRange(committedValue.length))
                errorMessage = null
            } else {
                errorMessage = error
            }
            return
        }

        errorMessage = null
        committedValue = text
        currentOnCommit(text)
    }

    // Valor alterado por fora (normalização de quem gravou, reset da tela). Não
    // dispara com a nossa própria edição: ali `value` chega igual ao committed.
    LaunchedEffect(value) {
        if (value != committedValue) {
            committedValue = value
            fieldValue = TextFieldValue(value, TextRange(value.length))
            errorMessage = null
        }
    }

    // Reiniciado a cada tecla, então só a pausa chega ao delay.
    LaunchedEffect(fieldValue.text) {
        if (fieldValue.text == committedValue) {
            return@LaunchedEffect
        }
        delay(debounceMillis)
        commit(fieldValue.text, revertWhenInvalid = false)
    }

    DisposableEffect(Unit) {
        onDispose {
            // Sem revert: a tela está indo embora e não há onde mostrar o erro.
            commit(fieldValue.text, revertWhenInvalid = false)
        }
    }

    // Rótulo em cima, campo embaixo, erro abaixo do campo — em vez do rótulo
    // flutuante e do `supportingText` do Material. O rótulo que sobe por cima da
    // borda precisa de 56dp de altura, e o campo deste app tem 28.
    // O [modifier] do chamador desce até o campo, não fica na coluna: ele traz a
    // `testTag`, e a ação de digitar mora no campo — tag na coluna deixa
    // `performTextInput` sem o `RequestFocus` que ele exige.
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
        ) {
            AppTextField(
                value = fieldValue.text,
                onValueChange = { text -> fieldValue = fieldValue.copy(text = text) },
                placeholder = placeholder,
                visualTransformation = visualTransformation,
                modifier = modifier
                    .weight(1f)
                    .onFocusChanged { focusState ->
                        if (!focusState.isFocused) {
                            commit(fieldValue.text, revertWhenInvalid = true)
                        }
                    }
            )
            if (trailingIcon != null) {
                trailingIcon()
            }
        }
        val message = errorMessage
        if (message != null) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}
