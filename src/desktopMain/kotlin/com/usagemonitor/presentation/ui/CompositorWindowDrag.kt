package com.usagemonitor.presentation.ui

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.isPrimaryPressed
import androidx.compose.ui.input.pointer.pointerInput
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.NativeLong
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.ptr.IntByReference
import com.sun.jna.ptr.NativeLongByReference
import com.sun.jna.ptr.PointerByReference

/**
 * Se o arrasto das janelas é entregue ao compositor (issue #434).
 *
 * **Só no COSMIC.** O compositor dele ignora toda posição que uma janela X11
 * gerenciada pede para si — na abertura e depois —, e o `WindowDraggableArea`
 * arrasta exatamente assim: medido no `cosmic-comp` 1.10.0 aninhado, a janela
 * fica parada enquanto o AWT acredita que ela andou. O KWin respeita o pedido
 * (medido na #426); GNOME e Pantheon não foram medidos. Liberar outro compositor
 * é decisão com medição (regra do #340).
 *
 * [desktop] é o `XDG_CURRENT_DESKTOP` verbatim, uma lista separada por `:`.
 */
internal fun windowDragByCompositor(osName: String, desktop: String?): Boolean =
    osName.lowercase().contains("linux") && isCosmicDesktop(desktop)

/** O COSMIC entre os itens do `XDG_CURRENT_DESKTOP`, sem caixa (#430, #434). */
internal fun isCosmicDesktop(desktop: String?): Boolean {
    if (desktop == null) {
        return false
    }
    return desktop.split(':').any { item -> item.trim().equals("COSMIC", ignoreCase = true) }
}

/** Lido uma vez: o ambiente do processo não muda. */
internal val dragsWindowsByCompositor: Boolean by lazy {
    windowDragByCompositor(System.getProperty("os.name").orEmpty(), System.getenv("XDG_CURRENT_DESKTOP"))
}

/**
 * A barra de título que pede ao compositor que arraste a janela, no lugar do
 * `WindowDraggableArea` (issue #434).
 *
 * O arrasto começa depois do *touch slop*, como o do Compose: clique simples
 * não vira arrasto. Os botões da barra consomem o próprio toque antes, e aqui
 * só entra o toque que ninguém consumiu. Se o pedido não sai — biblioteca
 * nativa ausente, servidor que não é X11 —, nada acontece e o gesto segue para
 * quem estiver atrás; o arrasto antigo não teria movido a janela de qualquer
 * jeito.
 */
internal fun Modifier.compositorWindowDrag(window: java.awt.Window): Modifier =
    pointerInput(window) {
        awaitEachGesture {
            val down = awaitFirstDown()
            if (!currentEvent.buttons.isPrimaryPressed) {
                return@awaitEachGesture
            }
            val slop = awaitTouchSlopOrCancellation(down.id) { change, _ -> change.consume() }
            if (slop == null) {
                return@awaitEachGesture
            }
            val request = X11CompositorMove.begin(window)
            runCatching { windowDragSink("arrasto pelo compositor: ${request.detail}") }
            if (request.sent) {
                // O compositor fica com o ponteiro até soltar; o `ButtonRelease`
                // chega no fim (medido), e o gesto fecha com ele.
                do {
                    val event = awaitPointerEvent()
                    event.changes.forEach { change -> change.consume() }
                } while (event.changes.any { change -> change.pressed })
            }
        }
    }

/**
 * `_NET_WM_MOVERESIZE` (EWMH): o cliente pede, o compositor arrasta.
 *
 * Por uma conexão X própria, ao lado da do AWT: medido no COSMIC, o pedido vale
 * vindo de outra conexão e sem soltar a captura implícita do ponteiro. A janela
 * nativa sai da JAWT (`Native.getWindowID`) e sobe até o filho da raiz, que é a
 * de nível superior que o compositor gerencia. As coordenadas vêm do próprio
 * servidor (`XQueryPointer`), em pixels do X, sem passar pela escala do AWT.
 */
internal object X11CompositorMove {
    private const val MOVE = 8
    private const val PRIMARY_BUTTON = 1
    private const val SOURCE_APPLICATION = 1
    private const val CLIENT_MESSAGE = 33
    private const val SUBSTRUCTURE_NOTIFY_MASK = 1L shl 19
    private const val SUBSTRUCTURE_REDIRECT_MASK = 1L shl 20

    @Suppress("FunctionName")
    private interface Xlib : Library {
        fun XOpenDisplay(name: String?): Pointer?
        fun XDefaultRootWindow(display: Pointer): NativeLong
        fun XInternAtom(display: Pointer, name: String, onlyIfExists: Boolean): NativeLong
        fun XQueryTree(
            display: Pointer,
            window: NativeLong,
            root: NativeLongByReference,
            parent: NativeLongByReference,
            children: PointerByReference,
            count: IntByReference
        ): Int
        fun XQueryPointer(
            display: Pointer,
            window: NativeLong,
            root: NativeLongByReference,
            child: NativeLongByReference,
            rootX: IntByReference,
            rootY: IntByReference,
            windowX: IntByReference,
            windowY: IntByReference,
            mask: IntByReference
        ): Boolean
        fun XSendEvent(display: Pointer, window: NativeLong, propagate: Boolean, mask: NativeLong, event: ClientMessage): Int
        fun XGetWindowProperty(
            display: Pointer,
            window: NativeLong,
            property: NativeLong,
            offset: NativeLong,
            length: NativeLong,
            delete: Boolean,
            requestedType: NativeLong,
            actualType: NativeLongByReference,
            actualFormat: IntByReference,
            items: NativeLongByReference,
            bytesAfter: NativeLongByReference,
            value: PointerByReference
        ): Int
        fun XFlush(display: Pointer): Int
        fun XFree(data: Pointer): Int
    }

    /** `XClientMessageEvent`, completado até o tamanho do `XEvent` (24 `long`). */
    @Structure.FieldOrder("type", "serial", "sendEvent", "display", "window", "messageType", "format", "data", "padding")
    internal class ClientMessage : Structure() {
        @JvmField var type: Int = 0
        @JvmField var serial: NativeLong = NativeLong(0)
        @JvmField var sendEvent: Int = 0
        @JvmField var display: Pointer? = null
        @JvmField var window: NativeLong = NativeLong(0)
        @JvmField var messageType: NativeLong = NativeLong(0)
        @JvmField var format: Int = 0
        @JvmField var data: Array<NativeLong> = Array(5) { NativeLong(0) }
        @JvmField var padding: Array<NativeLong> = Array(12) { NativeLong(0) }
    }

    private class Connection(
        val xlib: Xlib,
        val display: Pointer,
        val root: NativeLong,
        val moveResize: NativeLong,
        val wmState: NativeLong
    )

    /** O pedido saiu ([sent]) e o que vai para o `app.log` ([detail]). */
    internal class Request(val sent: Boolean, val detail: String)

    /** Aberta na primeira vez e mantida; o erro fica no lugar dela para o log. */
    private val connection: Result<Connection> by lazy {
        runCatching {
            val xlib = Native.load("X11", Xlib::class.java)
            val display = checkNotNull(xlib.XOpenDisplay(null)) { "XOpenDisplay sem conexão" }
            Connection(
                xlib = xlib,
                display = display,
                root = xlib.XDefaultRootWindow(display),
                moveResize = xlib.XInternAtom(display, "_NET_WM_MOVERESIZE", false),
                wmState = xlib.XInternAtom(display, "WM_STATE", false)
            )
        }
    }

    /** Pede o arrasto de [window] ao compositor. */
    @Synchronized
    fun begin(window: java.awt.Window): Request {
        val link = connection.getOrElse { error -> return failed("conexão", error) }
        return runCatching {
            val native = Native.getWindowID(window)
            val toplevel = clientWindowOf(link, NativeLong(native))
                ?: return@runCatching Request(false, "falhou: sem janela gerenciada (id $native)")
            val rootX = IntByReference()
            val rootY = IntByReference()
            val queried = link.xlib.XQueryPointer(
                link.display, link.root, NativeLongByReference(), NativeLongByReference(),
                rootX, rootY, IntByReference(), IntByReference(), IntByReference()
            )
            if (!queried) {
                return@runCatching Request(false, "falhou: XQueryPointer")
            }
            val message = ClientMessage().apply {
                type = CLIENT_MESSAGE
                sendEvent = 1
                display = link.display
                this.window = toplevel
                messageType = link.moveResize
                format = 32
                data[0] = NativeLong(rootX.value.toLong())
                data[1] = NativeLong(rootY.value.toLong())
                data[2] = NativeLong(MOVE.toLong())
                data[3] = NativeLong(PRIMARY_BUTTON.toLong())
                data[4] = NativeLong(SOURCE_APPLICATION.toLong())
            }
            val mask = NativeLong(SUBSTRUCTURE_REDIRECT_MASK or SUBSTRUCTURE_NOTIFY_MASK)
            val sent = link.xlib.XSendEvent(link.display, link.root, false, mask, message) != 0
            link.xlib.XFlush(link.display)
            val where = "janela 0x${toplevel.toLong().toString(16)} em ${rootX.value},${rootY.value}"
            Request(sent, if (sent) "pedido enviado, $where" else "falhou: XSendEvent, $where")
        }.getOrElse { error -> failed("pedido", error) }
    }

    private fun failed(stage: String, error: Throwable): Request =
        Request(false, "falhou na $stage: ${error.javaClass.simpleName} ${error.message.orEmpty()}".trim())

    /**
     * A janela que o compositor gerencia: o ancestral com `WM_STATE` (ICCCM).
     *
     * Não é o filho da raiz: o gerenciador X11 do COSMIC (Smithay) **reparenta**
     * cada janela numa moldura dele, e o pedido mandado à moldura é ignorado
     * (medido: `0x20002b` sem nome, com a janela do app `0x400039` dentro). Sem
     * `WM_STATE` no caminho, vale o filho da raiz, como num gerenciador que não
     * reparenta.
     */
    private fun clientWindowOf(link: Connection, start: NativeLong): NativeLong? =
        compositorClientWindow(start.toLong(), JnaWindowTree(link))?.let { id -> NativeLong(id) }

    /** As duas consultas da subida, pela conexão X real. */
    private class JnaWindowTree(private val link: Connection) : X11WindowTree {
        override fun parentAndRoot(window: Long): Pair<Long, Long>? {
            val root = NativeLongByReference()
            val parent = NativeLongByReference()
            val children = PointerByReference()
            if (link.xlib.XQueryTree(link.display, NativeLong(window), root, parent, children, IntByReference()) == 0) {
                return null
            }
            children.value?.let { list -> link.xlib.XFree(list) }
            return parent.value.toLong() to root.value.toLong()
        }

        override fun hasWmState(window: Long): Boolean = wmStatePresent(link, NativeLong(window))
    }

    private fun wmStatePresent(link: Connection, window: NativeLong): Boolean {
        val actualType = NativeLongByReference()
        val value = PointerByReference()
        val status = link.xlib.XGetWindowProperty(
            link.display, window, link.wmState, NativeLong(0), NativeLong(0), false, link.wmState,
            actualType, IntByReference(), NativeLongByReference(), NativeLongByReference(), value
        )
        value.value?.let { data -> link.xlib.XFree(data) }
        return status == 0 && actualType.value.toLong() != 0L
    }
}

/** As consultas que a subida até a janela cliente precisa; a implementação real fala com o X. */
internal interface X11WindowTree {
    /** `(pai, raiz)` de [window]; `null` se a consulta falhar. */
    fun parentAndRoot(window: Long): Pair<Long, Long>?

    /** Se [window] tem `WM_STATE` (ICCCM). */
    fun hasWmState(window: Long): Boolean
}

/**
 * A janela cliente que o compositor gerencia, subindo de [start] pela árvore: a
 * primeira com `WM_STATE` que estiver no caminho, ou o filho da raiz se nenhuma
 * tiver. `null` sem caminho, se uma consulta falhar ou se a árvore for mais funda
 * que [maxDepth].
 */
internal fun compositorClientWindow(start: Long, tree: X11WindowTree, maxDepth: Int = X11_MAX_TREE_DEPTH): Long? {
    if (start == 0L) {
        return null
    }
    var current = start
    var client: Long? = null
    repeat(maxDepth) {
        if (client == null && tree.hasWmState(current)) {
            client = current
        }
        val (parent, root) = tree.parentAndRoot(current) ?: return null
        if (parent == 0L || parent == root) {
            return client ?: current
        }
        current = parent
    }
    return null
}

private const val X11_MAX_TREE_DEPTH = 16
