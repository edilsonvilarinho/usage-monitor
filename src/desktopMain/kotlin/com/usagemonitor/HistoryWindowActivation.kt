package com.usagemonitor

internal interface HistoryWindowActivationTarget {
    var isVisible: Boolean
    fun toFront()
    fun requestFocus()
    fun setBounds(x: Int, y: Int, width: Int, height: Int)
}

internal fun activateHistoryWindow(window: java.awt.Window) {
    activateHistoryWindow(
        object : HistoryWindowActivationTarget {
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

internal fun activateHistoryWindow(window: HistoryWindowActivationTarget) {
    window.isVisible = true
    window.toFront()
    window.requestFocus()
}
