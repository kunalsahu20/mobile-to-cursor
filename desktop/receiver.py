"""
Vexra — Desktop Receiver (GUI)

Windowed TCP server matching the Vexra landing page aesthetic.
Runs asyncio in a background thread, updates tkinter GUI via queue.
"""

import asyncio
import logging
import queue
import secrets
import signal
import socket
import sys
import threading
import tkinter as tk
from datetime import datetime

from config import HOST, PORT
from protocol import parse_event
from injector import dispatch

# ── Logging → queue bridge ─────────────────────
_log_queue: queue.Queue = queue.Queue()


class QueueHandler(logging.Handler):
    """Send log records to a thread-safe queue for the GUI to consume."""

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
# TCP Server (runs in background thread)
# ═══════════════════════════════════════════════

async def handle_client(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
) -> None:
    """Handle a single connected phone — requires PIN auth first."""
    peer = writer.get_extra_info("peername")
    logger.info("📱 Phone connected: %s", peer)
    _log_queue.put(("status", "connecting"))

    if _connection_lock.locked():
        logger.warning("⛔ Rejected %s — another device connected", peer)
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
            # Step 1: Authenticate
            try:
                data = await asyncio.wait_for(reader.readline(), timeout=10.0)
            except asyncio.TimeoutError:
                logger.warning("⛔ Auth timeout from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"timeout"}\n')
                await writer.drain()
                return

            if not data:
                return

            line = data.decode("utf-8", errors="replace")
            event = parse_event(line)

            if event is None or event.get("type") != "AUTH":
                logger.warning("⛔ Expected AUTH from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"expected_auth"}\n')
                await writer.drain()
                return

            if event.get("pin") != AUTH_PIN:
                logger.warning("⛔ Wrong PIN from %s", peer)
                writer.write(b'{"status":"AUTH_FAIL","reason":"wrong_pin"}\n')
                await writer.drain()
                return

            # Auth passed
            authenticated = True
            writer.write(b'{"status":"AUTH_OK"}\n')
            await writer.drain()
            logger.info("✅ Authenticated: %s", peer)
            _log_queue.put(("status", "connected"))

            # Step 2: Event loop
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
            logger.info("📱 Phone disconnected (%s)", status)
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
# GUI (runs on main thread)
# ═══════════════════════════════════════════════

# Design tokens
BG = "#000000"
BG_CARD = "#0a0a0a"
BG_INPUT = "#111111"
FG = "#ffffff"
FG_MUTED = "#888888"
ACCENT = "#7850FF"
ACCENT_DIM = "#5a3dba"
BORDER = "#1a1a1a"
GREEN = "#22c55e"
YELLOW = "#eab308"
RED = "#ef4444"
FONT = ("Segoe UI", 10)
FONT_SM = ("Segoe UI", 9)
FONT_LG = ("Segoe UI", 14, "bold")
FONT_XL = ("Segoe UI", 28, "bold")
FONT_MONO = ("Consolas", 9)


class VexraApp:
    """Main GUI application."""

    def __init__(self) -> None:
        self.root = tk.Tk()
        self.root.title("Vexra")
        self.root.configure(bg=BG)
        self.root.resizable(False, False)

        # Window size and center
        win_w, win_h = 480, 580
        screen_w = self.root.winfo_screenwidth()
        screen_h = self.root.winfo_screenheight()
        x = (screen_w - win_w) // 2
        y = (screen_h - win_h) // 2
        self.root.geometry(f"{win_w}x{win_h}+{x}+{y}")

        # Try to set icon
        try:
            self.root.iconbitmap("vexra.ico")
        except Exception:
            pass

        self._build_ui()
        self._poll_queue()

    def _build_ui(self) -> None:
        """Construct the entire GUI layout."""
        root = self.root

        # ── Header ──
        header = tk.Frame(root, bg=BG, pady=24)
        header.pack(fill="x")

        tk.Label(
            header, text="VEXRA", font=FONT_XL, fg=FG, bg=BG,
        ).pack()
        tk.Label(
            header, text="Your phone. Your trackpad. No wires.",
            font=FONT_SM, fg=FG_MUTED, bg=BG,
        ).pack(pady=(4, 0))

        # ── Separator ──
        self._separator(root)

        # ── Connection info panel ──
        info_frame = tk.Frame(root, bg=BG_CARD, padx=20, pady=16)
        info_frame.pack(fill="x", padx=16, pady=(12, 0))

        # IP row
        ip_row = tk.Frame(info_frame, bg=BG_CARD)
        ip_row.pack(fill="x", pady=(0, 8))

        local_ips = get_local_ips()
        ip_text = local_ips[0] if local_ips else "No network"

        tk.Label(
            ip_row, text="IP", font=FONT_SM, fg=FG_MUTED, bg=BG_CARD,
        ).pack(side="left")
        self.ip_label = tk.Label(
            ip_row, text=ip_text, font=FONT, fg=FG, bg=BG_CARD,
        )
        self.ip_label.pack(side="left", padx=(12, 0))

        tk.Label(
            ip_row, text="PIN", font=FONT_SM, fg=FG_MUTED, bg=BG_CARD,
        ).pack(side="left", padx=(32, 0))
        self.pin_label = tk.Label(
            ip_row, text=AUTH_PIN, font=("Consolas", 14, "bold"),
            fg=ACCENT, bg=BG_CARD,
        )
        self.pin_label.pack(side="left", padx=(12, 0))

        # Port + Status row
        status_row = tk.Frame(info_frame, bg=BG_CARD)
        status_row.pack(fill="x")

        tk.Label(
            status_row, text="Port", font=FONT_SM, fg=FG_MUTED, bg=BG_CARD,
        ).pack(side="left")
        tk.Label(
            status_row, text=str(PORT), font=FONT, fg=FG, bg=BG_CARD,
        ).pack(side="left", padx=(12, 0))

        tk.Label(
            status_row, text="Status", font=FONT_SM, fg=FG_MUTED, bg=BG_CARD,
        ).pack(side="left", padx=(32, 0))

        self.status_dot = tk.Label(
            status_row, text="●", font=FONT_SM, fg=YELLOW, bg=BG_CARD,
        )
        self.status_dot.pack(side="left", padx=(8, 0))

        self.status_text = tk.Label(
            status_row, text="Starting...", font=FONT_SM, fg=FG_MUTED, bg=BG_CARD,
        )
        self.status_text.pack(side="left", padx=(4, 0))

        # ── Separator ──
        self._separator(root, pady=12)

        # ── Log area ──
        log_frame = tk.Frame(root, bg=BG, padx=16)
        log_frame.pack(fill="both", expand=True)

        tk.Label(
            log_frame, text="Activity Log", font=FONT_SM,
            fg=FG_MUTED, bg=BG, anchor="w",
        ).pack(fill="x", pady=(0, 6))

        self.log_text = tk.Text(
            log_frame, bg=BG_INPUT, fg=FG_MUTED, font=FONT_MONO,
            relief="flat", bd=0, padx=12, pady=10,
            wrap="word", state="disabled", cursor="arrow",
            insertbackground=FG_MUTED, selectbackground=ACCENT_DIM,
            highlightthickness=1, highlightbackground=BORDER,
            height=12,
        )
        self.log_text.pack(fill="both", expand=True)

        # Tag for highlighted lines
        self.log_text.tag_configure("success", foreground=GREEN)
        self.log_text.tag_configure("warning", foreground=YELLOW)
        self.log_text.tag_configure("error", foreground=RED)
        self.log_text.tag_configure("info", foreground=FG_MUTED)

        # ── Separator ──
        self._separator(root, pady=8)

        # ── Button bar ──
        btn_frame = tk.Frame(root, bg=BG, pady=12, padx=16)
        btn_frame.pack(fill="x")

        self.new_pin_btn = tk.Button(
            btn_frame, text="🔄  New PIN", font=FONT_SM,
            bg=BG_CARD, fg=FG, activebackground=ACCENT_DIM,
            activeforeground=FG, relief="flat", bd=0,
            padx=16, pady=8, cursor="hand2",
            command=self._regenerate_pin,
            highlightthickness=1, highlightbackground=BORDER,
        )
        self.new_pin_btn.pack(side="left")

        self.quit_btn = tk.Button(
            btn_frame, text="✕  Quit", font=FONT_SM,
            bg=BG_CARD, fg=FG_MUTED, activebackground=RED,
            activeforeground=FG, relief="flat", bd=0,
            padx=16, pady=8, cursor="hand2",
            command=self._on_quit,
            highlightthickness=1, highlightbackground=BORDER,
        )
        self.quit_btn.pack(side="right")

    def _separator(self, parent: tk.Widget, pady: int = 0) -> None:
        """Draw a subtle horizontal line."""
        sep = tk.Frame(parent, bg=BORDER, height=1)
        sep.pack(fill="x", padx=16, pady=pady)

    def _append_log(self, message: str) -> None:
        """Add a line to the log area with auto-scroll."""
        self.log_text.configure(state="normal")

        # Determine tag
        tag = "info"
        if "✅" in message or "AUTH_OK" in message:
            tag = "success"
        elif "⛔" in message or "Wrong" in message or "Rejected" in message:
            tag = "warning"
        elif "error" in message.lower() or "crashed" in message.lower():
            tag = "error"

        self.log_text.insert("end", message + "\n", tag)
        self.log_text.see("end")
        self.log_text.configure(state="disabled")

    def _update_status(self, status: str) -> None:
        """Update the status indicator."""
        if status == "connected":
            self.status_dot.configure(fg=GREEN)
            self.status_text.configure(text="Connected", fg=GREEN)
        elif status == "waiting":
            self.status_dot.configure(fg=YELLOW)
            self.status_text.configure(text="Waiting for phone...", fg=FG_MUTED)
        elif status == "connecting":
            self.status_dot.configure(fg=YELLOW)
            self.status_text.configure(text="Authenticating...", fg=YELLOW)
        elif status == "error":
            self.status_dot.configure(fg=RED)
            self.status_text.configure(text="Error", fg=RED)
        else:
            self.status_dot.configure(fg=FG_MUTED)
            self.status_text.configure(text=status, fg=FG_MUTED)

    def _regenerate_pin(self) -> None:
        """Generate a new PIN and update the display."""
        global AUTH_PIN
        AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
        self.pin_label.configure(text=AUTH_PIN)
        timestamp = datetime.now().strftime("%H:%M:%S")
        self._append_log(f"{timestamp}  🔄 New PIN generated: {AUTH_PIN}")

    def _poll_queue(self) -> None:
        """Drain the log queue and update the GUI."""
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
# Entry point
# ═══════════════════════════════════════════════

def main() -> None:
    """Launch server thread + GUI."""
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)

    # Start TCP server in background
    server_thread = threading.Thread(target=_server_thread_target, daemon=True)
    server_thread.start()

    # Run GUI on main thread
    app = VexraApp()
    app.run()


if __name__ == "__main__":
    main()
