package com.yepgoryo.CaptureCap

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.content.res.Resources
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import java.io.IOException


@RequiresApi(Build.VERSION_CODES.N)
class QuickTile : TileService() {
    companion object {
        const val ACTION_CONNECT_TILE: String = MainActivity.appName + ".ACTION_CONNECT_TILE"
    }
    private val mConnection: ServiceConnection = object: ServiceConnection {
        override fun onServiceConnected(componentName: ComponentName, iBinder: IBinder) {
            this@QuickTile.recordingTileBinder = iBinder as ScreenRecorder.RecordingTileBinder?
            this@QuickTile.recordingTileBinder?.setConnectTile(TileBinder())
            var quickTile: QuickTile = this@QuickTile
            quickTile.setTileState(quickTile.recordingTileBinder!!.isStarted())
        }

        override fun onServiceDisconnected(componentName: ComponentName) {
            this@QuickTile.recordingTileBinder?.setDisconnectTile()
            this@QuickTile.setTileState(false)
        }
    }
    var mainTile: Tile? = null
    private var recordingTileBinder: ScreenRecorder.RecordingTileBinder? = null

    inner class TileBinder : Binder() {
        fun recordingState(state: Boolean) {
            this@QuickTile.setTileState(state)
        }
    }

    fun setTileState(state: Boolean) {
        if (state) {
            this.mainTile?.state = 2
        } else {
            this.mainTile?.state = 1
        }
        this.mainTile?.updateTile()
    }

    override fun onStartListening() {
        super.onStartListening()
        this.mainTile = qsTile
        setTileState(false)
        val intent = Intent(this, ScreenRecorder::class.java)
        intent.setAction(ACTION_CONNECT_TILE)
        bindService(intent, this.mConnection, Context.BIND_AUTO_CREATE)
    }

    override fun onStopListening() {
        super.onStopListening()
        if (this.recordingTileBinder != null) {
            unbindService(this.mConnection)
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    @Throws(IllegalStateException::class, Resources.NotFoundException::class, IOException::class)
    override fun onClick() {
        super.onClick()
        if (this.recordingTileBinder != null) {
            if (this.recordingTileBinder!!.isStarted()) {
                this.recordingTileBinder?.stopService()
                return
            }
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        intent.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK)
        intent.setAction(MainActivity.ACTION_ACTIVITY_START_RECORDING)
        val pendingIntent = PendingIntent.getActivity(this, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        startActivityAndCollapse(pendingIntent)
    }
}
