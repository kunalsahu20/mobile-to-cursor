package com.mobiletocursor.network

/**
 * Event protocol — mirrors the desktop receiver's protocol.py.
 *
 * Wire format: newline-terminated JSON strings over a persistent TCP socket.
 * We use org.json (bundled with Android) to avoid extra dependencies.
 */
object EventProtocol {

    // ── Event type constants ────────────────────
    const val TYPE_KEY_INPUT = "KEY_INPUT"
    const val TYPE_KEY_PRESS = "KEY_PRESS"
    const val TYPE_MOUSE_MOVE = "MOUSE_MOVE"
    const val TYPE_MOUSE_CLICK = "MOUSE_CLICK"
    const val TYPE_MOUSE_SCROLL = "MOUSE_SCROLL"
    const val TYPE_HEARTBEAT = "HEARTBEAT"
    const val TYPE_AUTH = "AUTH"
    const val TYPE_ZOOM = "ZOOM"

    /** PIN authentication — must be the first message after connecting. */
    fun auth(pin: String): String =
        """{"type":"$TYPE_AUTH","pin":"$pin"}""" + "\n"

    /** Pinch-to-zoom. Positive delta = zoom in, negative = zoom out. */
    fun zoom(delta: Int): String =
        """{"type":"$TYPE_ZOOM","delta":$delta}""" + "\n"

    /** Text insertion: types characters as-is on the laptop. */
    fun keyInput(text: String): String =
        """{"type":"$TYPE_KEY_INPUT","text":${jsonEscape(text)}}""" + "\n"

    /** Special key press/release (backspace, enter, arrows, etc). */
    fun keyPress(key: String, action: String = "tap"): String =
        """{"type":"$TYPE_KEY_PRESS","key":"$key","action":"$action"}""" + "\n"

    /** Cursor movement by delta pixels. */
    fun mouseMove(dx: Int, dy: Int): String =
        """{"type":"$TYPE_MOUSE_MOVE","dx":$dx,"dy":$dy}""" + "\n"

    /** Mouse button press/release/tap. */
    fun mouseClick(button: String = "left", action: String = "tap"): String =
        """{"type":"$TYPE_MOUSE_CLICK","button":"$button","action":"$action"}""" + "\n"

    /** Scroll wheel — positive dy = scroll up. */
    fun mouseScroll(dx: Int = 0, dy: Int): String =
        """{"type":"$TYPE_MOUSE_SCROLL","dx":$dx,"dy":$dy}""" + "\n"

    /** Keep-alive heartbeat. */
    fun heartbeat(): String =
        """{"type":"$TYPE_HEARTBEAT"}""" + "\n"

    /**
     * Minimal JSON string escaping — handles quotes, backslash, and newlines.
     * We avoid pulling in Gson/Moshi just for this.
     */
    private fun jsonEscape(value: String): String {
        val sb = StringBuilder("\"")
        for (ch in value) {
            when (ch) {
                '"' -> sb.append("\\\"")
                '\\' -> sb.append("\\\\")
                '\n' -> sb.append("\\n")
                '\r' -> sb.append("\\r")
                '\t' -> sb.append("\\t")
                else -> sb.append(ch)
            }
        }
        sb.append("\"")
        return sb.toString()
    }
}
