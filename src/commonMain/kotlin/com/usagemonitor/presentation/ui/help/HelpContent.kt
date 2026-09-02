package com.usagemonitor.presentation.ui.help

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.Image
import androidx.compose.foundation.VerticalScrollbar
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollbarAdapter
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.usagemonitor.domain.entity.AppLanguage
import com.usagemonitor.presentation.ui.components.AppButton
import com.usagemonitor.presentation.ui.components.AppButtonTone
import com.usagemonitor.presentation.ui.components.AppDataRow
import com.usagemonitor.presentation.ui.components.AppDataSurfaceFlush
import com.usagemonitor.presentation.ui.components.AppEmptyState
import com.usagemonitor.presentation.ui.components.AppLoadingState
import com.usagemonitor.presentation.ui.components.AppSectionHeader
import com.usagemonitor.presentation.ui.components.AppSettingsNav
import com.usagemonitor.presentation.ui.components.AppTab
import com.usagemonitor.presentation.ui.components.AppVerticalDivider
import com.usagemonitor.presentation.ui.components.appSurfaceBlock
import com.usagemonitor.presentation.ui.theme.AppShapes
import com.usagemonitor.presentation.ui.theme.AppSpacing

const val HELP_CONTENT_TAG = "helpContent"

const val HELP_MEDIA_TAG = "helpMedia"

/** Marcado por tópico: o rótulo é traduzido e buscar por texto amarraria o teste ao idioma. */
fun helpTopicTestTag(topic: HelpTopic): String = "helpTopic_${topic.name}"

/**
 * Estado da demo do tópico selecionado.
 *
 * `sealed interface` e não enum mais um `ImageBitmap` anulável: o quadro só
 * existe num dos três estados, e um campo anulável ao lado de um enum permite a
 * combinação impossível "pronto, sem quadro".
 *
 * [Unavailable] não carrega texto. O motivo técnico — recurso ausente, arquivo
 * corrompido — não muda o que a pessoa faz a seguir, e a frase precisa vir
 * traduzida, o que o tocador (que vive no desktop) não tem como saber.
 */
sealed interface HelpMediaState {

    data object Loading : HelpMediaState

    data class Frame(val bitmap: ImageBitmap) : HelpMediaState

    data object Unavailable : HelpMediaState
}

/**
 * Ajuda: o que o app faz, o que cada coisa significa e como ligá-la.
 *
 * Stateless como todo componente deste projeto — inclusive o tópico escolhido,
 * que é hasteado. Não é preferência de gosto: quem carrega a demo é o tocador em
 * `desktopMain`, e ele precisa saber qual tópico está na tela. Com a escolha
 * presa aqui dentro, o tocador teria de adivinhá-la.
 *
 * A demo é **quadro pronto**, nunca um laço de animação: laço infinito trava o
 * `waitForIdle` dos testes de componente, e é por isso que o avanço de quadros
 * mora do lado do desktop.
 */
@Composable
fun HelpContent(
    selectedTopic: HelpTopic,
    onSelectTopic: (HelpTopic) -> Unit,
    language: AppLanguage,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    media: HelpMediaState = HelpMediaState.Unavailable
) {
    val isPt = language == AppLanguage.PT
    val topics = HelpCatalog.readingOrder
    val entry = HelpCatalog.entry(selectedTopic, language)

    // Um estado de rolagem por tópico, começando no topo: reaproveitar o mesmo
    // faria o tópico curto abrir rolado pela posição que o longo deixou — mesmo
    // defeito já corrigido nas abas das Configurações.
    val scrollState = remember(selectedTopic) { ScrollState(0) }

    Surface(
        modifier = modifier.fillMaxSize().testTag(HELP_CONTENT_TAG),
        color = MaterialTheme.colorScheme.background
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            // A lista de tópicos fica à esquerda e não rola: ela é o controle, e
            // o conteúdo rolando não pode tirá-la da vista.
            AppSettingsNav(
                items = topics.map { topic ->
                    AppTab(
                        label = HelpCatalog.entry(topic, language).title,
                        testTag = helpTopicTestTag(topic)
                    )
                },
                selectedIndex = topics.indexOf(selectedTopic),
                onSelect = { index -> onSelectTopic(topics[index]) },
                header = if (isPt) "Funcionalidades" else "Features",
                width = HELP_NAV_WIDTH
            )
            AppVerticalDivider()

            Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
                // A barra de rolagem mora dentro da área rolável: fora dela
                // ficaria por cima da lista de tópicos.
                BoxWithConstraints(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    // A faixa da demo cede altura antes de a seção "Como ativar"
                    // sair da vista. Medido no app: numa área útil de 1280×752
                    // com a escala em 115%, os 420dp fixos deixavam os passos
                    // abaixo da dobra — e eles são a pergunta que a tela existe
                    // para responder. A demo encolhe com `Fit` e continua
                    // inteira; a seção some.
                    val mediaHeight = minOf(HELP_MEDIA_HEIGHT, maxHeight * HELP_MEDIA_HEIGHT_SHARE)

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(scrollState)
                            .padding(AppSpacing.lg),
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        AppDataSurfaceFlush(
                            header = {
                                AppSectionHeader(
                                    title = entry.title,
                                    subtitle = entry.summary,
                                    markerColor = MaterialTheme.colorScheme.primary
                                )
                            }
                        ) {
                            HelpMediaFrame(
                                media = media,
                                title = entry.title,
                                isPt = isPt,
                                height = mediaHeight,
                                modifier = Modifier.padding(AppSpacing.md)
                            )
                            Text(
                                text = entry.description,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(
                                    start = AppSpacing.md,
                                    end = AppSpacing.md,
                                    bottom = AppSpacing.md
                                )
                            )
                        }

                        AppDataSurfaceFlush(
                            header = {
                                AppSectionHeader(title = if (isPt) "Como ativar" else "How to enable")
                            }
                        ) {
                            entry.steps.forEachIndexed { index, step ->
                                AppDataRow(showDivider = index != entry.steps.lastIndex) {
                                    // O número é rótulo, e rótulo é mono: é a
                                    // largura fixa do dígito que alinha os
                                    // textos dos passos entre si.
                                    Text(
                                        text = "${index + 1}.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.width(STEP_NUMBER_WIDTH)
                                    )
                                    Text(
                                        text = step,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                    VerticalScrollbar(
                        adapter = rememberScrollbarAdapter(scrollState),
                        modifier = Modifier.align(Alignment.CenterEnd).fillMaxHeight()
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(AppSpacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Uma primária por tela, e aqui ela é fechar: a tela informa,
                    // não propõe trabalho.
                    AppButton(
                        label = if (isPt) "Fechar" else "Close",
                        onClick = onClose,
                        tone = AppButtonTone.PRIMARY
                    )
                }
            }
        }
    }
}

/**
 * A demo do tópico, numa faixa de altura fixa.
 *
 * Altura fixa e não a proporção da gravação: com `aspectRatio` numa janela de
 * 900dp o bloco media ~410dp de altura e empurrava a seção "Como ativar" —
 * justamente o que esta tela existe para responder — inteira para fora da vista.
 * Medido: três asserts de componente falharam com "not displayed" antes desta
 * troca. A altura fixa também é o que mantém a página parada ao trocar de
 * tópico. `ContentScale.Fit` porque cortar a demo esconderia justamente o
 * controle que ela existe para mostrar; o que sobra é margem, não corte.
 *
 * Mídia ausente **não** esconde a descrição nem os passos: a demo ilustra o
 * tópico, não é o tópico.
 */
@Composable
private fun HelpMediaFrame(
    media: HelpMediaState,
    title: String,
    isPt: Boolean,
    height: Dp,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .appSurfaceBlock(shape = AppShapes.small),
        contentAlignment = Alignment.Center
    ) {
        when (media) {
            is HelpMediaState.Frame -> Image(
                bitmap = media.bitmap,
                // O texto da demo não é lido por leitor de tela; o rótulo diz o
                // que ela mostra, e é por ele que a suíte a encontra.
                contentDescription = if (isPt) {
                    "Demonstração: $title"
                } else {
                    "Demo: $title"
                },
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().testTag(HELP_MEDIA_TAG)
            )

            HelpMediaState.Loading -> AppLoadingState(
                message = if (isPt) "Carregando a demonstração." else "Loading the demo.",
                lines = 3,
                modifier = Modifier.testTag(HELP_MEDIA_TAG)
            )

            HelpMediaState.Unavailable -> AppEmptyState(
                message = if (isPt) {
                    "Demonstração indisponível."
                } else {
                    "Demo unavailable."
                },
                detail = if (isPt) {
                    "A gravação não veio nesta instalação. A descrição e os passos continuam válidos."
                } else {
                    "The recording did not ship with this install. The description and the steps still apply."
                },
                modifier = Modifier.testTag(HELP_MEDIA_TAG)
            )
        }
    }
}

/** Título da janela, na barra da moldura. */
fun helpWindowTitle(language: AppLanguage): String {
    return if (language == AppLanguage.PT) "Ajuda" else "Help"
}

/**
 * Largura do trilho de tópicos.
 *
 * Maior que o default de 150dp das Configurações: ali os rótulos são de uma
 * palavra ("Geral", "Rede"), e aqui são nomes de funcionalidade — o item mais
 * largo é "Dashboard e integrações", que não cabe em 150dp sem cortar.
 */
private val HELP_NAV_WIDTH = 200.dp

/** Largura do número do passo. Fixa para os textos alinharem entre si. */
private val STEP_NUMBER_WIDTH = 20.dp

/**
 * Teto da faixa da demo, igual à altura da gravação.
 *
 * As demos são gravadas em 1000×420 porque as telas deste app têm orçamento de
 * coluna de ~1000dp — gravá-las estreitas mostraria um layout que o app não tem.
 * Exibir 1000×420 numa faixa mais baixa reduziria a demo, e reduzir texto de
 * 12px pela metade torna ilegível justamente o rótulo que ela existe para
 * apontar. Por isso a faixa tem a altura da gravação, e a janela é larga.
 *
 * É **teto**, não altura fixa: em janela baixa ela cede lugar para a seção
 * "Como ativar", pela [HELP_MEDIA_HEIGHT_SHARE].
 */
private val HELP_MEDIA_HEIGHT = 420.dp

/**
 * Fatia máxima da área rolável que a demo ocupa numa janela baixa.
 *
 * Acima disso o cabeçalho, a descrição e o começo dos passos não cabem juntos —
 * e é a demo que encolhe, porque `ContentScale.Fit` a mantém inteira e legível
 * enquanto a seção que some não tem como se encolher.
 */
private const val HELP_MEDIA_HEIGHT_SHARE = 0.55f
