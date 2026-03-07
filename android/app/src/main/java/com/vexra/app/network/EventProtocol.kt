package com.vexra.app.network

import org.json.JSONObject

/**
 * Event protocol — mirrors the desktop receiver's protocol.py.
 *
 * Wire format: newline-terminated JSON strings over a persistent TCP socket.
 * Uses org.json.JSONObject (bundled with Android) for safe JSON construction,
 * handling unicode, control characters, and special escapes automatically.
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
        JSONObject().apply {
            put("type", TYPE_AUTH)
            put("pin", pin)
        }.toString() + "\n"

    /** Pinch-to-zoom. Positive delta = zoom in, negative = zoom out. */
    fun zoom(delta: Int): String =
        JSONObject().apply {
            put("type", TYPE_ZOOM)
            put("delta", delta)
        }.toString() + "\n"

    /** Text insertion: types characters as-is on the laptop. */
    fun keyInput(text: String): String =
        JSONObject().apply {
            put("type", TYPE_KEY_INPUT)
            put("text", text)
        }.toString() + "\n"

    /** Special key press/release (backspace, enter, arrows, etc). */
    fun keyPress(key: String, action: String = "tap"): String =
        JSONObject().apply {
            put("type", TYPE_KEY_PRESS)
            put("key", key)
            put("action", action)
        }.toString() + "\n"

    /** Cursor movement by delta pixels. */
    fun mouseMove(dx: Int, dy: Int): String =
        JSONObject().apply {
            put("type", TYPE_MOUSE_MOVE)
            put("dx", dx)
            put("dy", dy)
        }.toString() + "\n"

    /** Mouse button press/release/tap. */
    fun mouseClick(button: String = "left", action: String = "tap"): String =
        JSONObject().apply {
            put("type", TYPE_MOUSE_CLICK)
            put("button", button)
            put("action", action)
        }.toString() + "\n"

    /** Scroll wheel — positive dy = scroll up. */
    fun mouseScroll(dx: Int = 0, dy: Int): String =
        JSONObject().apply {
            put("type", TYPE_MOUSE_SCROLL)
            put("dx", dx)
            put("dy", dy)
        }.toString() + "\n"

    /** Keep-alive heartbeat. */
    fun heartbeat(): String =
        JSONObject().apply {
            put("type", TYPE_HEARTBEAT)
        }.toString() + "\n"
}
