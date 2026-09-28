package com.usagemonitor.presentation.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.domain.entity.AppUpdatePlatform
import com.usagemonitor.domain.entity.AppUpdateReceipt
import com.usagemonitor.domain.entity.AppUpdateReceiptStatus
import com.usagemonitor.domain.repository.AppUpdateSupport
import com.usagemonitor.domain.repository.UPDATE_FEED_URL_ENV_VAR

@Composable
fun AutoStartToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Inicialização com Sistema" else "System Startup",
        description = if (isPt) {
            "Registra a aplicação na inicialização do usuário atual."
        } else {
            "Registers the app to launch with the current user session."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(checked = enabled, onCheckedChange = { onToggle(it) })
    }
}

@Composable
fun AlwaysOnTopToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Manter sempre visível" else "Always on top",
        description = if (isPt) {
            "Mantém a janela acima das demais."
        } else {
            "Keeps the window above the others."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(checked = enabled, onCheckedChange = { onToggle(it) })
    }
}

/**
 * Modo somente cards: esconde a barra de título e o rodapé da janela.
 *
 * O texto de apoio não é decoração. Ligado, o modo tira da tela o botão de
 * fechar e a engrenagem das configurações, e quem não souber como voltar fica
 * com um app que não consegue desligar — as três saídas têm de estar escritas
 * onde o interruptor é acionado.
 */
@Composable
fun CardsOnlyModeToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Somente os cards" else "Cards only",
        description = if (isPt) {
            "Esconde a barra de título e o rodapé. Para voltar: Ctrl+Shift+M, o ícone na bandeja ou a faixa que aparece ao passar o mouse no topo da janela."
        } else {
            "Hides the title bar and the footer. To return: Ctrl+Shift+M, the tray icon, or the strip that appears when hovering the top of the window."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) },
            modifier = Modifier.testTag(CARDS_ONLY_MODE_SWITCH_TEST_TAG)
        )
    }
}

/**
 * Barra HUD (issue #164): terceiro chrome, ainda mais discreto que o modo
 * somente cards — uma faixa de 24dp ancorada no topo da tela, sem título, sem
 * cards. Mesma razão de existir do texto de apoio do modo somente cards: as
 * saídas têm de estar escritas onde o interruptor liga.
 */
@Composable
fun HudModeToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Barra HUD" else "HUD strip",
        description = if (isPt) {
            "Troca a janela por um notch colado numa borda da tela, com um anel e a palavra do estado por conta; o ponteiro em cima abre cada cota. Para voltar: clique no notch, Ctrl+Shift+H ou o ícone na bandeja."
        } else {
            "Replaces the window with a notch docked to a screen edge, with a ring and the status word per account; hovering opens every quota. To return: click the notch, Ctrl+Shift+H, or the tray icon."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) },
            modifier = Modifier.testTag(HUD_MODE_SWITCH_TEST_TAG)
        )
    }
}

/**
 * "Reduzir animações": para quem se incomoda com movimento, e para máquina lenta
 * em que a transição vira tranco. O texto diz o que some — as transições **e** o
 * que gira ou pulsa —, porque as duas coisas desligam juntas.
 */
@Composable
fun ReducedMotionToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Reduzir animações" else "Reduce motion",
        description = if (isPt) {
            "Troca telas, barras e menus de uma vez, sem transição, e desliga o que gira ou pulsa para indicar sessão ativa."
        } else {
            "Switches screens, bars and menus at once, without transitions, and turns off what spins or pulses to show an active session."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) },
            modifier = Modifier.testTag(REDUCED_MOTION_SWITCH_TEST_TAG)
        )
    }
}

/**
 * Anel de uso no ícone da bandeja (issue #328): o maior percentual entre as cotas
 * vigentes, lido sem abrir a janela. O texto diz de qual cota é o número, porque
 * o anel não diz — quem quer saber a fonte passa o ponteiro no ícone.
 */
@Composable
fun TrayUsageRingToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Anel de uso na bandeja" else "Usage ring in the tray",
        description = if (isPt) {
            "Desenha em volta do ícone da bandeja o maior percentual entre as cotas vigentes. A dica do ícone continua listando cada conta."
        } else {
            "Draws the highest percentage among the current quotas around the tray icon. The icon tooltip still lists every account."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) },
            modifier = Modifier.testTag(TRAY_USAGE_RING_SWITCH_TEST_TAG)
        )
    }
}

/**
 * Atualização automática: baixar a versão nova em segundo plano e aplicá-la ao
 * fechar o app.
 *
 * O texto de apoio diz o tamanho e o momento porque **os dois surpreendem**: são
 * ~120 MB por versão, sem atualização delta, e a troca dos arquivos acontece sem
 * nenhuma confirmação no instante em que o usuário fecha a janela. Interruptor
 * que não avisa disso liga uma coisa que o usuário não escolheu.
 *
 * Desabilitado, ele carrega **o motivo**. Um controle cinza sem explicação é pior
 * que controle nenhum: o usuário não descobre se é limitação da plataforma, da
 * instalação, ou defeito.
 */
@Composable
fun AutoUpdateToggle(
    enabled: Boolean,
    support: AppUpdateSupport,
    /** Ver [autoUpdateHint]: `null` é plataforma não reconhecida. */
    platform: AppUpdatePlatform? = null,
    language: AppLanguage = AppLanguage.PT,
    lastReceipt: AppUpdateReceipt? = null,
    /**
     * Valor de `USAGE_MONITOR_UPDATE_FEED_URL`, quando definida. Nunca nulo em
     * ambiente de teste e sempre nulo em produção — o aviso na tela é o que
     * impede alguém de rodar com o feed trocado sem perceber.
     */
    feedUrlOverride: String? = null,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val isPt = language == AppLanguage.PT
    val isSupported = support == AppUpdateSupport.SUPPORTED
    val label = if (isPt) "Atualização automática" else "Automatic updates"

    AppDataRow(modifier = modifier, showDivider = true) {
        Column(
            modifier = Modifier
                .weight(1f)
                .testTag(AUTO_UPDATE_TEXT_BLOCK_TEST_TAG)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = autoUpdateHint(support = support, isPt = isPt, platform = platform),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (lastReceipt != null) {
                Text(
                    text = lastUpdateReceiptLine(receipt = lastReceipt, isPt = isPt),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag(AUTO_UPDATE_RECEIPT_TEST_TAG)
                )
            }
            if (!feedUrlOverride.isNullOrBlank()) {
                // Tom de aviso porque é isso que ele é: com o feed trocado, o
                // SHA-256 que barra artefato adulterado passa a vir de outro lugar.
                Text(
                    text = if (isPt) {
                        "Aviso: o feed de releases está sobrescrito por $UPDATE_FEED_URL_ENV_VAR ($feedUrlOverride). Só para teste."
                    } else {
                        "Warning: the release feed is overridden by $UPDATE_FEED_URL_ENV_VAR ($feedUrlOverride). Testing only."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = AppTone.WARNING.color(),
                    modifier = Modifier.testTag(AUTO_UPDATE_FEED_OVERRIDE_TEST_TAG)
                )
            }
        }
        AppSwitch(
            // Sem suporte o interruptor mostra desligado, e não o que está
            // guardado: ligado-mas-inerte seria uma promessa falsa.
            checked = enabled && isSupported,
            onCheckedChange = { onToggle(it) },
            enabled = isSupported,
            modifier = Modifier.testTag(AUTO_UPDATE_SWITCH_TEST_TAG)
        )
    }
}

/**
 * Canal beta (issue #355): receber também as versões marcadas como beta, para
 * testar mudanças antes da release estável.
 *
 * Independe da atualização automática: sem ela a beta só é anunciada, como
 * qualquer versão. O texto diz as duas coisas que o usuário precisa saber antes
 * de ligar — que beta pode ter defeitos, e que desligar **não volta** para a
 * estável anterior.
 */
@Composable
fun BetaUpdatesToggle(
    enabled: Boolean,
    language: AppLanguage = AppLanguage.PT,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    showDivider: Boolean = true
) {
    val isPt = language == AppLanguage.PT
    SettingsOptionRow(
        label = if (isPt) "Receber versões beta" else "Receive beta updates",
        description = if (isPt) {
            "Oferece também as versões beta, que chegam antes da estável e podem ter defeitos. Desligar não volta para a versão anterior: o app fica na beta até sair uma estável mais nova."
        } else {
            "Also offers beta versions, which arrive before the stable release and may have bugs. Turning it off does not roll back: the app stays on the beta until a newer stable release comes out."
        },
        showDivider = showDivider,
        modifier = modifier
    ) {
        AppSwitch(
            checked = enabled,
            onCheckedChange = { onToggle(it) },
            modifier = Modifier.testTag(BETA_UPDATES_SWITCH_TEST_TAG)
        )
    }
}

/**
 * O motivo que acompanha o interruptor, por plataforma.
 *
 * A [platform] entrou porque dois dos motivos **mudam de conteúdo** conforme o
 * sistema: `UNSUPPORTED_PLATFORM` significava "não é Windows" e passou a
 * significar "é macOS ou algo que não reconhecemos", e
 * `UNSUPPORTED_INSTALL_ORIGIN` fala de MSI no Windows e de `.deb`/`.rpm` no
 * Linux. Sem ela, o texto continuaria afirmando no Linux coisas que deixaram de
 * ser verdade.
 *
 * `null` é plataforma **não reconhecida** — e não um default de conveniência:
 * quem não sabe onde está não pode nomear o instalador certo, e o texto genérico
 * é o que sobra de honesto.
 */
internal fun autoUpdateHint(
    support: AppUpdateSupport,
    isPt: Boolean,
    platform: AppUpdatePlatform? = null
): String {
    return when (support) {
        AppUpdateSupport.SUPPORTED -> if (isPt) {
            "Baixa a versão nova em segundo plano (~120 MB) e a aplica ao fechar o app."
        } else {
            "Downloads the new version in the background (~120 MB) and applies it on exit."
        }

        AppUpdateSupport.UNSUPPORTED_PLATFORM -> when (platform) {
            AppUpdatePlatform.MACOS -> if (isPt) {
                "Não disponível no macOS: o pacote não é assinado e o Gatekeeper exige liberação manual."
            } else {
                "Not available on macOS: the package is unsigned and Gatekeeper requires manual approval."
            }

            else -> if (isPt) {
                "Não disponível nesta plataforma. A atualização automática cobre Windows e Linux em user-space."
            } else {
                "Not available on this platform. Automatic updates cover Windows and user-space Linux."
            }
        }

        AppUpdateSupport.UNSUPPORTED_INSTALL_ORIGIN -> when (platform) {
            AppUpdatePlatform.LINUX -> if (isPt) {
                "Disponível apenas na instalação em user-space feita pelo instalador .sh. Esta cópia veio de um pacote .deb/.rpm ou de fora dele, e atualizá-la por aqui mexeria em arquivos do gerenciador de pacotes."
            } else {
                "Only available for the user-space install made by the .sh installer. This copy came from a .deb/.rpm package or from outside it, and updating it here would touch files owned by the package manager."
            }

            AppUpdatePlatform.WINDOWS -> if (isPt) {
                "Disponível apenas na instalação feita pelo instalador .exe. Esta cópia veio do MSI ou de fora dele, e atualizá-la por aqui criaria uma segunda instalação."
            } else {
                "Only available for installs made by the .exe installer. This copy came from the MSI or from outside it, and updating it here would create a second install."
            }

            else -> if (isPt) {
                "Disponível apenas nas instalações feitas pelo instalador oficial do aplicativo."
            } else {
                "Only available for installs made by the app's official installer."
            }
        }

        // O texto não nomeia a arquitetura desta máquina: quem a lê é
        // `os.arch`, e o valor bruto ("aarch64") não diz nada a quem instalou.
        AppUpdateSupport.UNSUPPORTED_ARCHITECTURE -> if (isPt) {
            "Não há pacote publicado para a arquitetura desta máquina. A atualização automática cobre apenas x86_64."
        } else {
            "No package is published for this machine's architecture. Automatic updates cover x86_64 only."
        }

        AppUpdateSupport.UNAVAILABLE -> if (isPt) {
            "Esta versão do aplicativo ainda não traz a atualização automática."
        } else {
            "This build does not ship automatic updates yet."
        }
    }
}

internal fun lastUpdateReceiptLine(receipt: AppUpdateReceipt, isPt: Boolean): String {
    val from = receipt.previousVersion?.let { previous -> "$previous → " }.orEmpty()
    return when (receipt.status) {
        AppUpdateReceiptStatus.SUCCESS -> if (isPt) {
            "Última atualização: $from${receipt.version}, concluída."
        } else {
            "Last update: $from${receipt.version}, completed."
        }

        AppUpdateReceiptStatus.FAILED -> {
            val reason = receipt.reason?.let { value -> " ($value)" }.orEmpty()
            if (isPt) {
                "Última atualização: $from${receipt.version} falhou$reason. A versão instalada não foi alterada."
            } else {
                "Last update: $from${receipt.version} failed$reason. The installed version was left untouched."
            }
        }
    }
}
