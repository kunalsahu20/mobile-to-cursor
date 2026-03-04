"""
Configuration for the Vexra desktop receiver.
All tunable settings live here — no magic numbers scattered in code.
"""

# ──────────────────────────────────────────────
# Network
# ──────────────────────────────────────────────
HOST = "0.0.0.0"   # Listen on all interfaces (USB tether + Wi-Fi)
PORT = 5050         # TCP port — must match Android app

# ──────────────────────────────────────────────
# Mouse
# ──────────────────────────────────────────────
MOUSE_SENSITIVITY = 1.8     # Multiplier for dx/dy — higher = faster cursor
SCROLL_SENSITIVITY = 1.0    # Multiplier for scroll events

# ──────────────────────────────────────────────
# Receiver
# ──────────────────────────────────────────────
BUFFER_SIZE = 4096          # TCP read buffer (bytes)
MAX_CLIENTS = 1             # Only one phone at a time
