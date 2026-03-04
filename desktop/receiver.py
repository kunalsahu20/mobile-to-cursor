"""
Vexra — Desktop Receiver (GUI)

Premium windowed TCP server with Stitch-designed UI + glow orb effects.
Consistent dark aesthetic matching the Vexra landing page.
Runs asyncio in a background thread, updates tkinter via queue.
"""

import asyncio
import logging
import math
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
#  TCP Server (background thread)
# ═══════════════════════════════════════════════

async def handle_client(reader, writer) -> None:
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
            tag = "authenticated" if authenticated else "unauthenticated"
            logger.info("📱  Disconnected (%s)", tag)
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
#  Design tokens (Stitch: #7b5ae7, dark, Inter)
# ═══════════════════════════════════════════════

BG = "#000000"
SURFACE_1 = "#0a0a0c"
SURFACE_2 = "#111114"
BORDER = "#1f1f24"
TEXT = "#f5f5f7"
TEXT_SEC = "#a1a1a8"
TEXT_MUT = "#6b6b74"
ACCENT = "#7b5ae7"
ACCENT_H = "#9478f0"
ACCENT_D = "#4a3590"
GREEN = "#34d399"
YELLOW = "#fbbf24"
RED = "#f87171"

W, H = 480, 660


# ── Glow orb helper ───────────────────────────

def _draw_glow(canvas: tk.Canvas, cx: int, cy: int, r: int,
               color_rgb: tuple, layers: int = 12) -> list:
    """Draw soft radial glow using concentric ovals with decreasing opacity."""
    items = []
    base_r, base_g, base_b = color_rgb
    for i in range(layers, 0, -1):
        frac = i / layers
        radius = int(r * frac)
        # Blend toward black (#000) as we go outward
        blend = frac ** 0.5
        cr = int(base_r * blend)
        cg = int(base_g * blend)
        cb = int(base_b * blend)
        color = f"#{cr:02x}{cg:02x}{cb:02x}"
        item = canvas.create_oval(
            cx - radius, cy - radius, cx + radius, cy + radius,
            fill=color, outline="",
        )
        items.append(item)
    return items


class VexraApp:
    """Premium receiver with landing-page glow effects."""

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

        self._anim_step = 0
        self._glow_items = []
        self._build()
        self._poll()
        self._animate_glow()

    def _build(self) -> None:
        # ── Background canvas with glow orbs ──
        self._bg_canvas = tk.Canvas(
            self.root, width=W, height=H,
            bg=BG, highlightthickness=0, bd=0,
        )
        self._bg_canvas.place(x=0, y=0, width=W, height=H)

        # Draw glow orbs (matching landing page positions)
        # Top-right: purple glow
        self._glow_tr = _draw_glow(
            self._bg_canvas, 420, 60, 220, (123, 90, 231), 15
        )
        # Bottom-left: pink/purple glow
        self._glow_bl = _draw_glow(
            self._bg_canvas, 40, 580, 200, (180, 60, 180), 12
        )
        # Center: subtle blue glow
        self._glow_c = _draw_glow(
            self._bg_canvas, 240, 320, 280, (60, 50, 140), 10
        )

        self._glow_items = [
            (self._glow_tr, 420, 60, 220, (123, 90, 231), 15),
            (self._glow_bl, 40, 580, 200, (180, 60, 180), 12),
            (self._glow_c, 240, 320, 280, (60, 50, 140), 10),
        ]

        # ── Top accent bar ──
        self._bg_canvas.create_rectangle(0, 0, W, 3, fill="#080810", outline="")
        self._bg_canvas.create_rectangle(40, 0, 440, 3, fill=ACCENT_D, outline="")
        self._bg_canvas.create_rectangle(120, 0, 360, 3, fill=ACCENT, outline="")

        # ── Main UI container (sits on top of canvas) ──
        main = tk.Frame(self.root, bg="")
        main.place(x=0, y=0, width=W, height=H)
        # Make frame transparent-ish by matching BG
        main.configure(bg=BG)

        # We use a simple approach: transparent-bg labels/frames on top
        # Since tkinter doesn't support true transparency, we use BG color
        # The glow effect shows through the dark background

        # ── Header ──
        hdr = tk.Frame(main, bg=BG)
        hdr.pack(fill="x", pady=(36, 0))

        tk.Label(
            hdr, text="V E X R A",
            font=("Segoe UI", 28, "bold"), fg=TEXT, bg=BG,
        ).pack()

        tk.Label(
            hdr, text="Your phone. Your trackpad. No wires.",
            font=("Segoe UI", 10), fg=TEXT_MUT, bg=BG,
        ).pack(pady=(6, 0))

        # ── Status pill ──
        pw = tk.Frame(main, bg=BG)
        pw.pack(pady=(20, 0))

        pill = tk.Frame(
            pw, bg=SURFACE_2,
            highlightbackground=BORDER, highlightthickness=1,
            padx=14, pady=5,
        )
        pill.pack()

        self._sdot = tk.Label(
            pill, text="●", font=("Segoe UI", 9), fg=YELLOW, bg=SURFACE_2,
        )
        self._sdot.pack(side="left")

        self._stxt = tk.Label(
            pill, text="  Starting…",
            font=("Segoe UI", 10), fg=TEXT_SEC, bg=SURFACE_2,
        )
        self._stxt.pack(side="left")

        # ── Connection card ──
        card = tk.Frame(
            main, bg=SURFACE_2,
            highlightbackground=BORDER, highlightthickness=1,
        )
        card.pack(fill="x", padx=28, pady=(20, 0))

        # Top: IP + Port
        top = tk.Frame(card, bg=SURFACE_2, padx=20, pady=16)
        top.pack(fill="x")

        lf = tk.Frame(top, bg=SURFACE_2)
        lf.pack(side="left", anchor="nw")
        tk.Label(lf, text="IP ADDRESS", font=("Segoe UI", 8, "bold"),
                 fg=TEXT_MUT, bg=SURFACE_2).pack(anchor="w")
        ips = get_local_ips()
        ip = ips[0] if ips else "No network"
        tk.Label(lf, text=ip, font=("Segoe UI", 13),
                 fg=TEXT, bg=SURFACE_2).pack(anchor="w", pady=(4, 0))

        rf = tk.Frame(top, bg=SURFACE_2)
        rf.pack(side="right", anchor="ne")
        tk.Label(rf, text="PORT", font=("Segoe UI", 8, "bold"),
                 fg=TEXT_MUT, bg=SURFACE_2).pack(anchor="e")
        tk.Label(rf, text=str(PORT), font=("Segoe UI", 13),
                 fg=TEXT, bg=SURFACE_2).pack(anchor="e", pady=(4, 0))

        # Divider
        tk.Frame(card, bg=BORDER, height=1).pack(fill="x", padx=20)

        # Bottom: PIN
        bot = tk.Frame(card, bg=SURFACE_2, padx=20, pady=16)
        bot.pack(fill="x")
        tk.Label(bot, text="CONNECTION PIN", font=("Segoe UI", 8, "bold"),
                 fg=TEXT_MUT, bg=SURFACE_2).pack(anchor="w")

        pr = tk.Frame(bot, bg=SURFACE_2)
        pr.pack(fill="x", pady=(6, 0))

        self._pin = tk.Label(
            pr, text=self._fpin(AUTH_PIN),
            font=("Consolas", 26, "bold"), fg=ACCENT, bg=SURFACE_2,
        )
        self._pin.pack(side="left")

        regen = tk.Label(
            pr, text="↻  Regenerate",
            font=("Segoe UI", 9), fg=TEXT_MUT, bg=SURFACE_2, cursor="hand2",
        )
        regen.pack(side="right", pady=(10, 0))
        regen.bind("<Button-1>", lambda e: self._new_pin())
        regen.bind("<Enter>", lambda e: regen.configure(fg=ACCENT_H))
        regen.bind("<Leave>", lambda e: regen.configure(fg=TEXT_MUT))

        # ── Activity log ──
        lw = tk.Frame(main, bg=BG)
        lw.pack(fill="both", expand=True, padx=28, pady=(16, 0))

        tk.Label(lw, text="ACTIVITY", font=("Segoe UI", 8, "bold"),
                 fg=TEXT_MUT, bg=BG).pack(anchor="w", pady=(0, 6))

        lb = tk.Frame(
            lw, bg=SURFACE_1,
            highlightbackground=BORDER, highlightthickness=1,
        )
        lb.pack(fill="both", expand=True)

        self._log = tk.Text(
            lb, bg=SURFACE_1, fg=TEXT_MUT,
            font=("Consolas", 9), relief="flat", bd=0,
            padx=14, pady=12, wrap="word",
            state="disabled", cursor="arrow",
            insertbackground=TEXT_MUT, selectbackground=ACCENT_D,
            highlightthickness=0,
        )
        self._log.pack(fill="both", expand=True)
        self._log.tag_configure("ok", foreground=GREEN)
        self._log.tag_configure("warn", foreground=YELLOW)
        self._log.tag_configure("err", foreground=RED)
        self._log.tag_configure("dim", foreground=TEXT_MUT)

        # ── Bottom bar ──
        foot = tk.Frame(main, bg=BG, padx=28, pady=12)
        foot.pack(fill="x")

        tk.Label(foot, text="v1.0.0", font=("Segoe UI", 8),
                 fg="#2a2a30", bg=BG).pack(side="left")

        ql = tk.Label(
            foot, text="✕  Quit", font=("Segoe UI", 9, "bold"),
            fg=TEXT_MUT, bg=BG, cursor="hand2",
        )
        ql.pack(side="right")
        ql.bind("<Button-1>", lambda e: self.root.destroy())
        ql.bind("<Enter>", lambda e: ql.configure(fg=RED))
        ql.bind("<Leave>", lambda e: ql.configure(fg=TEXT_MUT))

        # ── Bottom accent bar ──
        ba = tk.Canvas(main, width=W, height=2, bg=BG, highlightthickness=0)
        ba.pack(fill="x", side="bottom")
        ba.create_rectangle(0, 0, W, 2, fill="#080810", outline="")
        ba.create_rectangle(60, 0, 420, 2, fill=ACCENT_D, outline="")
        ba.create_rectangle(160, 0, 320, 2, fill=ACCENT, outline="")

    # ── Glow animation ──

    def _animate_glow(self) -> None:
        """Subtle breathing animation for glow orbs."""
        self._anim_step += 1
        t = self._anim_step * 0.03  # slow pace

        for items, cx, cy, base_r, color_rgb, layers in self._glow_items:
            # Gentle scale oscillation: between 0.85 and 1.15
            scale = 1.0 + 0.15 * math.sin(t + cx * 0.01)
            br, bg_c, bb = color_rgb

            for i, item in enumerate(items):
                frac = (layers - i) / layers
                radius = int(base_r * frac * scale)
                blend = frac ** 0.5
                cr = max(0, min(255, int(br * blend)))
                cg = max(0, min(255, int(bg_c * blend)))
                cb = max(0, min(255, int(bb * blend)))
                color = f"#{cr:02x}{cg:02x}{cb:02x}"
                self._bg_canvas.coords(
                    item,
                    cx - radius, cy - radius, cx + radius, cy + radius,
                )
                self._bg_canvas.itemconfigure(item, fill=color)

        # 30ms ≈ ~33fps for smooth animation
        self.root.after(30, self._animate_glow)

    # ── Helpers ──

    @staticmethod
    def _fpin(pin: str) -> str:
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
        color, label = m.get(s, (TEXT_MUT, f"  {s}"))
        self._sdot.configure(fg=color)
        self._stxt.configure(
            text=label, fg=color if s == "connected" else TEXT_SEC,
        )

    def _new_pin(self) -> None:
        global AUTH_PIN
        AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"
        self._pin.configure(text=self._fpin(AUTH_PIN))
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


def main() -> None:
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)
    threading.Thread(target=_server_thread, daemon=True).start()
    VexraApp().run()


if __name__ == "__main__":
    main()
