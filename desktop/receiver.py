"""
Vexra — Desktop Receiver (GUI)

Ultra-minimalist pairing screen designed via Stitch.
Pure black, PIN-as-hero, typography-only — no borders, no cards, no effects.
Runs asyncio server in background thread, updates tkinter via queue.
"""

import asyncio
import logging
import os
import queue
import secrets
import socket
import sys
import threading
import tkinter as tk
from datetime import datetime

from config import HOST, PORT
from protocol import parse_event
from injector import dispatch


def resource_path(relative_path: str) -> str:
    if hasattr(sys, "_MEIPASS"):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath(os.path.dirname(__file__)), relative_path)


# ── Logging → queue ───────────────────────────
_log_queue: queue.Queue = queue.Queue()


class QueueHandler(logging.Handler):
    def emit(self, record: logging.LogRecord) -> None:
        try:
            _log_queue.put(("log", self.format(record)))
        except Exception:
            pass


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(message)s",
    datefmt="%H:%M:%S",
    handlers=[QueueHandler()],
)
logger = logging.getLogger(__name__)

AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
_connection_lock = asyncio.Lock()


def get_local_ips() -> list[str]:
    ips = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            addr = info[4][0]
            if addr not in ips and not addr.startswith("127."):
                ips.append(addr)
    except socket.gaierror:
        pass
    return ips


# ═══════════════════════════════════════════════
#  TCP Server
# ═══════════════════════════════════════════════

async def handle_client(reader, writer) -> None:
    peer = writer.get_extra_info("peername")
    logger.info("Phone connected: %s", peer)
    _log_queue.put(("status", "connecting"))

    if _connection_lock.locked():
        logger.warning("Rejected %s — busy", peer)
        writer.write(b'{"status":"AUTH_FAIL","reason":"busy"}\n')
        await writer.drain()
        writer.close()
        try:
            await writer.wait_closed()
        except Exception:
            pass
        return

    async with _connection_lock:
        authenticated = False
        try:
            try:
                data = await asyncio.wait_for(reader.readline(), timeout=10.0)
            except asyncio.TimeoutError:
                logger.warning("Auth timeout from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"timeout"}\n')
                await writer.drain()
                return
            if not data:
                return
            line = data.decode("utf-8", errors="replace")
            event = parse_event(line)
            if event is None or event.get("type") != "AUTH":
                writer.write(b'{"status":"AUTH_FAIL","reason":"expected_auth"}\n')
                await writer.drain()
                return
            if event.get("pin") != AUTH_PIN:
                logger.warning("Wrong PIN from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"wrong_pin"}\n')
                await writer.drain()
                return

            authenticated = True
            writer.write(b'{"status":"AUTH_OK"}\n')
            await writer.drain()
            logger.info("Authenticated: %s", peer)
            _log_queue.put(("status", "connected"))

            while True:
                data = await reader.readline()
                if not data:
                    break
                line = data.decode("utf-8", errors="replace")
                event = parse_event(line)
                if event is None or event.get("type") == "AUTH":
                    continue
                dispatch(event)
        except asyncio.CancelledError:
            pass
        except ConnectionResetError:
            logger.warning("Phone disconnected abruptly")
        except Exception:
            logger.exception("Unexpected error")
        finally:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass
            logger.info("Disconnected (%s)",
                        "authenticated" if authenticated else "unauthenticated")
            _log_queue.put(("status", "waiting"))


async def run_server() -> None:
    server = await asyncio.start_server(handle_client, host=HOST, port=PORT)
    logger.info("Listening on port %d", PORT)
    _log_queue.put(("status", "waiting"))
    try:
        async with server:
            await server.serve_forever()
    except asyncio.CancelledError:
        pass
    finally:
        server.close()
        await server.wait_closed()


def _server_thread() -> None:
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)
    try:
        asyncio.run(run_server())
    except Exception:
        logger.exception("Server crashed")


# ═══════════════════════════════════════════════
#  Colors — only black, white, gray, purple
# ═══════════════════════════════════════════════

BG = "#000000"
WHITE = "#ffffff"
GRAY = "#666666"
DIM = "#333333"
ACCENT = "#7c5ce7"
ACCENT_H = "#9b7dff"
GREEN = "#34d399"
YELLOW = "#fbbf24"
RED = "#f87171"

W, H = 440, 600


class VexraApp:
    """Ultra-minimalist pairing screen (Stitch design)."""

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("Vexra")
        self.root.configure(bg=BG)
        self.root.resizable(False, False)

        sx = self.root.winfo_screenwidth()
        sy = self.root.winfo_screenheight()
        self.root.geometry(f"{W}x{H}+{(sx - W) // 2}+{(sy - H) // 2}")

        ico = resource_path("vexra.ico")
        if os.path.isfile(ico):
            try:
                self.root.iconbitmap(ico)
            except Exception:
                pass

        self._build()
        self._poll()

    def _build(self) -> None:
        r = self.root

        # ── Breathing room at top ──
        tk.Frame(r, bg=BG, height=60).pack()

        # ── Brand ──
        tk.Label(
            r, text="V E X R A",
            font=("Segoe UI", 24, "bold"), fg=WHITE, bg=BG,
        ).pack()

        tk.Label(
            r, text="Your phone. Your trackpad. No wires.",
            font=("Segoe UI", 9), fg=GRAY, bg=BG,
        ).pack(pady=(8, 0))

        # ── Big gap ──
        tk.Frame(r, bg=BG, height=50).pack()

        # ── Hero: PIN ──
        self._pin = tk.Label(
            r, text=self._fpin(AUTH_PIN),
            font=("Consolas", 48, "bold"), fg=ACCENT, bg=BG,
        )
        self._pin.pack()

        tk.Label(
            r, text="Enter this PIN on your phone",
            font=("Segoe UI", 9), fg=GRAY, bg=BG,
        ).pack(pady=(10, 0))

        # Regenerate link
        regen = tk.Label(
            r, text="↻ New PIN",
            font=("Segoe UI", 9), fg=DIM, bg=BG, cursor="hand2",
        )
        regen.pack(pady=(8, 0))
        regen.bind("<Button-1>", lambda e: self._new_pin())
        regen.bind("<Enter>", lambda e: regen.configure(fg=ACCENT_H))
        regen.bind("<Leave>", lambda e: regen.configure(fg=DIM))

        # ── Big gap ──
        tk.Frame(r, bg=BG, height=40).pack()

        # ── Info: IP & Port ──
        ips = get_local_ips()
        ip = ips[0] if ips else "No network"

        tk.Label(
            r, text=f"{ip}  ·  Port {PORT}",
            font=("Consolas", 10), fg=DIM, bg=BG,
        ).pack()

        # ── Status ──
        status_frame = tk.Frame(r, bg=BG)
        status_frame.pack(pady=(16, 0))

        self._sdot = tk.Label(
            status_frame, text="●",
            font=("Segoe UI", 9), fg=YELLOW, bg=BG,
        )
        self._sdot.pack(side="left")

        self._stxt = tk.Label(
            status_frame, text="  Waiting for phone",
            font=("Segoe UI", 9), fg=GRAY, bg=BG,
        )
        self._stxt.pack(side="left")

        # ── Push footer to bottom ──
        tk.Frame(r, bg=BG).pack(fill="both", expand=True)

        # ── Footer ──
        foot = tk.Frame(r, bg=BG, padx=24, pady=16)
        foot.pack(fill="x")

        tk.Label(
            foot, text="v1.0.0",
            font=("Segoe UI", 8), fg="#1a1a1a", bg=BG,
        ).pack(side="left")

        ql = tk.Label(
            foot, text="Quit",
            font=("Segoe UI", 8), fg=DIM, bg=BG, cursor="hand2",
        )
        ql.pack(side="right")
        ql.bind("<Button-1>", lambda e: self.root.destroy())
        ql.bind("<Enter>", lambda e: ql.configure(fg=RED))
        ql.bind("<Leave>", lambda e: ql.configure(fg=DIM))

    # ── Helpers ──

    @staticmethod
    def _fpin(pin: str) -> str:
        return f"{pin[:3]}  {pin[3:]}"

    def _set_status(self, s: str) -> None:
        m = {
            "connected": (GREEN, "  Connected"),
            "waiting": (YELLOW, "  Waiting for phone"),
            "connecting": (YELLOW, "  Authenticating…"),
            "error": (RED, "  Error"),
        }
        color, label = m.get(s, (GRAY, f"  {s}"))
        self._sdot.configure(fg=color)
        self._stxt.configure(text=label)

    def _new_pin(self) -> None:
        global AUTH_PIN
        AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
        self._pin.configure(text=self._fpin(AUTH_PIN))

    def _poll(self) -> None:
        while not _log_queue.empty():
            try:
                t, d = _log_queue.get_nowait()
                if t == "status":
                    self._set_status(d)
            except queue.Empty:
                break
        self.root.after(100, self._poll)

    def run(self) -> None:
        self.root.protocol("WM_DELETE_WINDOW", self.root.destroy)
        self.root.mainloop()


def main() -> None:
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)
    threading.Thread(target=_server_thread, daemon=True).start()
    VexraApp().run()


if __name__ == "__main__":
    main()
