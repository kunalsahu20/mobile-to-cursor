"""
Vexra — Desktop Receiver (GUI)

Stitch-designed minimalist dark UI with connection card and activity log.
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
    logger.info("Device connected: %s", peer)
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
    logger.info("Server started on port %d", PORT)
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
#  Design tokens (matching Stitch screenshot)
# ═══════════════════════════════════════════════

BG = "#000000"
CARD_BG = "#0d0d10"
CARD_BORDER = "#1a1a20"
TEXT_WHITE = "#e8e8ec"
TEXT_GRAY = "#8a8a94"
TEXT_DIM = "#555560"
ACCENT = "#7b5ae7"
ACCENT_LT = "#9b7dff"
GREEN = "#34d399"
YELLOW = "#fbbf24"
RED = "#f87171"

W, H = 460, 580


class VexraApp:
    """Stitch-designed receiver UI — faithful to screenshot."""

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

        # ── Header ──
        tk.Frame(r, bg=BG, height=28).pack()
        tk.Label(
            r, text="V E X R A",
            font=("Segoe UI", 20, "bold"), fg=TEXT_WHITE, bg=BG,
        ).pack()
        tk.Label(
            r, text="Your phone. Your trackpad. No wires.",
            font=("Segoe UI", 9), fg=TEXT_DIM, bg=BG,
        ).pack(pady=(4, 0))

        # ── Status pill ──
        pf = tk.Frame(r, bg=BG)
        pf.pack(pady=(12, 0))
        pill = tk.Frame(pf, bg=CARD_BG, highlightbackground=CARD_BORDER,
                        highlightthickness=1, padx=12, pady=4)
        pill.pack()
        self._sdot = tk.Label(pill, text="●", font=("Segoe UI", 8),
                              fg=YELLOW, bg=CARD_BG)
        self._sdot.pack(side="left")
        self._stxt = tk.Label(pill, text="  Starting…", font=("Segoe UI", 9),
                              fg=TEXT_GRAY, bg=CARD_BG)
        self._stxt.pack(side="left")

        # ═══ Connection details card ═══
        card = tk.Frame(r, bg=CARD_BG, highlightbackground=CARD_BORDER,
                        highlightthickness=1)
        card.pack(fill="x", padx=32, pady=(14, 0))

        # -- CONNECTION DETAILS section --
        det = tk.Frame(card, bg=CARD_BG, padx=16, pady=12)
        det.pack(fill="x")

        tk.Label(
            det, text="CONNECTION DETAILS",
            font=("Segoe UI", 8, "bold"), fg=TEXT_DIM, bg=CARD_BG,
        ).pack(anchor="w")

        ips = get_local_ips()
        ip_val = ips[0] if ips else "No network"
        tk.Label(
            det, text=f"IP:  {ip_val}",
            font=("Consolas", 12), fg=TEXT_WHITE, bg=CARD_BG,
        ).pack(anchor="w", pady=(6, 0))

        tk.Label(
            det, text=f"Port:  {PORT}",
            font=("Consolas", 12), fg=TEXT_WHITE, bg=CARD_BG,
        ).pack(anchor="w", pady=(2, 0))

        # -- Divider --
        tk.Frame(card, bg=CARD_BORDER, height=1).pack(fill="x", padx=16)

        # -- CONNECTION PIN section --
        pin_sec = tk.Frame(card, bg=CARD_BG, padx=16, pady=12)
        pin_sec.pack(fill="x")

        pin_hdr = tk.Frame(pin_sec, bg=CARD_BG)
        pin_hdr.pack(fill="x")

        tk.Label(
            pin_hdr, text="CONNECTION PIN",
            font=("Segoe UI", 8, "bold"), fg=TEXT_DIM, bg=CARD_BG,
        ).pack(side="left")

        regen = tk.Label(
            pin_hdr, text="REGENERATE",
            font=("Segoe UI", 8, "bold"), fg=ACCENT, bg=CARD_BG, cursor="hand2",
        )
        regen.pack(side="right")
        regen.bind("<Button-1>", lambda e: self._new_pin())
        regen.bind("<Enter>", lambda e: regen.configure(fg=ACCENT_LT))
        regen.bind("<Leave>", lambda e: regen.configure(fg=ACCENT))

        self._pin = tk.Label(
            pin_sec, text=self._fpin(AUTH_PIN),
            font=("Consolas", 24, "bold"), fg=ACCENT, bg=CARD_BG,
        )
        self._pin.pack(anchor="w", pady=(8, 0))

        # ═══ Activity log card ═══
        log_card = tk.Frame(r, bg=CARD_BG, highlightbackground=CARD_BORDER,
                            highlightthickness=1)
        log_card.pack(padx=32, pady=(12, 0),
                      expand=True, fill="both")

        # Log header
        log_hdr = tk.Frame(log_card, bg=CARD_BG, padx=16, pady=8)
        log_hdr.pack(fill="x")
        tk.Label(
            log_hdr, text="📋  ACTIVITY LOG",
            font=("Segoe UI", 8, "bold"), fg=TEXT_DIM, bg=CARD_BG,
        ).pack(anchor="w")

        # Divider
        tk.Frame(log_card, bg=CARD_BORDER, height=1).pack(fill="x", padx=16)

        # Log text area
        self._log = tk.Text(
            log_card, bg=CARD_BG, fg=TEXT_GRAY,
            font=("Consolas", 9), relief="flat", bd=0,
            padx=16, pady=8, wrap="word",
            state="disabled", cursor="arrow",
            insertbackground=TEXT_GRAY, selectbackground=ACCENT,
            highlightthickness=0,
        )
        self._log.pack(fill="both", expand=True)
        self._log.tag_configure("ok", foreground=GREEN)
        self._log.tag_configure("warn", foreground=YELLOW)
        self._log.tag_configure("err", foreground=RED)
        self._log.tag_configure("dim", foreground=TEXT_GRAY)
        self._log.tag_configure("bold", foreground=TEXT_WHITE,
                                font=("Consolas", 9, "bold"))

        # ── Footer ──
        foot = tk.Frame(r, bg=BG, padx=32, pady=10)
        foot.pack(fill="x")

        tk.Label(foot, text="v1.0.0", font=("Segoe UI", 8),
                 fg=TEXT_DIM, bg=BG).pack(side="left")

        ql = tk.Label(foot, text="QUIT", font=("Segoe UI", 8, "bold"),
                      fg=TEXT_DIM, bg=BG, cursor="hand2")
        ql.pack(side="right")
        ql.bind("<Button-1>", lambda e: self.root.destroy())
        ql.bind("<Enter>", lambda e: ql.configure(fg=RED))
        ql.bind("<Leave>", lambda e: ql.configure(fg=TEXT_DIM))

    # ── Helpers ──

    @staticmethod
    def _fpin(pin: str) -> str:
        return f"{pin[:3]}  {pin[3:]}"

    def _log_msg(self, msg: str) -> None:
        self._log.configure(state="normal")
        tag = "dim"
        if "Authenticated" in msg or "AUTH_OK" in msg:
            tag = "ok"
        elif "Wrong PIN" in msg or "Rejected" in msg or "timeout" in msg:
            tag = "warn"
        elif "error" in msg.lower() or "crash" in msg.lower():
            tag = "err"
        elif "Receiving" in msg or "connected" in msg.lower():
            tag = "bold"
        self._log.insert("end", msg + "\n", tag)
        self._log.see("end")
        self._log.configure(state="disabled")

    def _set_status(self, s: str) -> None:
        m = {
            "connected": (GREEN, "  Connected"),
            "waiting": (YELLOW, "  Waiting"),
            "connecting": (YELLOW, "  Authenticating…"),
            "error": (RED, "  Error"),
        }
        color, label = m.get(s, (TEXT_GRAY, f"  {s}"))
        self._sdot.configure(fg=color)
        self._stxt.configure(text=label)

    def _new_pin(self) -> None:
        global AUTH_PIN
        AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
        self._pin.configure(text=self._fpin(AUTH_PIN))
        ts = datetime.now().strftime("%H:%M:%S")
        self._log_msg(f"[{ts}] New PIN generated")

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


def main() -> None:
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)
    threading.Thread(target=_server_thread, daemon=True).start()
    VexraApp().run()


if __name__ == "__main__":
    main()
