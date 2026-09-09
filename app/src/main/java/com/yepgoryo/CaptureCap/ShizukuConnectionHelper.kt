package com.yepgoryo.CaptureCap

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi

import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuProvider

@RequiresApi(Build.VERSION_CODES.R)
class ShizukuConnectionHelper() {

    companion object {
        private const val TAG = "ShizukuConnectionHelper"

        private const val SHIZUKU_START = "moe.shizuku.privileged.api.START"
        private const val SHIZUKU_STOP = "moe.shizuku.privileged.api.STOP"

        fun shizukuAvailable(): Boolean {
            try {
                return Shizuku.pingBinder()
            } catch (e: Exception) {
                Log.w(TAG, "Shizuku unavailable: ${e.message}")
                return false
            }
        }

        fun hasShizukuPermission(context: Context): Boolean {
            try {
                if (Shizuku.isPreV11()) {
                    Toast.makeText(context, R.string.shizuku_unsupported, Toast.LENGTH_LONG).show()
                    return false
                }

                if (shizukuAvailable()) {
                    return Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
                } else {
                    return context.checkSelfPermission(ShizukuProvider.PERMISSION) == PackageManager.PERMISSION_GRANTED
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error while checking Shizuku permission")
                throw e
            }
        }

        fun getShizukuPackageName(context: Context): String? {
            return context.packageManager
                .getPermissionInfo(ShizukuProvider.PERMISSION, 0).packageName
        }

        fun startShizuku(context: Context, authKey: String) {
            try {
                if (shizukuAvailable()) {
                    Log.d(TAG, "Shizuku is already running")
                    return
                }

                val packageName = getShizukuPackageName(context)
                    ?: throw IllegalStateException("Shizuku manager package not found, cannot start")

                val intent = Intent(SHIZUKU_START)
                intent.setPackage(packageName)
                intent.putExtra("auth", authKey)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

                context.sendBroadcast(intent)
                Log.d(TAG, "Sent broadcast to start Shizuku to $packageName")
            } catch (_: Exception) {
                Log.e(TAG, "Failed to send broadcast to start Shizuku")
            }
        }

        fun stopShizuku(context: Context, authKey: String) {
            try {
                if (!shizukuAvailable()) {
                    Log.d(TAG, "Shizuku is already stopped")
                    return
                }

                val packageName = getShizukuPackageName(context)
                    ?: throw IllegalStateException("Shizuku manager package not found, cannot stop")

                val intent = Intent(SHIZUKU_STOP)
                intent.setPackage(packageName)
                intent.putExtra("auth", authKey)
                intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES)

                context.sendBroadcast(intent)
                Log.d(TAG, "Sent broadcast to stop Shizuku to $packageName")
            } catch (_: Exception) {
                Log.e(TAG, "Failed to send broadcast to stop Shizuku")
            }
        }
    }
}