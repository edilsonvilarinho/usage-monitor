package com.usagemonitor.presentation.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.material3.MaterialTheme

/**
 * A cor que o usuário escolhe para uma conta Claude (issue #275).
 *
 * Várias contas no mesmo PC apareciam todas no azul da Anthropic, e o único jeito
 * de separá-las era ler o apelido no título. **Paleta fixa, não seletor livre**:
 * cada cor tem uma variante para o tema escuro e outra para o claro, e
 * `AppAccentsContrastTest` mede as dezesseis — AA de 4,5:1 contra a `surface` dos
 * dois temas, a mesma matiz (±30°) entre as variantes, e 20° de distância entre
 * as cores. Uma cor livre escolhida no tema escuro não teria como passar no claro.
 *
 * **Enum novo, não valor em `AppAccents`**: aquele é a identidade do fornecedor,
 * esta é a da conta dentro do fornecedor. A cor da conta substitui o acento **só**
 * onde ele já aparece — o marcador de 2dp e a marca do fornecedor —, nunca pinta
 * superfície. `null` em quem guarda a escolha é "Padrão": o acento da fonte.
 *
 * O [name] é o que vai para o disco: renomear um valor apaga a escolha de quem o
 * tinha, e a leitura cai em "Padrão".
 */
enum class AccountAccent(
    val labelPt: String,
    val labelEn: String,
    val dark: Color,
    val light: Color
) {
    BLUE("Azul", "Blue", Color(0xFF78A4F0), Color(0xFF1361E7)),
    CYAN("Ciano", "Cyan", Color(0xFF17B4CC), Color(0xFF0B7686)),
    GREEN("Verde", "Green", Color(0xFF15BE4E), Color(0xFF0A7B30)),
    LIME("Lima", "Lime", Color(0xFF6BB814), Color(0xFF45780A)),
    AMBER("Âmbar", "Amber", Color(0xFFCA9D16), Color(0xFF84660B)),
    ORANGE("Laranja", "Orange", Color(0xFFEC8B53), Color(0xFFB34B0F)),
    ROSE("Rosa", "Rose", Color(0xFFF17EAE), Color(0xFFD01160)),
    VIOLET("Violeta", "Violet", Color(0xFFBD8FF3), Color(0xFF8C37EF));

    fun label(isPt: Boolean): String = if (isPt) labelPt else labelEn

    /**
     * A variante do tema em vigor. Decide pela luminância da `surface`, e não por
     * um `isDark` guardado: os 26 presets têm superfícies próprias, e é contra a
     * superfície que o contraste foi medido.
     */
    val current: Color
        @Composable
        @ReadOnlyComposable
        get() = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) dark else light

    companion object {
        /** A escolha lida do disco; nome desconhecido é "Padrão", nunca erro. */
        fun fromStorage(value: String?): AccountAccent? =
            value?.let { name -> entries.firstOrNull { entry -> entry.name == name } }
    }
}
