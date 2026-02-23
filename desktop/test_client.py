"""
Test client — simulates an Android phone sending events.

Run this AFTER starting receiver.py to verify everything works.
It sends a mix of keyboard, mouse, and scroll events so you
can see the cursor move and text appear in Notepad.

Usage:
    1.  Open Notepad (or any text editor)
    2.  Run:  python desktop/receiver.py       (in terminal 1)
    3.  Run:  python desktop/test_client.py    (in terminal 2)
    4.  Click into Notepad within 3 seconds
    5.  Watch the magic happen
"""

import socket
import json
import time
import sys

HOST = "127.0.0.1"
PORT = 5050


def send_event(sock: socket.socket, event: dict) -> None:
    """Send a single JSON event terminated by newline."""
    payload = json.dumps(event, separators=(",", ":")) + "\n"
    sock.sendall(payload.encode("utf-8"))


def main() -> None:
    print(f"Connecting to {HOST}:{PORT}...")
    sock = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
    try:
        sock.connect((HOST, PORT))
    except ConnectionRefusedError:
        print("ERROR: Could not connect. Is receiver.py running?")
        sys.exit(1)

    print("Connected! Click into Notepad within 3 seconds...")
    time.sleep(3)

    # ── Test 1: Type some text ──
    print("[Test 1] Typing text...")
    send_event(sock, {"type": "KEY_INPUT", "text": "Hello from Mobile to Cursor! "})
    time.sleep(0.5)

    # ── Test 2: Special keys ──
    print("[Test 2] Pressing Enter...")
    send_event(sock, {"type": "KEY_PRESS", "key": "enter", "action": "tap"})
    time.sleep(0.3)

    print("[Test 2] Typing more text...")
    send_event(sock, {"type": "KEY_INPUT", "text": "This is a new line."})
    time.sleep(0.5)

    # ── Test 3: Backspace ──
    print("[Test 3] Pressing Backspace 5 times...")
    for _ in range(5):
        send_event(sock, {"type": "KEY_PRESS", "key": "backspace", "action": "tap"})
        time.sleep(0.05)
    time.sleep(0.3)

    # ── Test 4: Mouse movement ──
    print("[Test 4] Moving mouse in a square pattern...")
    for _ in range(3):
        for dx, dy in [(10, 0), (0, 10), (-10, 0), (0, -10)]:
            send_event(sock, {"type": "MOUSE_MOVE", "dx": dx, "dy": dy})
            time.sleep(0.02)
    time.sleep(0.5)

    # ── Test 5: Mouse click ──
    print("[Test 5] Left click...")
    send_event(sock, {"type": "MOUSE_CLICK", "button": "left", "action": "tap"})
    time.sleep(0.3)

    # ── Test 6: Scroll ──
    print("[Test 6] Scrolling down...")
    for _ in range(5):
        send_event(sock, {"type": "MOUSE_SCROLL", "dx": 0, "dy": -2})
        time.sleep(0.05)
    time.sleep(0.3)

    # ── Test 7: Heartbeat ──
    print("[Test 7] Sending heartbeat...")
    send_event(sock, {"type": "HEARTBEAT"})

    print()
    print("✅ All tests sent! Check Notepad for typed text and watch cursor movement.")
    print("   Press Ctrl+C to exit.")

    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        pass
    finally:
        sock.close()


if __name__ == "__main__":
    main()
