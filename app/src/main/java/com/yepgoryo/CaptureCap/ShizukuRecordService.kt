package com.yepgoryo.CaptureCap

import android.content.Context
import android.net.LocalServerSocket
import android.net.LocalSocket
import android.os.ParcelFileDescriptor
import android.util.Log
import androidx.annotation.Keep

import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

import kotlinx.coroutines.*
import kotlin.system.exitProcess

@Keep
class ShizukuRecordService : IShizukuRecordService.Stub {
    private val recordingActive = AtomicBoolean(false)
    private var recordScope: CoroutineScope? = null
    private var audioWritePipe: ParcelFileDescriptor? = null
    private var audioPipeRelayJob: Job? = null
    private var serverSocket: LocalServerSocket? = null
    private var clientConnection: LocalSocket? = null
    private var scrcpyProcess: Process? = null

    private companion object {
        private const val TAG = "ShizukuRecordService"

        const val SERVER_MAIN_CLASS = "com.genymobile.scrcpy.Server"
        const val SERVER_SOCKET_NAME_PREFIX = "scrcpy_"
    }

    @Keep
    constructor() : this(null)

    @Keep
    constructor(context: Context?) {
        Log.d(TAG, "RecordService is running with uid=${android.os.Process.myUid()}")
    }

    private fun startProcessMonitor(process: Process) {
        recordScope?.launch(Dispatchers.IO) {
            try {
                val exitCode = process.waitFor()
                if (exitCode != 0 && recordingActive.get()) {
                    Log.e("${TAG}ProcessMonitor", "Scrcpy crashed with code: $exitCode")
                    stopRecording()
                } else {
                    Log.d("${TAG}ProcessMonitor", "Scrcpy finished with code: $exitCode")
                }
            } catch (_: InterruptedException) {
                Log.d("${TAG}ProcessMonitor", "Scrcpy was interrupted")
            } finally {
                Log.d("${TAG}ProcessMonitor", "Scrcpy finished")
            }
        }
    }

    private fun startAudioRelay() {
        audioPipeRelayJob = recordScope?.launch(Dispatchers.IO) {
            try {
                Log.d("${TAG}AudioRelay", "Waiting for scrcpy connection")

                val connection = serverSocket?.accept()

                if (connection == null) {
                    Log.e("${TAG}AudioRelay", "Scrcpy server socket is null or closed")
                    throw RuntimeException("Scrcpy server socket is null")
                }

                clientConnection = connection

                Log.d("${TAG}AudioRelay", "Scrcpy is connected to the socket")

                val buffer = ByteArray(AudioPlaybackRecorder.AUDIO_BUFFER_SHORTS_STEREO_SIZE * 2)

                ParcelFileDescriptor.AutoCloseOutputStream(audioWritePipe).use { output ->
                    while (isActive) {
                        val bytesRead = connection.inputStream.read(buffer)

                        if (bytesRead == -1) {
                            Log.d("${TAG}AudioRelay", "EOF: Scrcpy disconnected")
                            break
                        }

                        output.write(buffer, 0, bytesRead)
                    }
                }
            } catch (e: IOException) {
                if (recordingActive.get()) {
                    Log.e("${TAG}AudioRelay", "IO error: ${e.message}")
                } else {
                    Log.d("${TAG}AudioRelay", "IO error during shutdown: ${e.message}")
                }
            } finally {
                Log.d("${TAG}AudioRelay", "Audio relay finished")
                stopRecording()
            }
        }
    }

    override fun startRecording(
        audioSource: String,
        serverPath: String,
    ): ParcelFileDescriptor? {
        if (recordingActive.get()) {
            Log.e(TAG, "Rejected starting the recording. A session is already active.")
            return null
        }

        try {
            Log.d(TAG, "Starting the recording")

            val serverJarFile = File(serverPath)
            if (!serverJarFile.exists()) {
                Log.e(TAG, "Server file not found at path: $serverPath")
                return null
            }

            if (!ScrcpyHelper.verifyScrcpyServerHash(serverJarFile)) {
                Log.e(TAG, "Scrcpy hash mismatch at path: $serverPath")
                return null
            }

            Log.d(TAG, "Verified the scrcpy file")

            val pipe = ParcelFileDescriptor.createPipe()

            val audioReadPipe = pipe[0]
            audioWritePipe = pipe[1]

            val socketName = ScrcpyHelper.getScrcpyRandomSocketName()

            val serverFullSocketName = SERVER_SOCKET_NAME_PREFIX + socketName
            serverSocket = LocalServerSocket(serverFullSocketName)

            recordScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

            startAudioRelay()

            val scrcpyServerArgs = ScrcpyHelper.buildScrcpyServerArgs(socketName, audioSource)
            val scrcpyLaunchCommand = mutableListOf("app_process", "/", SERVER_MAIN_CLASS)
            scrcpyLaunchCommand.addAll(scrcpyServerArgs)

            val scrcpyBuilder = ProcessBuilder(scrcpyLaunchCommand)
            scrcpyBuilder.environment()["CLASSPATH"] = serverPath
            scrcpyBuilder.redirectErrorStream(true)

            scrcpyProcess = scrcpyBuilder.start()

            recordingActive.set(true)

            Log.d(TAG, "Scrcpy launched successfully")

            startProcessMonitor(scrcpyProcess!!)

            Log.d(TAG, "Recording pipe established")

            return audioReadPipe
        } catch (e: Exception) {
            Log.e(TAG, "Pipeline startup failed. Error: ${e.message}")
            stopRecording()
            return null
        }
    }

    override fun stopRecording() {
        if (!recordingActive.compareAndSet(true, false)) {
            Log.e(TAG, "Recording already stopped")
            return
        }

        Log.d(TAG, "Stopping scrcpy process")
        scrcpyProcess?.destroy()

        try {
            scrcpyProcess?.waitFor(2L, TimeUnit.SECONDS)
        } catch (e: InterruptedException) {
            Log.e(TAG, "Interrupted while waiting for scrcpy exit: ${e.message}")
        }

        Log.d(TAG, "Waiting for audio relay to finish")

        runBlocking {
            withTimeoutOrNull(2000L) {
                audioPipeRelayJob?.join()
            }
        }

        recordScope?.cancel()
        clientConnection?.close()
        serverSocket?.close()
        audioWritePipe?.close()

        recordScope = null
        clientConnection = null
        serverSocket = null
        audioWritePipe = null
        audioPipeRelayJob = null
        scrcpyProcess = null

        Log.d(TAG, "Recording stopped and released")
    }

    override fun destroy() {
        Log.d(TAG, "Destroying the RecordService process")
        stopRecording()
        exitProcess(0)
    }
}