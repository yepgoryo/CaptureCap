package com.yepgoryo.CaptureCap

import android.content.ClipData
import android.content.ClipboardManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import java.io.InputStream

class DonateScreen : AppCompatActivity() {
    private var appSettings: GlobalProperties? = null

    private fun copyWallet(wallet: String) {
        val clipboardManager = getSystemService(CLIPBOARD_SERVICE) as ClipboardManager

        clipboardManager.setPrimaryClip(ClipData.newPlainText   ("", wallet))

        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
            Toast.makeText(baseContext, "Wallet Copied", Toast.LENGTH_SHORT).show()
        }
    }

    public override fun onCreate(bundle: Bundle?) {
        val globalProperties = GlobalProperties(baseContext)
        this.appSettings = globalProperties

        super.onCreate(bundle)
        setContentView(R.layout.donate)

        val bitcoinText: EditText = findViewById(R.id.bitcoin_wallet)
        val copyBitcoinText: Button = findViewById(R.id.copy_bitcoin_wallet)

        val bitcoinInputStream: InputStream = applicationContext.assets.open("bitcoin.txt")
        val bitcoinBytes = ByteArray(bitcoinInputStream.available())
        bitcoinInputStream.read(bitcoinBytes)
        bitcoinText.setText(String(bitcoinBytes))
        bitcoinInputStream.close()

        copyBitcoinText.setOnClickListener {
            copyWallet(String(bitcoinBytes))
        }

        val ethereumText: EditText = findViewById(R.id.ethereum_wallet)
        val copyEthereumText: Button = findViewById(R.id.copy_ethereum_wallet)

        val ethereumInputStream: InputStream = applicationContext.assets.open("ethereum.txt")
        val ethereumBytes = ByteArray(ethereumInputStream.available())
        ethereumInputStream.read(ethereumBytes)
        ethereumText.setText(String(ethereumBytes))
        ethereumInputStream.close()

        copyEthereumText.setOnClickListener {
            copyWallet(String(ethereumBytes))
        }

        val moneroText: EditText = findViewById(R.id.monero_wallet)
        val copyMoneroText: Button = findViewById(R.id.copy_monero_wallet)

        val moneroInputStream: InputStream = applicationContext.assets.open("monero.txt")
        val moneroBytes = ByteArray(moneroInputStream.available())
        moneroInputStream.read(moneroBytes)
        moneroText.setText(String(moneroBytes))
        moneroInputStream.close()

        copyMoneroText.setOnClickListener {
            copyWallet(String(moneroBytes))
        }

        var statusBarHeight = 0
        val resourceId = getResources().getIdentifier("status_bar_height", "dimen", "android")
        if (resourceId > 0) {
            statusBarHeight = getResources().getDimensionPixelSize(resourceId)
        }

        val statusbarlayout = findViewById<LinearLayout?>(R.id.statusbar)

        val statusbarlayoutparams: LinearLayout.LayoutParams = statusbarlayout!!.getLayoutParams() as LinearLayout.LayoutParams
        statusbarlayoutparams.height = statusBarHeight
        statusbarlayout.setLayoutParams(statusbarlayoutparams)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.mainscroll)) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars()
            )
            v.updatePadding(
                left = bars.left,
                top = bars.top-statusBarHeight,
                right = bars.right,
                bottom = bars.bottom,
            )

            WindowInsetsCompat.CONSUMED
        }
    }
}
