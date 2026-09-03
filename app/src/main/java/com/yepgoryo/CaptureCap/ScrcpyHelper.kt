package com.yepgoryo.CaptureCap

import android.content.Context
import android.util.Log

import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest
import java.security.SecureRandom

class ScrcpyHelper {
    companion object {
        const val TAG = "ScrcpyHelper"

        fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

        fun getScrcpyServerPath(context: Context): String {
            val folder = context.getExternalFilesDir(null) ?: context.externalCacheDir

            if (folder == null) {
                throw IllegalStateException("External app storage is unavailable")
            }

            return "${folder.absolutePath}/scrcpy-${BuildConfig.SCRCPY_SERVER_VERSION}-server.jar"
        }

        private fun extractScrcpyServerFromAssets(context: Context, destFile: File) {
            try {
                Log.d(TAG, "Extracting scrcpy server from assets")
                context.assets.open(BuildConfig.SCRCPY_SERVER_ASSET_NAME).use { input ->
                    destFile.parentFile?.mkdirs()

                    FileOutputStream(destFile).use { output ->
                        val buffer = ByteArray(4096)
                        var bytesRead = input.read(buffer)

                        while (bytesRead > 0) {
                            output.write(buffer, 0, bytesRead)
                            bytesRead = input.read(buffer)
                        }
                    }

                    destFile.setReadable(true, false)
                }
                Log.d(TAG, "Server file extracted")
            } catch (e: Exception) {
                Log.e(TAG, "Asset extraction failed: ${e.message}")
            }
        }

        fun verifyScrcpyServerHash(file: File): Boolean {
            if (!file.exists()) {
                Log.e(TAG, "Scrcpy file not found at ${file.path}, cannot verify")
                return false
            }

            try {
                val digest = MessageDigest.getInstance("SHA-256")

                Log.d(TAG, "Verifying scrcpy")

                file.inputStream().use { inputStream ->
                    val buffer = ByteArray(4096)
                    var bytesRead: Int = inputStream.read(buffer)

                    while (bytesRead != -1) {
                        digest.update(buffer, 0, bytesRead)
                        bytesRead = inputStream.read(buffer)
                    }
                }

                val actualHash = digest.digest().hex()
                val matches = actualHash.equals(BuildConfig.SCRCPY_SERVER_SHA256, true)

                if (!matches) {
                    Log.e(TAG, "Wrong SHA256 hash: $actualHash. Expected: ${BuildConfig.SCRCPY_SERVER_SHA256}")
                } else {
                    Log.d(TAG, "Scrcpy verification success!")
                }

                return matches
            } catch (e: Exception) {
                Log.e(TAG, "Hash verification error: ${e.message}")
                return false
            }
        }

        fun checkScrcpyServerFile(context: Context, serverPath: String): Boolean {
            val file = File(serverPath)
            if (!file.exists()) {
                Log.d(TAG, "Server file absent at $serverPath")
            } else if (!verifyScrcpyServerHash(file)) {
                Log.d(TAG, "Server file has wrong hash value at $serverPath")
            } else {
                return true
            }
            extractScrcpyServerFromAssets(context, file)
            if (!verifyScrcpyServerHash(file)) {
                Log.e(TAG, "The extracted server file seems to be corrupted")
                return false
            }
            return true
        }

        fun buildScrcpyServerArgs(
            socketName: String,
            audioSource: String,
        ): List<String> {
            Log.d(TAG, "Generating config for audio source: \"$audioSource\"")

            val args = mutableListOf(
                BuildConfig.SCRCPY_SERVER_VERSION,
                "log_level=info",
                "video=false",
                "audio=true",
                "control=false",
                "tunnel_forward=false",
                "send_dummy_byte=false",
                "scid=$socketName",
                "audio_source=$audioSource",
                "audio_codec=raw",
                "send_device_meta=false",
                "send_frame_meta=true",
                "send_stream_meta=true",
            )
            return args
        }

        fun getScrcpyRandomSocketName(): String {
            return SecureRandom().nextInt(Int.MAX_VALUE).toString(16).padStart(8, '0')
        }
    }
}