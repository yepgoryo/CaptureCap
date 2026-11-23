package com.yepgoryo.CaptureCap

import android.content.Context
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.util.Log
import java.io.FileOutputStream
import java.io.PrintStream
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class Logger(private val type: Any, private val context: Context) {
    private var properties: GlobalProperties? = null

    init {
        properties = GlobalProperties(context)
    }

    val exceptionHandler = Thread.UncaughtExceptionHandler { _, throwable ->
        Log.e(javaClass.name, "Fatal exception!", throwable)
        this@Logger.logError(throwable)
        this@Logger.logMessageFromThread("Exiting...")
        System.exit(2)
    }

    fun setExceptionHandler() {
        if (Thread.getDefaultUncaughtExceptionHandler() != exceptionHandler) {
            Thread.setDefaultUncaughtExceptionHandler(exceptionHandler)
        }
    }

    fun shouldLog(): Boolean {
        if (properties!!.getBooleanProperty(GlobalProperties.PropertiesBoolean.ENABLE_LOGGING, false) && properties!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_LOGS_PATH, "NULL") != "NULL" && properties!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_LOGS_PATH, "NULL").isNotEmpty()) {
            return true
        }
        return false
    }

    fun logError(e: Throwable) {
        if (!shouldLog()) {
            return
        }

        val fileDescriptor = openLogFile()!!

        val fileStream = PrintStream(FileOutputStream(fileDescriptor!!.fileDescriptor))
        e.printStackTrace(fileStream)
        fileStream.flush()
        fileDescriptor.close()
    }

    fun logMessageFromThread(msg: String) {
        Log.d(getParentType(), msg)
        if (!shouldLog()) {
            return
        }

        val fileDescriptor = openLogFile()!!

        val fileStream = FileOutputStream(fileDescriptor!!.fileDescriptor)
        fileStream.write("\n[Thread]: ${msg}\n".toByteArray())
        fileStream.close()
        fileDescriptor.close()
    }

    fun logMessage(msg: String) {
        Log.d(getParentType(), msg)
        if (!shouldLog()) {
            return
        }

        val fileDescriptor = openLogFile()!!

        val fileStream = FileOutputStream(fileDescriptor!!.fileDescriptor)
        fileStream.write("\n[${getParentType()}]: ${msg}\n".toByteArray())
        fileStream.close()
        fileDescriptor.close()
    }

    fun getParentType(): String {
        return type.javaClass.name
    }

    fun openLogFile(): ParcelFileDescriptor? {
        var logFileDescriptor: ParcelFileDescriptor? = null
        try {
            logFileDescriptor = context.contentResolver.openFileDescriptor(getLogFilePath(), "wa")
        } catch (e: Exception) {
            Log.e(getParentType(), "File ${getLogFilePath()} not found, or failed opening!", e)
            val path = createLogFile()
            if (getLogFilePath() != path) {
                Log.d(getParentType(), "Files aren't equal! \"${path}\" and \"${getLogFilePath()}\"!")
            }
            logFileDescriptor = context.contentResolver.openFileDescriptor(getLogFilePath(), "wa")
        }

        return logFileDescriptor
    }

    fun createLogFile(): Uri {
        return DocumentsContract.createDocument(
            context.contentResolver,
            getLogParentFilePath(),
            "text/plain",
            getLogFileName()
        )!!
    }

    fun getLogFileName(): String {
        val logDate: String = SimpleDateFormat("yyyyMMdd", Locale.US).format(Calendar.getInstance().getTime())
        val recordingFileName = "Log_${logDate}"

        return recordingFileName
    }

    fun getLogParentFilePath(): Uri {
        var folderPath: String = properties!!.getStringProperty(GlobalProperties.PropertiesString.FOLDER_LOGS_PATH, "")

        val documentPath: String = Regex("^content://[^/]*/tree/").replaceFirst(folderPath, "")
        val documentParentPath: Uri = Uri.parse("$folderPath/document/$documentPath")

        return documentParentPath
    }

    fun getLogFilePath(): Uri {
        val documentParentPath: Uri = getLogParentFilePath()
        val documentFileName: String = getLogFileName()

        return Uri.parse("${documentParentPath}%2F${documentFileName}.txt")
    }
}