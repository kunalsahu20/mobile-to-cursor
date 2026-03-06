package com.vexra.app.network

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub releases for new versions and downloads the APK.
 *
 * Flow: checkForUpdate() → download() → installApk()
 * Uses GitHub REST API (no auth needed for public repos).
 */
class UpdateChecker(private val context: Context) {

    data class UpdateInfo(
        val latestVersion: String = "",
        val downloadUrl: String = "",
        val releaseNotes: String = "",
    )

    enum class UpdateState {
        IDLE, CHECKING, UPDATE_AVAILABLE, NO_UPDATE, DOWNLOADING, READY_TO_INSTALL, ERROR
    }

    data class UpdateUiState(
        val state: UpdateState = UpdateState.IDLE,
        val info: UpdateInfo = UpdateInfo(),
        val progress: Int = 0,
        val errorMessage: String = "",
    )

    private val _state = MutableStateFlow(UpdateUiState())
    val state: StateFlow<UpdateUiState> = _state.asStateFlow()

    companion object {
        private const val GITHUB_API =
            "https://api.github.com/repos/kunalsahu20/mobile-to-cursor/releases/latest"
        const val CURRENT_VERSION = "1.0.8"
    }

    /**
     * Hit the GitHub API to check if a newer version exists.
     * Compares tag_name (stripped of "v" prefix) with CURRENT_VERSION.
     */
    suspend fun checkForUpdate() {
        _state.value = UpdateUiState(state = UpdateState.CHECKING)

        try {
            val result = withContext(Dispatchers.IO) {
                val conn = URL(GITHUB_API).openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json")
                conn.connectTimeout = 10_000
                conn.readTimeout = 10_000

                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()

                val json = JSONObject(body)
                val tag = json.getString("tag_name").removePrefix("v")
                val notes = json.optString("body", "")

                // Find the .apk asset
                val assets = json.getJSONArray("assets")
                var apkUrl = ""
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.getString("name").endsWith(".apk")) {
                        apkUrl = asset.getString("browser_download_url")
                        break
                    }
                }

                UpdateInfo(latestVersion = tag, downloadUrl = apkUrl, releaseNotes = notes)
            }

            val hasUpdate = isNewer(result.latestVersion, CURRENT_VERSION)
            _state.value = UpdateUiState(
                state = if (hasUpdate) UpdateState.UPDATE_AVAILABLE else UpdateState.NO_UPDATE,
                info = result,
            )
        } catch (e: Exception) {
            _state.value = UpdateUiState(
                state = UpdateState.ERROR,
                errorMessage = e.message ?: "Failed to check for updates",
            )
        }
    }

    /**
     * Download the APK to the app's cache directory.
     * Updates progress (0-100) in the state flow.
     */
    suspend fun downloadAndInstall() {
        val url = _state.value.info.downloadUrl
        if (url.isBlank()) {
            _state.value = _state.value.copy(
                state = UpdateState.ERROR,
                errorMessage = "No APK download URL found",
            )
            return
        }

        _state.value = _state.value.copy(state = UpdateState.DOWNLOADING, progress = 0)

        try {
            val apkFile = withContext(Dispatchers.IO) {
                val cacheDir = File(context.cacheDir, "updates")
                cacheDir.mkdirs()
                val file = File(cacheDir, "Vexra-update.apk")

                val conn = URL(url).openConnection() as HttpURLConnection
                conn.instanceFollowRedirects = true
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000

                val totalSize = conn.contentLength
                var downloaded = 0

                conn.inputStream.use { input ->
                    file.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var bytesRead: Int
                        while (input.read(buffer).also { bytesRead = it } != -1) {
                            output.write(buffer, 0, bytesRead)
                            downloaded += bytesRead
                            if (totalSize > 0) {
                                val pct = (downloaded * 100 / totalSize).coerceIn(0, 100)
                                _state.value = _state.value.copy(progress = pct)
                            }
                        }
                    }
                }
                conn.disconnect()
                file
            }

            _state.value = _state.value.copy(state = UpdateState.READY_TO_INSTALL)
            installApk(apkFile)
        } catch (e: Exception) {
            _state.value = _state.value.copy(
                state = UpdateState.ERROR,
                errorMessage = "Download failed: ${e.message}",
            )
        }
    }

    /** Trigger Android's package installer via FileProvider URI. */
    private fun installApk(file: File) {
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file,
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** Compare semantic versions (e.g. "1.1.0" > "1.0.0"). */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").mapNotNull { it.toIntOrNull() }
        val l = local.split(".").mapNotNull { it.toIntOrNull() }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv > lv) return true
            if (rv < lv) return false
        }
        return false
    }
}
