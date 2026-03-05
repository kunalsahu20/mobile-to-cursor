╔══════════════════════════════════════════════════╗
║             VEXRA DESKTOP RECEIVER               ║
║          Setup Guide (.zip Version)               ║
╚══════════════════════════════════════════════════╝

Website:  https://vexra.happie.in
GitHub:   https://github.com/kunalsahu20/mobile-to-cursor
Discord:  https://discord.gg/TVFkDKVxR6

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

PREREQUISITES
─────────────
  • Python 3.8 or later
    Download: https://www.python.org/downloads/
    IMPORTANT: Check "Add Python to PATH" during installation!

  • Both your phone and PC must be on the SAME Wi-Fi network.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

QUICK START (Easiest Method)
────────────────────────────
  1. Extract this zip to any folder.
  2. Double-click "Start Vexra.bat"
     → It will auto-install dependencies and launch the receiver.
  3. Note the IP address and PIN shown in the Vexra window.
  4. Open the Vexra app on your phone.
  5. Enter the IP address and PIN → Tap Connect.
  6. Done! Your phone is now a wireless trackpad & keyboard.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

MANUAL START (If .bat doesn't work)
───────────────────────────────────
  1. Open Command Prompt or PowerShell in this folder.
  2. Run:  pip install pynput
  3. Run:  python receiver.py
  4. Follow steps 3-6 above.

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

FILES IN THIS ZIP
─────────────────
  Start Vexra.bat  → One-click launcher (recommended)
  receiver.py      → Main desktop receiver application
  config.py        → Network configuration (host, port)
  protocol.py      → Message protocol handler
  injector.py      → Mouse & keyboard input injector
  requirements.txt → Python dependencies list
  vexra.ico        → Application icon
  README.txt       → This file

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

TROUBLESHOOTING
───────────────
  Q: "Python is not recognized" error?
  A: Reinstall Python and check "Add Python to PATH".

  Q: Can't connect from phone?
  A: Make sure both devices are on the same Wi-Fi.
     Check your firewall — allow Python through it.

  Q: Antivirus blocks it?
  A: This is a false positive from pynput (keyboard/mouse
     library). The full source code is open on GitHub.

  Q: Connection drops randomly?
  A: Try disabling VPN or switching Wi-Fi bands (2.4/5 GHz).

━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━

Made with love by Kunal Sahu
https://github.com/kunalsahu20
