package com.usagemonitor.presentation.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Presets de aparência disponíveis na aplicação.
 *
 * Cada preset altera as superfícies e o acento estrutural da interface. As
 * cores semânticas de estado e as identidades das integrações continuam em
 * [AppAccents], porque a cor delas comunica significado, não decoração.
 */
enum class AppThemePreset(
    val isDark: Boolean,
    val labelPt: String,
    val labelEn: String,
    val background: Color,
    val surface: Color,
    val raised: Color,
    val border: Color,
    val foreground: Color,
    val muted: Color,
    val primary: Color
) {
    OBSIDIANA_DARK(
        true, "Obsidiana", "Obsidian",
        Color(0xFF131010), Color(0xFF1B1818), Color(0xFF211E1E),
        Color(0xFF3D3838), Color(0xFFF2EDED), Color(0xFFB8B2B2), Color(0xFF4C8DFF)
    ),
    GRAFITE_DARK(
        true, "Grafite", "Graphite",
        Color(0xFF111315), Color(0xFF1A1D20), Color(0xFF22262A),
        Color(0xFF3A4046), Color(0xFFF1F3F5), Color(0xFFB5BCC4), Color(0xFF7DB3FF)
    ),
    MEIA_NOITE_DARK(
        true, "Meia-noite", "Midnight",
        Color(0xFF0E1420), Color(0xFF151D2B), Color(0xFF1D2738),
        Color(0xFF344154), Color(0xFFEEF5FF), Color(0xFFB2C0D0), Color(0xFF6FB5FF)
    ),
    FLORESTA_DARK(
        true, "Floresta", "Forest",
        Color(0xFF0F1713), Color(0xFF16231B), Color(0xFF1E2C22),
        Color(0xFF385044), Color(0xFFEFF8F1), Color(0xFFB1C3B5), Color(0xFF68D391)
    ),
    OCEANO_DARK(
        true, "Oceano", "Ocean",
        Color(0xFF0C171A), Color(0xFF132429), Color(0xFF1B3037),
        Color(0xFF35535B), Color(0xFFEAF7F8), Color(0xFFACBEC2), Color(0xFF59D0D9)
    ),
    AMEIXA_DARK(
        true, "Ameixa", "Plum",
        Color(0xFF160F19), Color(0xFF201622), Color(0xFF2A1E2E),
        Color(0xFF49394F), Color(0xFFFAF0FC), Color(0xFFC4B5C8), Color(0xFFD7A4FF)
    ),
    BRASA_DARK(
        true, "Brasa", "Ember",
        Color(0xFF1A110E), Color(0xFF241915), Color(0xFF2F211B),
        Color(0xFF584238), Color(0xFFFFF2EC), Color(0xFFCBB8AE), Color(0xFFFF9B77)
    ),
    COBALTO_DARK(
        true, "Cobalto", "Cobalt",
        Color(0xFF0F121C), Color(0xFF171C2B), Color(0xFF20263A),
        Color(0xFF3B4663), Color(0xFFEEF2FF), Color(0xFFB0BAD2), Color(0xFF91A7FF)
    ),
    AMBAR_DARK(
        true, "Âmbar", "Amber",
        Color(0xFF1A140D), Color(0xFF241C12), Color(0xFF2E2417),
        Color(0xFF56472A), Color(0xFFF5EDDE), Color(0xFFC9B999), Color(0xFFE0A72E)
    ),
    RUBI_DARK(
        true, "Rubi", "Ruby",
        Color(0xFF1A1113), Color(0xFF241416), Color(0xFF301B1E),
        Color(0xFF573239), Color(0xFFF8ECED), Color(0xFFCBAAAE), Color(0xFFE8657A)
    ),
    JADE_DARK(
        true, "Jade", "Jade",
        Color(0xFF0E1C18), Color(0xFF132420), Color(0xFF1A2F29),
        Color(0xFF33544A), Color(0xFFE9F7F2), Color(0xFFA9CBC0), Color(0xFF3FCB9B)
    ),
    ROSA_DARK(
        true, "Rosa", "Rose",
        Color(0xFF190F19), Color(0xFF211621), Color(0xFF2B1E2C),
        Color(0xFF4F3A4F), Color(0xFFFAEEF6), Color(0xFFCBADC4), Color(0xFFE784C9)
    ),
    OLIVA_DARK(
        true, "Oliva", "Olive",
        Color(0xFF151A0D), Color(0xFF1C2013), Color(0xFF242A18),
        Color(0xFF434C2A), Color(0xFFF2F5E5), Color(0xFFC3CBA4), Color(0xFFB8D24A)
    ),

    PORCELANA_LIGHT(
        false, "Porcelana", "Porcelain",
        Color(0xFFF6F3F3), Color(0xFFFFFCFC), Color(0xFFEFEAEA),
        Color(0xFFD7D0D0), Color(0xFF171414), Color(0xFF686060), Color(0xFF1565C0)
    ),
    PAPEL_LIGHT(
        false, "Papel", "Paper",
        Color(0xFFF5F6F8), Color(0xFFFFFFFF), Color(0xFFECEFF2),
        Color(0xFFD3D8DE), Color(0xFF17191C), Color(0xFF626A73), Color(0xFF2459A6)
    ),
    NEVOA_LIGHT(
        false, "Névoa", "Mist",
        Color(0xFFF2F6FA), Color(0xFFFBFDFF), Color(0xFFE7EEF5),
        Color(0xFFC9D7E4), Color(0xFF14202B), Color(0xFF586A7B), Color(0xFF1D5F9F)
    ),
    SALVIA_LIGHT(
        false, "Sálvia", "Sage",
        Color(0xFFF3F7F3), Color(0xFFFCFFFC), Color(0xFFE6F0E7),
        Color(0xFFC8D8CB), Color(0xFF152019), Color(0xFF5C6E61), Color(0xFF236B3A)
    ),
    GELO_LIGHT(
        false, "Gelo", "Ice",
        Color(0xFFF1F8F9), Color(0xFFFBFFFF), Color(0xFFE3F1F2),
        Color(0xFFC5DDE0), Color(0xFF102023), Color(0xFF587075), Color(0xFF006B73)
    ),
    LAVANDA_LIGHT(
        false, "Lavanda", "Lavender",
        Color(0xFFF7F4FA), Color(0xFFFEFBFF), Color(0xFFEEE8F5),
        Color(0xFFD7CDE1), Color(0xFF21182A), Color(0xFF6B5C74), Color(0xFF6B3FA0)
    ),
    PESSEGO_LIGHT(
        false, "Pêssego", "Peach",
        Color(0xFFFFF6F1), Color(0xFFFFFCFA), Color(0xFFF6E9E0),
        Color(0xFFE3CEC1), Color(0xFF281813), Color(0xFF756157), Color(0xFF9D472A)
    ),
    AREIA_LIGHT(
        false, "Areia", "Sand",
        Color(0xFFFAF7EF), Color(0xFFFFFDF8), Color(0xFFF1E9D6),
        Color(0xFFDED2B8), Color(0xFF252117), Color(0xFF756B57), Color(0xFF7B5A00)
    ),
    AMBAR_LIGHT(
        false, "Âmbar", "Amber",
        Color(0xFFFFF8EC), Color(0xFFFFFCF5), Color(0xFFF5E9D0),
        Color(0xFFE0CFA6), Color(0xFF241C12), Color(0xFF78684A), Color(0xFF8A5A00)
    ),
    RUBI_LIGHT(
        false, "Rubi", "Ruby",
        Color(0xFFFFF2F2), Color(0xFFFFF7F7), Color(0xFFF7E2E3),
        Color(0xFFE4BEC1), Color(0xFF2A1416), Color(0xFF7A5457), Color(0xFFB3273F)
    ),
    JADE_LIGHT(
        false, "Jade", "Jade",
        Color(0xFFEEFBF5), Color(0xFFF5FFFB), Color(0xFFDDF2E9),
        Color(0xFFB9DED0), Color(0xFF0F241D), Color(0xFF4F7568), Color(0xFF00795A)
    ),
    ROSA_LIGHT(
        false, "Rosa", "Rose",
        Color(0xFFFFF0F8), Color(0xFFFFF6FB), Color(0xFFF7DEEF),
        Color(0xFFE3BBD8), Color(0xFF291423), Color(0xFF7A566E), Color(0xFFA02A72)
    ),
    OLIVA_LIGHT(
        false, "Oliva", "Olive",
        Color(0xFFF7FAEC), Color(0xFFFBFDF2), Color(0xFFEEF0DA),
        Color(0xFFD4D8B4), Color(0xFF20250F), Color(0xFF5C6640), Color(0xFF5C6F00)
    );

    val storageName: String
        get() = name

    companion object {
        val dark: List<AppThemePreset> = entries.filter { it.isDark }
        val light: List<AppThemePreset> = entries.filterNot { it.isDark }

        fun fromStorage(value: String?): AppThemePreset? {
            return value?.let { stored -> entries.firstOrNull { it.name == stored } }
        }

        fun fromLegacyMode(isDark: Boolean): AppThemePreset {
            return if (isDark) OBSIDIANA_DARK else PORCELANA_LIGHT
        }
    }
}
