package com.vexra.app.network

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedOutputStream
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import org.json.JSONObject

/**
 * Persistent TCP client that connects to the desktop receiver.
 *
 * Design choices for low latency:
 * - Single persistent connection (no reconnect-per-event)
 * - BufferedOutputStream with immediate flush on each event
 * - Socket TCP_NODELAY = true (disables Nagle's algorithm)
 * - All I/O on Dispatchers.IO
 * - Heartbeat every 5 seconds to detect dead connections early
 */
class TcpClient(private val scope: CoroutineScope) {

    companion object {
        private const val TAG = "TcpClient"
        private const val CONNECT_TIMEOUT_MS = 3000
        private const val HEARTBEAT_INTERVAL_MS = 5000L
        private const val RECONNECT_BASE_DELAY_MS = 1000L
        private const val RECONNECT_MAX_DELAY_MS = 10000L
    }

    enum class ConnectionState { DISCONNECTED, CONNECTING, CONNECTED, AUTH_FAILED, RATE_LIMITED }

    private val _state = MutableStateFlow(ConnectionState.DISCONNECTED)
    val state: StateFlow<ConnectionState> = _state.asStateFlow()

    private var socket: Socket? = null
    private var outputStream: BufferedOutputStream? = null
    private var connectJob: Job? = null
    private var heartbeatJob: Job? = null

    private var targetHost: String = ""
    private var targetPort: Int = 5050
    private var authPin: String = ""
    private var shouldReconnect = false

    /** Reason for auth failure (e.g., "wrong_pin", "rate_limited", "busy") */
    private val _authFailReason = MutableStateFlow("")
    val authFailReason: StateFlow<String> = _authFailReason.asStateFlow()

    /** Seconds until rate-limit lockout expires (0 = no lockout) */
    private val _retryAfterSecs = MutableStateFlow(0)
    val retryAfterSecs: StateFlow<Int> = _retryAfterSecs.asStateFlow()

    /**
     * Start connecting to the desktop receiver.
     * Sends PIN auth immediately after TCP connect.
     * Auto-reconnects on disconnect until [disconnect] is called.
     */
    fun connect(host: String, port: Int, pin: String) {
        targetHost = host
        targetPort = port
        authPin = pin
        shouldReconnect = true
        connectJob?.cancel()
        connectJob = scope.launch(Dispatchers.IO) {
            var retryDelay = RECONNECT_BASE_DELAY_MS
            while (isActive && shouldReconnect) {
                _state.value = ConnectionState.CONNECTING
                try {
                    val sock = Socket()
                    sock.tcpNoDelay = true       // Disable Nagle — send immediately
                    sock.keepAlive = true
                    sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)

                    socket = sock
                    outputStream = BufferedOutputStream(sock.getOutputStream())

                    // ── Send PIN auth immediately ──
                    val authMsg = EventProtocol.auth(authPin)
                    outputStream!!.write(authMsg.toByteArray(Charsets.UTF_8))
                    outputStream!!.flush()

                    // Read server response (AUTH_OK or AUTH_FAIL)
                    val input = sock.getInputStream()
                    val responseBuilder = StringBuilder()
                    while (true) {
                        val b = input.read()
                        if (b == -1 || b.toChar() == '\n') break
                        responseBuilder.append(b.toChar())
                    }
                    val response = responseBuilder.toString().trim()

                    if (!response.contains("AUTH_OK")) {
                        Log.w(TAG, "Auth failed: $response")
                        // Parse server response for reason and retry_after
                        try {
                            val json = JSONObject(response)
                            val reason = json.optString("reason", "unknown")
                            val retryAfter = json.optInt("retry_after", 0)
                            _authFailReason.value = reason
                            _retryAfterSecs.value = retryAfter
                            _state.value = if (reason == "rate_limited" || retryAfter > 0) {
                                ConnectionState.RATE_LIMITED
                            } else {
                                ConnectionState.AUTH_FAILED
                            }
                        } catch (e: Exception) {
                            _authFailReason.value = "unknown"
                            _state.value = ConnectionState.AUTH_FAILED
                        }
                        shouldReconnect = false
                        cleanup()
                        return@launch
                    }

                    // Auth passed!
                    _state.value = ConnectionState.CONNECTED
                    retryDelay = RECONNECT_BASE_DELAY_MS
                    Log.i(TAG, "Connected & authenticated to $host:$port")

                    startHeartbeat()

                    // Block until socket is closed (read will return -1)
                    waitForDisconnect(sock)

                } catch (e: IOException) {
                    Log.w(TAG, "Connection failed: ${e.message}")
                } finally {
                    cleanup()
                    if (_state.value != ConnectionState.AUTH_FAILED) {
                        _state.value = ConnectionState.DISCONNECTED
                    }
                }

                if (shouldReconnect && isActive) {
                    Log.i(TAG, "Reconnecting in ${retryDelay}ms...")
                    delay(retryDelay)
                    retryDelay = (retryDelay * 2).coerceAtMost(RECONNECT_MAX_DELAY_MS)
                }
            }
        }
    }

    /** Stop the connection and disable auto-reconnect. */
    fun disconnect() {
        shouldReconnect = false
        heartbeatJob?.cancel()
        connectJob?.cancel()
        cleanup()
        _state.value = ConnectionState.DISCONNECTED
    }

    /**
     * Send a raw event string to the desktop receiver.
     * Runs on IO dispatcher. Returns false if not connected.
     */
    suspend fun send(event: String): Boolean = withContext(Dispatchers.IO) {
        val out = outputStream ?: return@withContext false
        try {
            out.write(event.toByteArray(Charsets.UTF_8))
            out.flush()
            true
        } catch (e: IOException) {
            Log.w(TAG, "Send failed: ${e.message}")
            false
        }
    }

    /** Block until the socket is closed by the other side. */
    private suspend fun waitForDisconnect(sock: Socket) = withContext(Dispatchers.IO) {
        try {
            val input = sock.getInputStream()
            // Read will block; returns -1 when the connection is closed
            while (isActive && input.read() != -1) {
                // Discard any data from server (we don't expect any)
            }
        } catch (_: IOException) {
            // Expected on disconnect
        }
    }

    /** Start sending heartbeats to keep the connection alive. */
    private fun startHeartbeat() {
        heartbeatJob?.cancel()
        heartbeatJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                delay(HEARTBEAT_INTERVAL_MS)
                val success = send(EventProtocol.heartbeat())
                if (!success) break
            }
        }
    }

    private fun cleanup() {
        heartbeatJob?.cancel()
        try { outputStream?.close() } catch (_: IOException) {}
        try { socket?.close() } catch (_: IOException) {}
        outputStream = null
        socket = null
    }
}
