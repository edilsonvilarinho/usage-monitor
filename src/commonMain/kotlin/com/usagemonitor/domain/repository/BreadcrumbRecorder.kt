package com.usagemonitor.domain.repository

import com.usagemonitor.domain.entity.Breadcrumb
import com.usagemonitor.domain.entity.BreadcrumbCategory

/**
 * A trilha de eventos: quem escreve os passos e quem os lê de volta.
 *
 * **Escrita e leitura no mesmo contrato**, e não duas interfaces. A trilha é um
 * arquivo só, com um formato só; separar o leitor do escritor daria dois donos
 * do mesmo formato, e o dia em que um deles mudasse o outro leria lixo em
 * silêncio.
 *
 * [record] **nunca lança**. Ele é chamado de dentro de `catch`, de handler de
 * exceção não tratada e do meio de laços de background: uma falha de I/O ao
 * anotar o passo não pode virar a segunda falha do dia, e muito menos a que
 * derruba o app enquanto ele tenta explicar a primeira.
 */
interface BreadcrumbRecorder {

    /**
     * Anota um passo. A mensagem é curta e **não carrega segredo**: nada de
     * prompt, resposta de IA, corpo de resposta HTTP ou credencial.
     */
    fun record(category: BreadcrumbCategory, message: String)

    /**
     * Os [limit] passos mais recentes, do mais antigo para o mais novo.
     *
     * A ordem é a de leitura de uma trilha — o relatório conta uma história, e
     * história começa no começo. Falha de leitura devolve lista vazia: relatório
     * sem trilha ainda é relatório, relatório que não abre não é nada.
     */
    fun read(limit: Int): List<Breadcrumb>
}

/**
 * Recorder que não grava nem lê nada.
 *
 * Existe para composições sem disco — teste de componente, gerador de captura,
 * qualquer caminho em que anotar um passo não teria destino. Mesmo papel do
 * `UnsupportedAppUpdateReleaseOpener`: a dependência continua obrigatória e
 * explícita, e ninguém precisa aceitar um parâmetro anulável para dispensá-la.
 */
object NoOpBreadcrumbRecorder : BreadcrumbRecorder {
    override fun record(category: BreadcrumbCategory, message: String) = Unit

    override fun read(limit: Int): List<Breadcrumb> = emptyList()
}
