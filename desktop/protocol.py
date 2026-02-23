"""
Event protocol shared between Android app and desktop receiver.

Wire format (v1):
    - Each event is a single JSON object terminated by a newline '\n'.
    - The receiver reads line-by-line, parses JSON, and dispatches.

Event types:
    KEY_INPUT   — text insertion   {"type":"KEY_INPUT","text":"hello"}
    KEY_PRESS   — special key      {"type":"KEY_PRESS","key":"backspace","action":"down"}
    MOUSE_MOVE  — cursor delta     {"type":"MOUSE_MOVE","dx":5,"dy":-3}
    MOUSE_CLICK — button click     {"type":"MOUSE_CLICK","button":"left","action":"down"}
    MOUSE_SCROLL— scroll wheel     {"type":"MOUSE_SCROLL","dx":0,"dy":-3}
    HEARTBEAT   — keep-alive       {"type":"HEARTBEAT"}
    AUTH        — pin auth         {"type":"AUTH","pin":"123456"}
    ZOOM        — pinch zoom       {"type":"ZOOM","delta":3}
"""

import json
import logging

logger = logging.getLogger(__name__)

# ── Event type constants ─────────────────────
TYPE_KEY_INPUT = "KEY_INPUT"
TYPE_KEY_PRESS = "KEY_PRESS"
TYPE_MOUSE_MOVE = "MOUSE_MOVE"
TYPE_MOUSE_CLICK = "MOUSE_CLICK"
TYPE_MOUSE_SCROLL = "MOUSE_SCROLL"
TYPE_HEARTBEAT = "HEARTBEAT"
TYPE_AUTH = "AUTH"
TYPE_ZOOM = "ZOOM"

VALID_TYPES = {
    TYPE_KEY_INPUT,
    TYPE_KEY_PRESS,
    TYPE_MOUSE_MOVE,
    TYPE_MOUSE_CLICK,
    TYPE_MOUSE_SCROLL,
    TYPE_HEARTBEAT,
    TYPE_AUTH,
    TYPE_ZOOM,
}

# ── Special key mapping (Android name → pynput Key name) ──
SPECIAL_KEYS = {
    "backspace": "backspace",
    "enter": "enter",
    "tab": "tab",
    "escape": "esc",
    "delete": "delete",
    "space": "space",
    "up": "up",
    "down": "down",
    "left": "left",
    "right": "right",
    "home": "home",
    "end": "end",
    "page_up": "page_up",
    "page_down": "page_down",
    "ctrl": "ctrl_l",
    "alt": "alt_l",
    "shift": "shift_l",
    "caps_lock": "caps_lock",
    "win": "cmd",
    "print_screen": "print_screen",
    "insert": "insert",
    "num_lock": "num_lock",
    "volume_up": "media_volume_up",
    "volume_down": "media_volume_down",
    "volume_mute": "media_volume_mute",
    "media_play": "media_play_pause",
    "media_next": "media_next",
    "media_prev": "media_previous",
    "f1": "f1",
    "f2": "f2",
    "f3": "f3",
    "f4": "f4",
    "f5": "f5",
    "f6": "f6",
    "f7": "f7",
    "f8": "f8",
    "f9": "f9",
    "f10": "f10",
    "f11": "f11",
    "f12": "f12",
}


def parse_event(raw_line: str) -> dict | None:
    """
    Parse a single newline-terminated JSON line into an event dict.
    Returns None if the line is malformed or has an unknown type.
    """
    raw_line = raw_line.strip()
    if not raw_line:
        return None

    try:
        event = json.loads(raw_line)
    except json.JSONDecodeError as exc:
        logger.warning("Malformed JSON: %s — %s", raw_line[:80], exc)
        return None

    event_type = event.get("type")
    if event_type not in VALID_TYPES:
        logger.warning("Unknown event type: %s", event_type)
        return None

    return event


def serialize_event(event: dict) -> bytes:
    """Serialize an event dict to newline-terminated JSON bytes."""
    return (json.dumps(event, separators=(",", ":")) + "\n").encode("utf-8")
