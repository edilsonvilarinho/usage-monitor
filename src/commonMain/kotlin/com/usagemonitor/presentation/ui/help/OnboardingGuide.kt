package com.usagemonitor.presentation.ui.help

import com.usagemonitor.presentation.ui.CommonLabels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppModalRevealScope
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.appModalRevealRow
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val ONBOARDING_GUIDE_TAG = "onboardingGuide"
const val ONBOARDING_NEXT_TAG = "onboardingNext"
const val ONBOARDING_BACK_TAG = "onboardingBack"
const val ONBOARDING_SKIP_TAG = "onboardingSkip"

/**
 * Um cartão do guia de introdução (#411, direção AC4 — carrossel ilustrado).
 *
 * Cada cartão aponta para um tópico da Ajuda: a ilustração é a **demo real**
 * daquele tópico (`help/<mediaId>.gif`, gravada das telas do app) e o "como
 * ativar" completo continua lá. O texto do cartão é curto de propósito — o guia
 * diz que a função existe e onde liga, e a Ajuda explica.
 */
data class OnboardingSlide(
    val topic: HelpTopic,
    val titlePt: String,
    val textPt: String,
    val titleEn: String,
    val textEn: String
) {
    fun title(language: AppLanguage): String = if (language == AppLanguage.PT) titlePt else titleEn
    fun text(language: AppLanguage): String = if (language == AppLanguage.PT) textPt else textEn
}

/**
 * Os cartões, na ordem em que o app é usado: ler a barra, ligar o que mede, ser
 * avisado, levar a leitura para longe do PC e entender o ritmo. Os rótulos entre
 * aspas são os do código, como na Ajuda.
 */
object OnboardingGuideCatalog {
    val slides: List<OnboardingSlide> = listOf(
        OnboardingSlide(
            topic = HelpTopic.WINDOW_MODES,
            titlePt = "Um anel por conta",
            textPt = "Na barra HUD, o arco de fora é a janela semanal e o de dentro a de 5h; cor e " +
                "palavra dizem o risco. O ponteiro sobre o anel abre o balão com as cotas e a hora do " +
                "reinício, e um clique atualiza só aquela conta.",
            titleEn = "One ring per account",
            textEn = "On the HUD bar, the outer arc is the weekly window and the inner one the 5h " +
                "window; colour and word state the risk. Hovering the ring opens the balloon with the " +
                "quotas and the reset time, and a click refreshes only that account."
        ),
        OnboardingSlide(
            topic = HelpTopic.DASHBOARD,
            titlePt = "Integrações",
            textPt = "Anthropic e Codex entram pela credencial que o próprio CLI já gravou. As demais " +
                "ligam na engrenagem da ponta do notch → \"Configurações\" → aba \"APIs\", e \"Testar " +
                "chave\" confirma a coleta.",
            titleEn = "Integrations",
            textEn = "Anthropic and Codex come in through the credential the CLI already wrote. The " +
                "others are turned on from the gear at the end of the notch → \"Settings\" → \"APIs\" " +
                "tab, and \"Test key\" confirms the collection."
        ),
        OnboardingSlide(
            topic = HelpTopic.ALERTS,
            titlePt = "Avisos antes do limite",
            textPt = "Na aba \"Alertas\", a bandeja avisa quando uma cota cruza um limiar ou uma " +
                "sessão CLI satura. \"Silenciar num período do dia\" adia os avisos da noite — nenhum " +
                "se perde.",
            titleEn = "Warnings before the limit",
            textEn = "In the \"Alerts\" tab, the tray warns when a quota crosses a threshold or a CLI " +
                "session saturates. \"Mute during a time range\" postpones the night's warnings — " +
                "none is lost."
        ),
        OnboardingSlide(
            topic = HelpTopic.TELEGRAM_BOT,
            titlePt = "Avisos no celular",
            textPt = "Ainda em \"Alertas\", o \"Bot do Telegram\" repassa os avisos a um bot próprio e " +
                "responde /status, /quando e /analise. Só conversas pareadas são atendidas.",
            titleEn = "Warnings on the phone",
            textEn = "Still under \"Alerts\", the \"Telegram bot\" forwards the warnings to a bot of " +
                "one's own and answers /status, /quando and /analise. Only paired chats are served."
        ),
        OnboardingSlide(
            topic = HelpTopic.WEB_ACCESS,
            titlePt = "A HUD no navegador",
            textPt = "Na aba \"Rede\", \"Acesso pela rede local\" mostra a mesma leitura no navegador " +
                "do celular ou de outro computador, só na rede local e com um endereço protegido por token.",
            titleEn = "The HUD in the browser",
            textEn = "In the \"Network\" tab, \"Local network access\" shows the same reading in the " +
                "browser of a phone or another computer, on the local network only and behind a " +
                "token-protected address."
        ),
        OnboardingSlide(
            topic = HelpTopic.SESSION_ANALYSIS,
            titlePt = "Entender o ritmo",
            textPt = "No balão da conta, \"Análise da sessão: ritmo, vazão e janelas\" diz se o ritmo " +
                "atual chega ao reinício. Todas as funções estão na \"Ajuda\", pela engrenagem, e este " +
                "guia volta por \"Rever guia de introdução\".",
            titleEn = "Read the pace",
            textEn = "In the account balloon, \"Session analysis: pace, throughput and windows\" states " +
                "whether the current pace reaches the reset. Every feature is in \"Help\", from the " +
                "gear, and this guide comes back through \"Replay the welcome guide\"."
        )
    )
}

/** Título da janela, na barra da moldura. */
fun onboardingGuideWindowTitle(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Guia de introdução" else "Welcome guide"
}

/** O passo escrito: a posição no guia não depende de cor nem de forma. */
fun onboardingStepLabel(index: Int, total: Int, language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Passo ${index + 1} de $total" else "Step ${index + 1} of $total"
}

/**
 * O guia de introdução: um cartão por vez, com a demo real do tópico em cima.
 *
 * Stateless como todo componente do projeto: o índice mora na janela, que é quem
 * sabe qual demo carregar. Trocar de cartão refaz a revelação E9 das modais
 * (`AppModalRevealScope`) — o mesmo gesto da Ajuda ao trocar de tópico —, e com
 * "Reduzir animações" a troca é seca.
 *
 * "Voltar" some no primeiro cartão em vez de ficar cinza (controle desabilitado
 * teria de dizer por quê, e não há o que dizer). No último, a primária vira
 * "Concluir" e "Pular guia" sai, porque não há mais o que pular.
 */
@Composable
fun OnboardingGuideContent(
    slideIndex: Int,
    language: AppLanguage,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    media: HelpMediaState = HelpMediaState.Unavailable,
    slides: List<OnboardingSlide> = OnboardingGuideCatalog.slides
) {
    val isPt = language == AppLanguage.PT
    val index = slideIndex.coerceIn(0, slides.lastIndex)
    val slide = slides[index]
    val isLast = index == slides.lastIndex

    Surface(
        modifier = modifier.fillMaxSize().testTag(ONBOARDING_GUIDE_TAG),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                // A demo cede altura antes de o texto sair da vista, como na Ajuda.
                val mediaHeight = minOf(ONBOARDING_MEDIA_HEIGHT, maxHeight * ONBOARDING_MEDIA_HEIGHT_SHARE)
                AppModalRevealScope(replayKey = index) {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        AppDataSurfaceFlush(
                            header = {
                                AppSectionHeader(
                                    title = slide.title(language),
                                    subtitle = onboardingStepLabel(index, slides.size, language),
                                    markerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            HelpMediaFrame(
                                media = media,
                                title = slide.title(language),
                                isPt = isPt,
                                height = mediaHeight,
                                modifier = Modifier.padding(AppSpacing.md)
                            )
                            Text(
                                text = slide.text(language),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.appModalRevealRow().padding(
                                    start = AppSpacing.md,
                                    end = AppSpacing.md,
                                    bottom = AppSpacing.md
                                )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLast) {
                    AppButton(
                        label = if (isPt) "Pular guia" else "Skip guide",
                        onClick = onFinish,
                        tone = AppButtonTone.GHOST,
                        modifier = Modifier.testTag(ONBOARDING_SKIP_TAG)
                    )
                }
                Spacer(modifier = Modifier.weight(1f))
                if (index > 0) {
                    AppButton(
                        label = CommonLabels.back(language),
                        onClick = onBack,
                        modifier = Modifier.testTag(ONBOARDING_BACK_TAG)
                    )
                }
                // Uma primária por tela: avançar é o que o guia propõe.
                AppButton(
                    label = if (isLast) {
                        if (isPt) "Concluir" else "Done"
                    } else {
                        if (isPt) "Próximo" else "Next"
                    },
                    onClick = if (isLast) onFinish else onNext,
                    tone = AppButtonTone.PRIMARY,
                    modifier = Modifier.testTag(ONBOARDING_NEXT_TAG)
                )
            }
        }
    }
}

/** Teto da demo: a altura da gravação, como na Ajuda (texto de 12px legível). */
private val ONBOARDING_MEDIA_HEIGHT = 420.dp

/** Fatia da área que a demo ocupa numa janela baixa; o texto do cartão fica na vista. */
private const val ONBOARDING_MEDIA_HEIGHT_SHARE = 0.68f
