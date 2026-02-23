"""
OS-level input injection for Windows using pynput.

Translates parsed protocol events into real keyboard/mouse actions
that the OS treats as hardware input.
"""

import logging
from pynput.keyboard import Controller as KeyboardController, Key
from pynput.mouse import Controller as MouseController, Button

from config import MOUSE_SENSITIVITY, SCROLL_SENSITIVITY
from protocol import (
    SPECIAL_KEYS,
    TYPE_KEY_INPUT,
    TYPE_KEY_PRESS,
    TYPE_MOUSE_MOVE,
    TYPE_MOUSE_CLICK,
    TYPE_MOUSE_SCROLL,
    TYPE_HEARTBEAT,
    TYPE_ZOOM,
)

logger = logging.getLogger(__name__)

# ── Singleton controllers (thread-safe for pynput) ──
_keyboard = KeyboardController()
_mouse = MouseController()

# ── Map button name strings to pynput Button enum ──
_BUTTON_MAP = {
    "left": Button.left,
    "right": Button.right,
    "middle": Button.middle,
}


def _get_pynput_key(key_name: str):
    """
    Resolve a special key name (e.g. 'backspace') to a pynput Key object.
    Returns None if unrecognized.
    """
    mapped = SPECIAL_KEYS.get(key_name)
    if mapped is None:
        logger.warning("Unknown special key: %s", key_name)
        return None
    try:
        return getattr(Key, mapped)
    except AttributeError:
        logger.warning("pynput has no Key.%s", mapped)
        return None


def dispatch(event: dict) -> None:
    """
    Dispatch a parsed event dict to the correct injection handler.
    This is the single entry point called by the receiver.
    """
    event_type = event.get("type")

    if event_type == TYPE_KEY_INPUT:
        _handle_key_input(event)
    elif event_type == TYPE_KEY_PRESS:
        _handle_key_press(event)
    elif event_type == TYPE_MOUSE_MOVE:
        _handle_mouse_move(event)
    elif event_type == TYPE_MOUSE_CLICK:
        _handle_mouse_click(event)
    elif event_type == TYPE_MOUSE_SCROLL:
        _handle_mouse_scroll(event)
    elif event_type == TYPE_ZOOM:
        _handle_zoom(event)
    elif event_type == TYPE_HEARTBEAT:
        pass  # Silently consume keep-alives
    else:
        logger.warning("Unhandled event type: %s", event_type)


# ── Handlers ─────────────────────────────────


def _handle_key_input(event: dict) -> None:
    """
    Handle text insertion. The 'text' field contains one or more
    characters that should be typed as-is.
    """
    text = event.get("text", "")
    if not text:
        return
    # type() is more efficient than individual press/release for bulk text
    _keyboard.type(text)
    logger.debug("Typed: %s", repr(text))


def _handle_key_press(event: dict) -> None:
    """
    Handle special key press/release (Backspace, Enter, arrows, etc.).
    Also handles combo keys like 'ctrl+c' — press modifier, tap key, release modifier.
    """
    key_name = event.get("key", "")
    action = event.get("action", "tap")

    # Handle combo keys like "ctrl+c", "alt+f4"
    if "+" in key_name:
        _handle_combo(key_name)
        return

    pynput_key = _get_pynput_key(key_name)
    if pynput_key is None:
        return

    if action == "down" or action == "press":
        _keyboard.press(pynput_key)
    elif action == "up" or action == "release":
        _keyboard.release(pynput_key)
    else:
        # Convenience: "tap" = press + release
        _keyboard.press(pynput_key)
        _keyboard.release(pynput_key)

    logger.debug("Key %s: %s", action, key_name)


def _handle_combo(combo: str) -> None:
    """
    Handle a keyboard combo like 'ctrl+c' or 'win+d'.
    Presses all modifiers, types the final key, then releases modifiers.
    """
    parts = combo.lower().split("+")
    if len(parts) < 2:
        return

    modifiers = parts[:-1]
    final_key = parts[-1]

    # Resolve modifier pynput keys
    mod_keys = []
    for mod in modifiers:
        pkey = _get_pynput_key(mod)
        if pkey:
            mod_keys.append(pkey)

    # Press all modifiers
    for mk in mod_keys:
        _keyboard.press(mk)

    # Type the final key — try special key first (no warning), then raw char
    mapped = SPECIAL_KEYS.get(final_key)
    if mapped:
        try:
            pynput_key = getattr(Key, mapped)
            _keyboard.press(pynput_key)
            _keyboard.release(pynput_key)
        except AttributeError:
            pass
    elif len(final_key) == 1:
        _keyboard.press(final_key)
        _keyboard.release(final_key)

    # Release all modifiers in reverse order
    for mk in reversed(mod_keys):
        _keyboard.release(mk)

    logger.info("Combo executed: %s", combo)


def _handle_mouse_move(event: dict) -> None:
    """
    Move cursor by (dx, dy) pixels, scaled by sensitivity.
    """
    dx = event.get("dx", 0)
    dy = event.get("dy", 0)

    scaled_dx = int(dx * MOUSE_SENSITIVITY)
    scaled_dy = int(dy * MOUSE_SENSITIVITY)

    _mouse.move(scaled_dx, scaled_dy)


def _handle_mouse_click(event: dict) -> None:
    """
    Press or release a mouse button.
    """
    button_name = event.get("button", "left")
    action = event.get("action", "tap")

    button = _BUTTON_MAP.get(button_name, Button.left)

    if action == "down":
        _mouse.press(button)
    elif action == "up":
        _mouse.release(button)
    else:
        # "tap" = click (press + release)
        _mouse.click(button)

    logger.debug("Mouse %s: %s", action, button_name)


def _handle_mouse_scroll(event: dict) -> None:
    """
    Scroll by (dx, dy). Positive dy = scroll up, negative = scroll down.
    """
    dx = event.get("dx", 0)
    dy = event.get("dy", 0)

    scaled_dx = int(dx * SCROLL_SENSITIVITY)
    scaled_dy = int(dy * SCROLL_SENSITIVITY)

    _mouse.scroll(scaled_dx, scaled_dy)
    logger.debug("Scroll: dx=%d, dy=%d", scaled_dx, scaled_dy)


def _handle_zoom(event: dict) -> None:
    """
    Pinch-to-zoom — atomic Ctrl+Scroll.

    Holds Ctrl, scrolls by delta, then releases Ctrl in one go.
    Positive delta = zoom in, negative = zoom out.
    This is the standard Windows pinch-to-zoom behavior (Ctrl+Scroll).
    """
    delta = event.get("delta", 0)
    if delta == 0:
        return

    ctrl_key = Key.ctrl_l
    _keyboard.press(ctrl_key)
    _mouse.scroll(0, delta)
    _keyboard.release(ctrl_key)
    logger.debug("Zoom: delta=%d", delta)

