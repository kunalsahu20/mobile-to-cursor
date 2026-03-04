# Vexra

**Turn your phone into a wireless trackpad & keyboard for your PC.**

A lightweight, open-source remote input system that lets you control your Windows desktop from your Android phone over Wi-Fi. No cloud, no accounts, no bloatware — just a direct TCP connection on your local network.

---

## Features

### Trackpad
| Gesture | Action |
|---------|--------|
| 1 finger drag | Move cursor |
| 1 finger tap | Left click |
| 1 finger long press | Right click |
| 2 finger scroll | Scroll up/down |
| 2 finger pinch | Zoom in/out (Ctrl+Scroll) |
| 2 finger tap | Right click |
| 3 finger swipe up | Task View (Win+Tab) |
| 3 finger swipe down | Show Desktop (Win+D) |
| 3 finger swipe left/right | Switch apps (Alt+Tab) |
| 3 finger tap | Windows Search |
| 4 finger swipe up/down | Volume Up/Down |
| 4 finger swipe left/right | Switch virtual desktops |
| 4 finger tap | Notification Center (Win+N) |

### Keyboard
- Full text input with compose-and-send
- Special keys: Backspace, Enter, Tab, Escape, Arrows, Home/End, PgUp/PgDn
- Modifier keys with LED indicators: Ctrl, Shift, Alt, Win, CapsLock
- Keyboard shortcuts: Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A, Ctrl+S, Alt+Tab, Alt+F4
- Function keys: F1–F12
- Media keys: Play/Pause, Next, Previous, Volume

### Security
- **PIN authentication** — A random 6-digit PIN is generated each time the receiver starts. You must enter this PIN in the phone app to connect.
- **Single device limit** — Only one phone can connect at a time.
- **Local network only** — No internet, no cloud, no data leaves your Wi-Fi.
- **Auto-reconnect** — Saves last IP/PIN and reconnects on app relaunch.

---

## Quick Start

### Prerequisites
- **Desktop:** Download `Vexra.exe` from [Releases](https://github.com/kunalsahu20/mobile-to-cursor/releases) — **or** Python 3.10+ with `pynput` installed
- **Phone:** Android 8.0+ device on the same Wi-Fi network

### 1. Start the Desktop Receiver

**Option A — Standalone .exe (recommended):**
Download `Vexra.exe` from the [latest release](https://github.com/kunalsahu20/mobile-to-cursor/releases) and double-click to run.

> ⚠️ **Windows SmartScreen Warning:** Since Vexra is an open-source unsigned executable, Windows may show a "Windows protected your PC" warning. Click **"More info" → "Run anyway"** to proceed. The full source code is available on this repo for verification.

**Option B — Run from source:**
```bash
cd desktop
pip install -r requirements.txt
python receiver.py
```

The terminal will display your local IP address and a 6-digit PIN. You need both to connect from the phone app.

### 2. Build & Install the Android App

1. Open the `android/` folder in **Android Studio**
2. Connect your phone via USB (or use wireless ADB)
3. Click **Run** to build and install
4. Enter the **IP**, **port**, and **PIN** shown in the desktop terminal, then tap **Connect**

### 3. Use It

Switch between **Keyboard** and **Trackpad** modes using the bottom toggle. All trackpad gestures mirror Windows precision trackpad behavior.

---

## Project Structure

```
vexra/
├── desktop/                    # Python TCP receiver
│   ├── receiver.py             # Main server — accepts connections, validates PIN
│   ├── protocol.py             # Event type definitions & JSON parsing
│   ├── injector.py             # OS input injection via pynput
│   ├── config.py               # Port, sensitivity settings
│   ├── test_client.py          # CLI test tool
│   ├── requirements.txt        # Python dependencies
│   └── dist/
│       └── Vexra.exe           # Standalone Windows receiver
│
├── android/                    # Kotlin/Jetpack Compose app
│   └── app/src/main/java/com/mobiletocursor/
│       ├── MainActivity.kt     # Entry point
│       ├── ui/
│       │   ├── MainScreen.kt   # Full UI — keyboard, modifiers, shortcuts
│       │   └── TrackpadView.kt # Multi-touch gesture detection
│       ├── network/
│       │   ├── TcpClient.kt    # TCP connection with auto-reconnect
│       │   └── EventProtocol.kt# JSON event builders
│       └── viewmodel/
│           └── MainViewModel.kt# State management & event dispatch
│
├── landing-page/               # Vexra website
│   ├── index.html              # Dark mode landing page
│   └── white.html              # Light mode landing page
│
├── .gitignore
├── LICENSE
└── README.md
```

---

## Configuration

Edit `desktop/config.py` to customize:

```python
HOST = "0.0.0.0"         # Listen on all interfaces
PORT = 5050               # TCP port
MOUSE_SENSITIVITY = 1.5   # Cursor speed multiplier
SCROLL_SENSITIVITY = 1.0  # Scroll speed multiplier
```

---

## Security Notes

- A random 6-digit PIN is generated each time the receiver starts. Only phones with the correct PIN can connect.
- Only one device can connect at a time. Additional connections are rejected.
- Communication stays on your local Wi-Fi. Nothing goes to the internet.
- Data is sent as unencrypted plain TCP. Only use on trusted networks.
- The desktop receiver stores nothing. The Android app saves the last IP/PIN locally via SharedPreferences.

---

## Contributing

Contributions are welcome. Feel free to open issues or submit pull requests.

---

## License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made by <a href="https://github.com/kunalsahu20">Kunal Sahu</a>
</p>
