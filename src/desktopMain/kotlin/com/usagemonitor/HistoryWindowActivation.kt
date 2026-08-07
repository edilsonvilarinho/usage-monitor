package com.usagemonitor

internal interface WindowActivationTarget {
    var isVisible: Boolean
    fun toFront()
    fun requestFocus()
    fun setBounds(x: Int, y: Int, width: Int, height: Int)
}

internal fun activateWindow(window: java.awt.Window) {
    activateWindow(
        object : WindowActivationTarget {
            override var isVisible: Boolean
                get() = window.isVisible
                set(value) {
                    window.isVisible = value
                }

            override fun toFront() {
                window.toFront()
            }

            override fun requestFocus() {
                window.requestFocus()
            }

            override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
                window.setBounds(x, y, width, height)
            }
        }
    )
}

internal fun activateWindow(window: WindowActivationTarget) {
    window.isVisible = true
    window.toFront()
    window.requestFocus()
}
