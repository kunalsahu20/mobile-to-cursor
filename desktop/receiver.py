"""
Vexra — Desktop Receiver (GUI)

Premium windowed TCP server matching the Vexra landing page aesthetic.
Runs asyncio in a background thread, updates tkinter GUI via queue.
"""

import asyncio
import logging
import os
import queue
import secrets
import socket
import sys
import tempfile
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
    """Send log records to a thread-safe queue for the GUI."""

    def emit(self, record: logging.LogRecord) -> None:
        try:
            msg = self.format(record)
            _log_queue.put(("log", msg))
        except Exception:
            pass


logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(message)s",
    datefmt="%H:%M:%S",
    handlers=[QueueHandler()],
)
logger = logging.getLogger(__name__)

# ── Auth & connection state ────────────────────
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
    """Handle a single connected phone — requires PIN auth first."""
    peer = writer.get_extra_info("peername")
    logger.info("📱  Phone connected: %s", peer)
    _log_queue.put(("status", "connecting"))

    if _connection_lock.locked():
        logger.warning("⛔  Rejected %s — another device connected", peer)
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
            logger.exception("Unexpected error in client handler")
        finally:
            writer.close()
            try:
                await writer.wait_closed()
            except Exception:
                pass
            status = "authenticated" if authenticated else "unauthenticated"
            logger.info("📱  Phone disconnected (%s)", status)
            _log_queue.put(("status", "waiting"))


async def run_server() -> None:
    """Start the TCP server."""
    server = await asyncio.start_server(handle_client, host=HOST, port=PORT)
    logger.info("Server listening on port %d", PORT)
    _log_queue.put(("status", "waiting"))
    try:
        async with server:
            await server.serve_forever()
    except asyncio.CancelledError:
        pass
    finally:
        server.close()
        await server.wait_closed()


def _server_thread_target() -> None:
    """Entry point for the background server thread."""
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)
    try:
        asyncio.run(run_server())
    except Exception:
        logger.exception("Server thread crashed")


# ═══════════════════════════════════════════════
#  GUI — Premium Dark Theme
# ═══════════════════════════════════════════════

# Color palette (matches landing page)
C_BG = "#050505"
C_SURFACE = "#0c0c0c"
C_CARD = "#111113"
C_BORDER = "#1e1e22"
C_TEXT = "#f0f0f0"
C_MUTED = "#707078"
C_ACCENT = "#7c5ce7"
C_ACCENT_GLOW = "#9b7dff"
C_GREEN = "#34d399"
C_YELLOW = "#fbbf24"
C_RED = "#f87171"

F_BRAND = ("Segoe UI", 32, "bold")
F_TAGLINE = ("Segoe UI", 10)
F_LABEL = ("Segoe UI", 9)
F_VALUE = ("Segoe UI", 11)
F_PIN = ("Consolas", 22, "bold")
F_MONO = ("Consolas", 9)
F_BTN = ("Segoe UI", 9, "bold")
F_STATUS = ("Segoe UI", 10)


class VexraApp:
    """Premium dark GUI for the Vexra receiver."""

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("Vexra")
        self.root.configure(bg=C_BG)
        self.root.resizable(False, False)

        # Window size and center
        w, h = 460, 620
        sx = self.root.winfo_screenwidth()
        sy = self.root.winfo_screenheight()
        self.root.geometry(f"{w}x{h}+{(sx - w) // 2}+{(sy - h) // 2}")

        # Set window icon
        ico_path = resource_path("vexra.ico")
        if os.path.isfile(ico_path):
            try:
                self.root.iconbitmap(ico_path)
            except Exception:
                pass

        self._build_ui()
        self._poll_queue()

    # ────────────────────────────────────────────
    #  UI Construction
    # ────────────────────────────────────────────

    def _build_ui(self) -> None:
        """Build the entire premium layout."""

        # ── Glow canvas (top accent) ──
        glow = tk.Canvas(
            self.root, width=460, height=3, bg=C_BG,
            highlightthickness=0, bd=0,
        )
        glow.pack(fill="x")
        glow.create_rectangle(60, 0, 400, 3, fill=C_ACCENT, outline="")

        # ── Header ──
        header = tk.Frame(self.root, bg=C_BG)
        header.pack(fill="x", pady=(32, 0))

        tk.Label(
            header, text="V E X R A", font=F_BRAND,
            fg=C_TEXT, bg=C_BG,
        ).pack()

        tk.Label(
            header, text="Your phone. Your trackpad. No wires.",
            font=F_TAGLINE, fg=C_MUTED, bg=C_BG,
        ).pack(pady=(6, 0))

        # ── Status pill ──
        status_frame = tk.Frame(self.root, bg=C_BG)
        status_frame.pack(pady=(20, 0))

        self.status_pill = tk.Frame(
            status_frame, bg=C_CARD,
            highlightbackground=C_BORDER,
            highlightthickness=1,
            padx=16, pady=6,
        )
        self.status_pill.pack()

        self.status_dot = tk.Label(
            self.status_pill, text="●", font=("Segoe UI", 8),
            fg=C_YELLOW, bg=C_CARD,
        )
        self.status_dot.pack(side="left")

        self.status_label = tk.Label(
            self.status_pill, text="  Starting...",
            font=F_STATUS, fg=C_MUTED, bg=C_CARD,
        )
        self.status_label.pack(side="left")

        # ── Connection card ──
        card = tk.Frame(
            self.root, bg=C_CARD,
            highlightbackground=C_BORDER,
            highlightthickness=1,
        )
        card.pack(fill="x", padx=24, pady=(20, 0))

        # IP section
        ip_section = tk.Frame(card, bg=C_CARD, pady=16, padx=20)
        ip_section.pack(fill="x")

        ip_left = tk.Frame(ip_section, bg=C_CARD)
        ip_left.pack(side="left")

        tk.Label(
            ip_left, text="IP ADDRESS", font=F_LABEL,
            fg=C_MUTED, bg=C_CARD, anchor="w",
        ).pack(anchor="w")

        local_ips = get_local_ips()
        ip_text = local_ips[0] if local_ips else "No network"
        tk.Label(
            ip_left, text=ip_text, font=F_VALUE,
            fg=C_TEXT, bg=C_CARD, anchor="w",
        ).pack(anchor="w", pady=(2, 0))

        ip_right = tk.Frame(ip_section, bg=C_CARD)
        ip_right.pack(side="right")

        tk.Label(
            ip_right, text="PORT", font=F_LABEL,
            fg=C_MUTED, bg=C_CARD, anchor="e",
        ).pack(anchor="e")

        tk.Label(
            ip_right, text=str(PORT), font=F_VALUE,
            fg=C_TEXT, bg=C_CARD, anchor="e",
        ).pack(anchor="e", pady=(2, 0))

        # Divider inside card
        tk.Frame(card, bg=C_BORDER, height=1).pack(fill="x", padx=20)

        # PIN section
        pin_section = tk.Frame(card, bg=C_CARD, pady=16, padx=20)
        pin_section.pack(fill="x")

        tk.Label(
            pin_section, text="CONNECTION PIN", font=F_LABEL,
            fg=C_MUTED, bg=C_CARD, anchor="w",
        ).pack(anchor="w")

        pin_row = tk.Frame(pin_section, bg=C_CARD)
        pin_row.pack(fill="x", pady=(4, 0))

        self.pin_label = tk.Label(
            pin_row, text=self._format_pin(AUTH_PIN),
            font=F_PIN, fg=C_ACCENT_GLOW, bg=C_CARD,
        )
        self.pin_label.pack(side="left")

        # New PIN button inline
        self.new_pin_btn = tk.Label(
            pin_row, text="↻ Regenerate", font=F_LABEL,
            fg=C_ACCENT, bg=C_CARD, cursor="hand2",
        )
        self.new_pin_btn.pack(side="right", pady=(8, 0))
        self.new_pin_btn.bind("<Button-1>", lambda e: self._regenerate_pin())
        self.new_pin_btn.bind("<Enter>", lambda e: self.new_pin_btn.configure(fg=C_ACCENT_GLOW))
        self.new_pin_btn.bind("<Leave>", lambda e: self.new_pin_btn.configure(fg=C_ACCENT))

        # ── Log area ──
        log_outer = tk.Frame(self.root, bg=C_BG)
        log_outer.pack(fill="both", expand=True, padx=24, pady=(16, 0))

        tk.Label(
            log_outer, text="ACTIVITY", font=F_LABEL,
            fg=C_MUTED, bg=C_BG, anchor="w",
        ).pack(fill="x", pady=(0, 6))

        log_container = tk.Frame(
            log_outer, bg=C_SURFACE,
            highlightbackground=C_BORDER,
            highlightthickness=1,
        )
        log_container.pack(fill="both", expand=True)

        self.log_text = tk.Text(
            log_container, bg=C_SURFACE, fg=C_MUTED,
            font=F_MONO, relief="flat", bd=0,
            padx=12, pady=10, wrap="word",
            state="disabled", cursor="arrow",
            insertbackground=C_MUTED,
            selectbackground=C_ACCENT,
            highlightthickness=0,
        )
        self.log_text.pack(fill="both", expand=True)

        self.log_text.tag_configure("success", foreground=C_GREEN)
        self.log_text.tag_configure("warning", foreground=C_YELLOW)
        self.log_text.tag_configure("error", foreground=C_RED)
        self.log_text.tag_configure("info", foreground=C_MUTED)

        # ── Bottom bar ──
        bottom = tk.Frame(self.root, bg=C_BG, pady=14, padx=24)
        bottom.pack(fill="x")

        # Quit button (bottom right)
        self.quit_btn = tk.Label(
            bottom, text="✕  Quit", font=F_BTN,
            fg=C_MUTED, bg=C_BG, cursor="hand2",
        )
        self.quit_btn.pack(side="right")
        self.quit_btn.bind("<Button-1>", lambda e: self._on_quit())
        self.quit_btn.bind("<Enter>", lambda e: self.quit_btn.configure(fg=C_RED))
        self.quit_btn.bind("<Leave>", lambda e: self.quit_btn.configure(fg=C_MUTED))

        # Version label (bottom left)
        tk.Label(
            bottom, text="v1.0.0", font=F_LABEL,
            fg="#333338", bg=C_BG,
        ).pack(side="left")

        # ── Bottom accent line ──
        glow_b = tk.Canvas(
            self.root, width=460, height=2, bg=C_BG,
            highlightthickness=0, bd=0,
        )
        glow_b.pack(fill="x", side="bottom")
        glow_b.create_rectangle(80, 0, 380, 2, fill=C_ACCENT, outline="")

    # ────────────────────────────────────────────
    #  Helpers
    # ────────────────────────────────────────────

    @staticmethod
    def _format_pin(pin: str) -> str:
        """Format PIN with spacing for readability: '482910' → '482  910'."""
        return f"{pin[:3]}  {pin[3:]}"

    def _append_log(self, message: str) -> None:
        """Add a line to the log with color tagging."""
        self.log_text.configure(state="normal")

        tag = "info"
        if "✅" in message:
            tag = "success"
        elif "⛔" in message:
            tag = "warning"
        elif "error" in message.lower() or "crashed" in message.lower():
            tag = "error"

        self.log_text.insert("end", message + "\n", tag)
        self.log_text.see("end")
        self.log_text.configure(state="disabled")

    def _update_status(self, status: str) -> None:
        """Update the status pill."""
        status_map = {
            "connected": (C_GREEN, "  Connected"),
            "waiting": (C_YELLOW, "  Waiting for phone..."),
            "connecting": (C_YELLOW, "  Authenticating..."),
            "error": (C_RED, "  Error"),
        }
        color, text = status_map.get(status, (C_MUTED, f"  {status}"))
        self.status_dot.configure(fg=color)
        self.status_label.configure(text=text, fg=color if status == "connected" else C_MUTED)

    def _regenerate_pin(self) -> None:
        """Generate a new PIN and update display."""
        global AUTH_PIN
        AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
        self.pin_label.configure(text=self._format_pin(AUTH_PIN))
        ts = datetime.now().strftime("%H:%M:%S")
        self._append_log(f"{ts}  🔄  New PIN generated")

    def _poll_queue(self) -> None:
        """Drain the log queue and update GUI."""
        while not _log_queue.empty():
            try:
                msg_type, msg_data = _log_queue.get_nowait()
                if msg_type == "log":
                    self._append_log(msg_data)
                elif msg_type == "status":
                    self._update_status(msg_data)
            except queue.Empty:
                break
        self.root.after(100, self._poll_queue)

    def _on_quit(self) -> None:
        """Clean shutdown."""
        self.root.destroy()

    def run(self) -> None:
        """Start the GUI main loop."""
        self.root.protocol("WM_DELETE_WINDOW", self._on_quit)
        self.root.mainloop()


# ═══════════════════════════════════════════════
#  Entry point
# ═══════════════════════════════════════════════

def main() -> None:
    """Launch server thread + GUI."""
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)

    server_thread = threading.Thread(target=_server_thread_target, daemon=True)
    server_thread.start()

    app = VexraApp()
    app.run()


if __name__ == "__main__":
    main()
