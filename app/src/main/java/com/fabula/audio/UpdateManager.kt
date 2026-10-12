package com.fabula.audio

import android.app.Activity
import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class UpdateManager(private val context: Context) {
    companion object {
        private const val TAG = "UpdateManager"
        private const val UPDATE_URL =
            "https://raw.githubusercontent.com/Fabula-Audio/updates/main/version.json"
        private const val APK_FILE_NAME = "fabula_update.apk"
    }

    fun checkForUpdate() {
        Thread {
            try {
                val url = URL(UPDATE_URL)
                val connection = url.openConnection() as HttpURLConnection
                try {
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.requestMethod = "GET"
                    connection.connect()
                    if (connection.responseCode !in 200..299) {
                        throw IllegalStateException("HTTP ${connection.responseCode}")
                    }

                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    val latestVersion = json.getString("latest_version")
                    val downloadUrl = json.getString("download_url")
                    val changelog = json.optString("changelog", "Доступна новая версия")

                    val currentVersion = BuildConfig.VERSION_NAME

                    if (latestVersion != currentVersion) {
                        (context as? Activity)?.runOnUiThread {
                            if (!isFinishingOrDestroyed()) {
                                showUpdateDialog(downloadUrl, changelog, latestVersion)
                            }
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Не удалось проверить обновление: ${e.message}")
                // Молча игнорируем — нет интернета, GitHub недоступен или JSON некорректен.
            }
        }.start()
    }

    private fun isFinishingOrDestroyed(): Boolean {
        val activity = context as? Activity ?: return false
        return activity.isFinishing || (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed)
    }

    private fun showUpdateDialog(downloadUrl: String, changelog: String, newVersion: String) {
        AlertDialog.Builder(context)
            .setTitle("Доступно обновление $newVersion")
            .setMessage(changelog)
            .setPositiveButton("Скачать") { _, _ -> downloadAndInstall(downloadUrl) }
            .setNegativeButton("Позже", null)
            .setCancelable(true)
            .show()
    }

    private fun downloadAndInstall(url: String) {
        try {
            val parsedUrl = Uri.parse(url)
            require(parsedUrl.scheme == "https") { "URL обновления должен использовать HTTPS" }

            val request = DownloadManager.Request(parsedUrl)
                .setTitle("Fabula Audio — обновление")
                .setDescription("Скачивание новой версии")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    APK_FILE_NAME
                )

            val downloadManager =
                context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            var downloadId = -1L
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context?, intent: Intent?) {
                    val id = intent?.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
                    if (id != downloadId) return

                    try {
                        context.unregisterReceiver(this)
                    } catch (e: Exception) {
                        Log.w(TAG, "Не удалось снять receiver: ${e.message}")
                    }

                    val query = DownloadManager.Query().setFilterById(id)
                    val cursor = downloadManager.query(query)
                    val status = if (cursor != null && cursor.moveToFirst()) {
                        cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
                    } else {
                        DownloadManager.STATUS_FAILED
                    }
                    cursor?.close()
                    if (status == DownloadManager.STATUS_SUCCESSFUL) {
                        installApk()
                    } else {
                        Log.w(TAG, "Скачивание обновления завершилось ошибкой: status=$status")
                    }
                }
            }

            val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
            } else {
                @Suppress("DEPRECATION")
                context.registerReceiver(receiver, filter)
            }

            try {
                downloadId = downloadManager.enqueue(request)
            } catch (e: Exception) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {
                }
                throw e
            }
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось запустить скачивание обновления: ${e.message}")
        }
    }

    private fun installApk() {
        val file = File(
            context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS),
            APK_FILE_NAME
        )
        if (!file.exists()) {
            Log.w(TAG, "APK обновления не найден: ${file.absolutePath}")
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
                .setData(Uri.parse("package:${context.packageName}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(settingsIntent)
            return
        }

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "Не удалось открыть APK для установки: ${e.message}")
        }
    }
}
