"""
Vexra — Desktop Receiver (GUI)

Premium windowed TCP server with Stitch-designed UI.
Consistent dark aesthetic matching the Vexra landing page.
Runs asyncio in a background thread, updates tkinter via queue.
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
    """Resolve path for PyInstaller --onefile bundles."""
    if hasattr(sys, "_MEIPASS"):
        return os.path.join(sys._MEIPASS, relative_path)
    return os.path.join(os.path.abspath(os.path.dirname(__file__)), relative_path)


# ── Logging → queue bridge ─────────────────────
_log_queue: queue.Queue = queue.Queue()


class QueueHandler(logging.Handler):
    """Route log records to thread-safe queue for GUI consumption."""

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

# ── State ──────────────────────────────────────
AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
_connection_lock = asyncio.Lock()


def get_local_ips() -> list[str]:
    """Return all local IPv4 addresses."""
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
#  TCP Server (background thread)
# ═══════════════════════════════════════════════

async def handle_client(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
) -> None:
    """Handle a single phone connection with PIN auth."""
    peer = writer.get_extra_info("peername")
    logger.info("📱  Phone connected: %s", peer)
    _log_queue.put(("status", "connecting"))

    if _connection_lock.locked():
        logger.warning("⛔  Rejected %s — busy", peer)
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
                logger.warning("⛔  Auth timeout from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"timeout"}\n')
                await writer.drain()
                return

            if not data:
                return

            line = data.decode("utf-8", errors="replace")
            event = parse_event(line)

            if event is None or event.get("type") != "AUTH":
                logger.warning("⛔  Expected AUTH from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"expected_auth"}\n')
                await writer.drain()
                return

            if event.get("pin") != AUTH_PIN:
                logger.warning("⛔  Wrong PIN from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"wrong_pin"}\n')
                await writer.drain()
                return

            authenticated = True
            writer.write(b'{"status":"AUTH_OK"}\n')
            await writer.drain()
            logger.info("✅  Authenticated: %s", peer)
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
            logger.info("Client handler cancelled")
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
            tag = "authenticated" if authenticated else "unauthenticated"
            logger.info("📱  Disconnected (%s)", tag)
            _log_queue.put(("status", "waiting"))


async def run_server() -> None:
    """Start the TCP server."""
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
    """Background thread entry."""
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)
    try:
        asyncio.run(run_server())
    except Exception:
        logger.exception("Server crashed")


# ═══════════════════════════════════════════════
#  GUI — Stitch-designed premium dark theme
# ═══════════════════════════════════════════════

# Color tokens (from Stitch: #7b5ae7 purple, dark mode, Inter, round-4)
BG = "#000000"
SURFACE_1 = "#0a0a0c"
SURFACE_2 = "#111114"
SURFACE_3 = "#18181c"
BORDER = "#1f1f24"
BORDER_HOVER = "#2a2a30"
TEXT = "#f5f5f7"
TEXT_SECONDARY = "#a1a1a8"
TEXT_MUTED = "#6b6b74"
ACCENT = "#7b5ae7"
ACCENT_HOVER = "#9478f0"
ACCENT_DIM = "#4a3590"
GREEN = "#34d399"
YELLOW = "#fbbf24"
RED = "#f87171"

RADIUS = 8  # round-4 from Stitch


class VexraApp:
    """Premium windowed receiver matching Stitch design."""

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("Vexra")
        self.root.configure(bg=BG)
        self.root.resizable(False, False)

        w, h = 480, 640
        sx = self.root.winfo_screenwidth()
        sy = self.root.winfo_screenheight()
        self.root.geometry(f"{w}x{h}+{(sx - w) // 2}+{(sy - h) // 2}")

        ico = resource_path("vexra.ico")
        if os.path.isfile(ico):
            try:
                self.root.iconbitmap(ico)
            except Exception:
                pass

        self._build()
        self._poll()

    # ── Layout ──

    def _build(self) -> None:
        r = self.root

        # ── Top accent gradient bar ──
        top_bar = tk.Canvas(r, width=480, height=3, bg=BG, highlightthickness=0)
        top_bar.pack(fill="x")
        # Simulate gradient: purple center, fading edges
        top_bar.create_rectangle(0, 0, 480, 3, fill="#0d0d12", outline="")
        top_bar.create_rectangle(40, 0, 440, 3, fill=ACCENT_DIM, outline="")
        top_bar.create_rectangle(100, 0, 380, 3, fill=ACCENT, outline="")

        # ── Header section ──
        hdr = tk.Frame(r, bg=BG)
        hdr.pack(fill="x", pady=(36, 0))

        tk.Label(
            hdr, text="V E X R A",
            font=("Segoe UI", 28, "bold"),
            fg=TEXT, bg=BG,
        ).pack()

        tk.Label(
            hdr, text="Your phone. Your trackpad. No wires.",
            font=("Segoe UI", 10), fg=TEXT_MUTED, bg=BG,
        ).pack(pady=(6, 0))

        # ── Status pill ──
        pill_wrap = tk.Frame(r, bg=BG)
        pill_wrap.pack(pady=(20, 0))

        pill = tk.Frame(
            pill_wrap, bg=SURFACE_2,
            highlightbackground=BORDER, highlightthickness=1,
            padx=14, pady=5,
        )
        pill.pack()

        self._status_dot = tk.Label(
            pill, text="●", font=("Segoe UI", 9), fg=YELLOW, bg=SURFACE_2,
        )
        self._status_dot.pack(side="left")

        self._status_text = tk.Label(
            pill, text="  Starting…",
            font=("Segoe UI", 10), fg=TEXT_SECONDARY, bg=SURFACE_2,
        )
        self._status_text.pack(side="left")

        # ── Connection card ──
        card = tk.Frame(
            r, bg=SURFACE_2,
            highlightbackground=BORDER, highlightthickness=1,
        )
        card.pack(fill="x", padx=28, pady=(20, 0))

        # -- Top half: IP + Port --
        top_half = tk.Frame(card, bg=SURFACE_2, padx=20, pady=16)
        top_half.pack(fill="x")

        left = tk.Frame(top_half, bg=SURFACE_2)
        left.pack(side="left", anchor="nw")

        tk.Label(
            left, text="IP ADDRESS",
            font=("Segoe UI", 8, "bold"), fg=TEXT_MUTED, bg=SURFACE_2,
        ).pack(anchor="w")

        ips = get_local_ips()
        ip = ips[0] if ips else "No network"
        tk.Label(
            left, text=ip,
            font=("Segoe UI", 13), fg=TEXT, bg=SURFACE_2,
        ).pack(anchor="w", pady=(4, 0))

        right = tk.Frame(top_half, bg=SURFACE_2)
        right.pack(side="right", anchor="ne")

        tk.Label(
            right, text="PORT",
            font=("Segoe UI", 8, "bold"), fg=TEXT_MUTED, bg=SURFACE_2,
        ).pack(anchor="e")

        tk.Label(
            right, text=str(PORT),
            font=("Segoe UI", 13), fg=TEXT, bg=SURFACE_2,
        ).pack(anchor="e", pady=(4, 0))

        # -- Divider --
        tk.Frame(card, bg=BORDER, height=1).pack(fill="x", padx=20)

        # -- Bottom half: PIN --
        bot_half = tk.Frame(card, bg=SURFACE_2, padx=20, pady=16)
        bot_half.pack(fill="x")

        tk.Label(
            bot_half, text="CONNECTION PIN",
            font=("Segoe UI", 8, "bold"), fg=TEXT_MUTED, bg=SURFACE_2,
        ).pack(anchor="w")

        pin_row = tk.Frame(bot_half, bg=SURFACE_2)
        pin_row.pack(fill="x", pady=(6, 0))

        self._pin = tk.Label(
            pin_row, text=self._fmt_pin(AUTH_PIN),
            font=("Consolas", 26, "bold"), fg=ACCENT, bg=SURFACE_2,
        )
        self._pin.pack(side="left")

        regen = tk.Label(
            pin_row, text="↻  Regenerate",
            font=("Segoe UI", 9), fg=TEXT_MUTED, bg=SURFACE_2, cursor="hand2",
        )
        regen.pack(side="right", pady=(10, 0))
        regen.bind("<Button-1>", lambda e: self._new_pin())
        regen.bind("<Enter>", lambda e: regen.configure(fg=ACCENT_HOVER))
        regen.bind("<Leave>", lambda e: regen.configure(fg=TEXT_MUTED))

        # ── Activity log ──
        log_wrap = tk.Frame(r, bg=BG)
        log_wrap.pack(fill="both", expand=True, padx=28, pady=(16, 0))

        tk.Label(
            log_wrap, text="ACTIVITY",
            font=("Segoe UI", 8, "bold"), fg=TEXT_MUTED, bg=BG,
        ).pack(anchor="w", pady=(0, 6))

        log_border = tk.Frame(
            log_wrap, bg=SURFACE_1,
            highlightbackground=BORDER, highlightthickness=1,
        )
        log_border.pack(fill="both", expand=True)

        self._log = tk.Text(
            log_border, bg=SURFACE_1, fg=TEXT_MUTED,
            font=("Consolas", 9), relief="flat", bd=0,
            padx=14, pady=12, wrap="word",
            state="disabled", cursor="arrow",
            insertbackground=TEXT_MUTED, selectbackground=ACCENT_DIM,
            highlightthickness=0,
        )
        self._log.pack(fill="both", expand=True)

        self._log.tag_configure("ok", foreground=GREEN)
        self._log.tag_configure("warn", foreground=YELLOW)
        self._log.tag_configure("err", foreground=RED)
        self._log.tag_configure("dim", foreground=TEXT_MUTED)

        # ── Bottom bar ──
        foot = tk.Frame(r, bg=BG, padx=28, pady=12)
        foot.pack(fill="x")

        tk.Label(
            foot, text="v1.0.0",
            font=("Segoe UI", 8), fg="#2a2a30", bg=BG,
        ).pack(side="left")

        quit_lbl = tk.Label(
            foot, text="✕  Quit",
            font=("Segoe UI", 9, "bold"), fg=TEXT_MUTED, bg=BG, cursor="hand2",
        )
        quit_lbl.pack(side="right")
        quit_lbl.bind("<Button-1>", lambda e: self.root.destroy())
        quit_lbl.bind("<Enter>", lambda e: quit_lbl.configure(fg=RED))
        quit_lbl.bind("<Leave>", lambda e: quit_lbl.configure(fg=TEXT_MUTED))

        # ── Bottom accent gradient bar ──
        bot_bar = tk.Canvas(r, width=480, height=2, bg=BG, highlightthickness=0)
        bot_bar.pack(fill="x", side="bottom")
        bot_bar.create_rectangle(0, 0, 480, 2, fill="#0d0d12", outline="")
        bot_bar.create_rectangle(60, 0, 420, 2, fill=ACCENT_DIM, outline="")
        bot_bar.create_rectangle(140, 0, 340, 2, fill=ACCENT, outline="")

    # ── Helpers ──

    @staticmethod
    def _fmt_pin(pin: str) -> str:
        return f"{pin[:3]}   {pin[3:]}"

    def _log_msg(self, msg: str) -> None:
        self._log.configure(state="normal")
        tag = "dim"
        if "✅" in msg:
            tag = "ok"
        elif "⛔" in msg:
            tag = "warn"
        elif "error" in msg.lower() or "crash" in msg.lower():
            tag = "err"
        self._log.insert("end", msg + "\n", tag)
        self._log.see("end")
        self._log.configure(state="disabled")

    def _set_status(self, s: str) -> None:
        m = {
            "connected": (GREEN, "  Connected"),
            "waiting": (YELLOW, "  Waiting for phone…"),
            "connecting": (YELLOW, "  Authenticating…"),
            "error": (RED, "  Error"),
        }
        color, label = m.get(s, (TEXT_MUTED, f"  {s}"))
        self._status_dot.configure(fg=color)
        fg = color if s == "connected" else TEXT_SECONDARY
        self._status_text.configure(text=label, fg=fg)

    def _new_pin(self) -> None:
        global AUTH_PIN
        AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
        self._pin.configure(text=self._fmt_pin(AUTH_PIN))
        ts = datetime.now().strftime("%H:%M:%S")
        self._log_msg(f"{ts}  🔄  New PIN generated")

    def _poll(self) -> None:
        while not _log_queue.empty():
            try:
                t, d = _log_queue.get_nowait()
                if t == "log":
                    self._log_msg(d)
                elif t == "status":
                    self._set_status(d)
            except queue.Empty:
                break
        self.root.after(100, self._poll)

    def run(self) -> None:
        self.root.protocol("WM_DELETE_WINDOW", self.root.destroy)
        self.root.mainloop()


# ═══════════════════════════════════════════════
#  Entry
# ═══════════════════════════════════════════════

def main() -> None:
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)

    threading.Thread(target=_server_thread, daemon=True).start()
    app = VexraApp()
    app.run()


if __name__ == "__main__":
    main()
