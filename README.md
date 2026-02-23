# 📱 Mobile to Cursor

**Turn your phone into a wireless trackpad & keyboard for your PC.**

A lightweight, open-source remote input system that lets you control your Windows desktop from your Android phone over Wi-Fi. No cloud, no accounts, no bloatware — just a direct TCP connection on your local network.

---

## ✨ Features

### 🖱️ Trackpad
| Gesture | Action |
|---------|--------|
| 1 finger drag | Move cursor |
| 1 finger tap | Left click |
| 1 finger long press | Right click |
| 2 finger scroll | Scroll up/down |
| 2 finger pinch | Zoom in/out (Ctrl+Scroll) |
| 2 finger tap | Right click |
| 3 finger swipe ↑ | Task View (Win+Tab) |
| 3 finger swipe ↓ | Show Desktop (Win+D) |
| 3 finger swipe ←/→ | Switch apps (Alt+Tab) |
| 3 finger tap | Windows Search |
| 4 finger swipe ↑/↓ | Volume Up/Down |
| 4 finger swipe ←/→ | Switch virtual desktops |
| 4 finger tap | Notification Center (Win+N) |

### ⌨️ Keyboard
- Full text input with compose-and-send
- Special keys: Backspace, Enter, Tab, Escape, Arrows, Home/End, PgUp/PgDn
- Modifier keys with LED indicators: Ctrl, Shift, Alt, Win, CapsLock
- Keyboard shortcuts: Ctrl+C, Ctrl+V, Ctrl+Z, Ctrl+A, Ctrl+S, Alt+Tab, Alt+F4
- Function keys: F1–F12
- Media keys: Play/Pause, Next, Previous, Volume

### 🔒 Security
- **PIN authentication** — 6-digit PIN generated on each receiver startup
- **Local network only** — no internet, no cloud, no data leaves your Wi-Fi
- **Auto-reconnect** — saves last IP/PIN and reconnects on app relaunch

---

## 🚀 Quick Start

### Prerequisites
- **Desktop:** Python 3.10+ with `pynput` installed
- **Phone:** Android 8.0+ device on the same Wi-Fi network

### 1. Start the Desktop Receiver

```bash
cd desktop
pip install -r requirements.txt
python receiver.py
```

The receiver will show your local IP and a **6-digit PIN**:

```
====================================================
   Mobile to Cursor — Desktop Receiver
====================================================
   Listening on port 5050

   Enter one of these IPs in the phone app:
     →  192.168.1.100

   🔑 PIN:  482917
   Enter this PIN in the phone app to authenticate.

   Waiting for phone to connect...
====================================================
```

### 2. Build & Install the Android App

1. Open the `android/` folder in **Android Studio**
2. Connect your phone via USB (or use wireless ADB)
3. Click **▶ Run** to build and install
4. Enter the **IP**, **port**, and **PIN** shown by the receiver, then tap **Connect**

### 3. Use It!

- Switch between **Keyboard** and **Trackpad** modes using the bottom toggle
- All gestures mirror Windows precision trackpad behavior

---

## 📁 Project Structure

```
mobile-to-cursor/
├── desktop/                    # Python TCP receiver
│   ├── receiver.py             # Main server — accepts connections, validates PIN
│   ├── protocol.py             # Event type definitions & JSON parsing
│   ├── injector.py             # OS input injection via pynput
│   ├── config.py               # Port, sensitivity settings
│   ├── test_client.py          # CLI test tool
│   └── requirements.txt        # Python dependencies
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
├── .gitignore
├── LICENSE
└── README.md
```

---

## 🔧 Configuration

Edit `desktop/config.py` to customize:

```python
HOST = "0.0.0.0"         # Listen on all interfaces
PORT = 5050               # TCP port
MOUSE_SENSITIVITY = 1.5   # Cursor speed multiplier
SCROLL_SENSITIVITY = 1.0  # Scroll speed multiplier
```

---

## 🛡️ Security Notes

- **PIN Authentication** — A random 6-digit PIN is generated each time the receiver starts. Only phones with the correct PIN can connect.
- **Local network only** — Communication stays on your Wi-Fi, nothing goes to the internet.
- **Unencrypted TCP** — Data is sent as plain text. Only use on trusted networks (home Wi-Fi, not coffee shops).
- **No data stored server-side** — The desktop receiver stores nothing. The Android app saves the last IP/PIN locally via SharedPreferences.

---

## 🤝 Contributing

Contributions are welcome! Feel free to open issues or submit pull requests.

---

## 📄 License

This project is licensed under the **MIT License** — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Made with ❤️ by <a href="https://github.com/kunalsahu20">Kunal Sahu</a>
</p>
