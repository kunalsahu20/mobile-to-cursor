"""
TCP server — the core of the desktop receiver.

Binds to 0.0.0.0:<PORT>, accepts a single phone connection at a time,
reads newline-delimited JSON events, and dispatches them to the injector.

Architecture:
    asyncio event loop → StreamReader (line-by-line) → protocol.parse → injector.dispatch

Why asyncio instead of threading?
    - Single connection, so no need for thread pool
    - Non-blocking reads pair well with the OS input injection (which is fast)
    - Cleaner shutdown handling
"""

import asyncio
import logging
import secrets
import signal
import sys
import socket

from config import HOST, PORT, BUFFER_SIZE
from protocol import parse_event, TYPE_AUTH
from injector import dispatch

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-7s  %(message)s",
    datefmt="%H:%M:%S",
)
logger = logging.getLogger(__name__)

# ── Authentication ─────────────────────────────
# Random 6-digit PIN generated at startup. Changes every restart.
AUTH_PIN = f"{secrets.randbelow(1_000_000):06d}"


def get_local_ips() -> list[str]:
    """Return all local IPv4 addresses (useful for showing the user what to connect to)."""
    ips = []
    try:
        for info in socket.getaddrinfo(socket.gethostname(), None, socket.AF_INET):
            addr = info[4][0]
            if addr not in ips and not addr.startswith("127."):
                ips.append(addr)
    except socket.gaierror:
        pass
    return ips


async def handle_client(
    reader: asyncio.StreamReader,
    writer: asyncio.StreamWriter,
) -> None:
    """Handle a single connected phone — requires PIN auth first."""
    peer = writer.get_extra_info("peername")
    logger.info("📱 Phone connected: %s", peer)

    authenticated = False

    try:
        # ── Step 1: Authenticate ──────────────────
        # First message MUST be AUTH with correct PIN.
        # Wait up to 10 seconds for the auth message.
        try:
            data = await asyncio.wait_for(reader.readline(), timeout=10.0)
        except asyncio.TimeoutError:
            logger.warning("⛔ Auth timeout from %s — disconnecting", peer)
            writer.write(b'{"status":"AUTH_FAIL","reason":"timeout"}\n')
            await writer.drain()
            return

        if not data:
            return

        line = data.decode("utf-8", errors="replace")
        event = parse_event(line)

        if event is None or event.get("type") != "AUTH":
            logger.warning("⛔ First message was not AUTH from %s — disconnecting", peer)
            writer.write(b'{"status":"AUTH_FAIL","reason":"expected_auth"}\n')
            await writer.drain()
            return

        if event.get("pin") != AUTH_PIN:
            logger.warning("⛔ Wrong PIN from %s — disconnecting", peer)
            writer.write(b'{"status":"AUTH_FAIL","reason":"wrong_pin"}\n')
            await writer.drain()
            return

        # Auth passed!
        authenticated = True
        writer.write(b'{"status":"AUTH_OK"}\n')
        await writer.drain()
        logger.info("✅ Phone authenticated: %s", peer)

        # ── Step 2: Normal event loop ─────────────
        while True:
            data = await reader.readline()
            if not data:
                break

            line = data.decode("utf-8", errors="replace")
            event = parse_event(line)
            if event is None:
                continue

            # Skip stray AUTH messages after initial auth
            if event.get("type") == "AUTH":
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
        logger.info("📱 Phone disconnected (%s): %s", status, peer)


async def run_server() -> None:
    """Start the TCP server and wait for connections."""
    server = await asyncio.start_server(
        handle_client,
        host=HOST,
        port=PORT,
    )

    local_ips = get_local_ips()

    print()
    print("=" * 52)
    print("   Mobile to Cursor — Desktop Receiver")
    print("=" * 52)
    print(f"   Listening on port {PORT}")
    print()
    if local_ips:
        print("   Enter one of these IPs in the phone app:")
        for ip in local_ips:
            print(f"     →  {ip}")
    else:
        print("   Could not detect local IPs. Check ipconfig.")
    print()
    print(f"   🔑 PIN:  {AUTH_PIN}")
    print("   Enter this PIN in the phone app to authenticate.")
    print()
    print("   Waiting for phone to connect...")
    print("=" * 52)
    print()

    try:
        async with server:
            await server.serve_forever()
    except asyncio.CancelledError:
        pass
    finally:
        server.close()
        await server.wait_closed()


def main() -> None:
    """
    Entry point — start the async server with instant Ctrl+C shutdown.

    Uses the default ProactorEventLoop on Windows (responds to Ctrl+C
    immediately) but suppresses the noisy cleanup errors on exit.
    """
    import warnings
    warnings.filterwarnings("ignore", category=ResourceWarning)

    try:
        asyncio.run(run_server())
    except KeyboardInterrupt:
        pass
    except SystemExit:
        pass

    # Final clean message
    print()
    logger.info("Receiver stopped.")


if __name__ == "__main__":
    main()

