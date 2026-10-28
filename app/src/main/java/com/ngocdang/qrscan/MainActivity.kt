package com.ngocdang.qrscan

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val prefs by lazy { getSharedPreferences("cfg", Context.MODE_PRIVATE) }
    private lateinit var etUrl: EditText
    private lateinit var etSheet: EditText
    private lateinit var etCol: EditText
    private lateinit var etRow: EditText
    private lateinit var tvStatus: TextView
    private lateinit var tvLog: TextView
    private lateinit var btnPause: Button
    private val scanner = BarcodeScanning.getClient()
    private val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 90)
    private var busy = false
    private var paused = false
    private var lastCode = ""
    private var lastTime = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        etUrl = findViewById(R.id.etUrl)
        etSheet = findViewById(R.id.etSheet)
        etCol = findViewById(R.id.etCol)
        etRow = findViewById(R.id.etRow)
        tvStatus = findViewById(R.id.tvStatus)
        tvLog = findViewById(R.id.tvLog)
        btnPause = findViewById(R.id.btnPause)

        etUrl.setText(prefs.getString("url", ""))
        etSheet.setText(prefs.getString("sheet", ""))
        etCol.setText(prefs.getString("col", "A"))
        etRow.setText(prefs.getInt("row", 2).toString())

        btnPause.setOnClickListener {
            paused = !paused
            btnPause.text = if (paused) "Tiếp tục quét" else "Tạm dừng quét"
            tvStatus.text = if (paused) "Đã tạm dừng" else "Sẵn sàng quét"
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 1)
        }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<out String>, res: IntArray) {
        super.onRequestPermissionsResult(code, perms, res)
        if (res.isNotEmpty() && res[0] == PackageManager.PERMISSION_GRANTED) startCamera()
        else tvStatus.text = "Cần cấp quyền camera để quét"
    }

    private fun startCamera() {
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(findViewById<PreviewView>(R.id.previewView).surfaceProvider)
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
            analysis.setAnalyzer(Executors.newSingleThreadExecutor()) { proxy ->
                val media = proxy.image
                if (media == null || paused || busy) {
                    proxy.close()
                } else {
                    val img = InputImage.fromMediaImage(media, proxy.imageInfo.rotationDegrees)
                    scanner.process(img)
                        .addOnSuccessListener { list ->
                            list.firstOrNull()?.rawValue?.let { onCode(it) }
                        }
                        .addOnCompleteListener { proxy.close() }
                }
            }
            provider.unbindAll()
            provider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis)
        }, ContextCompat.getMainExecutor(this))
    }

    private fun onCode(code: String) {
        val now = System.currentTimeMillis()
        if (busy || (code == lastCode && now - lastTime < 2500)) return
        val url = etUrl.text.toString().trim()
        val row = etRow.text.toString().toIntOrNull()
        if (url.isEmpty() || row == null || row < 1) {
            tvStatus.text = "Nhập link Web App và số dòng hợp lệ"
            return
        }
        val col = etCol.text.toString().trim().ifEmpty { "A" }.uppercase()
        val sheet = etSheet.text.toString().trim()
        lastCode = code; lastTime = now; busy = true
        tvStatus.text = "Đang gửi: $code"
        Thread {
            var ok = false
            var msg = ""
            try {
                val enc = { s: String -> URLEncoder.encode(s, "UTF-8") }
                val full = url + (if (url.contains("?")) "&" else "?") +
                    "sheet=${enc(sheet)}&col=${enc(col)}&row=$row&value=${enc(code)}"
                val c = URL(full).openConnection() as HttpURLConnection
                c.connectTimeout = 15000; c.readTimeout = 15000
                c.instanceFollowRedirects = true
                val body = c.inputStream.bufferedReader().readText().trim()
                ok = body.startsWith("OK")
                msg = body.take(80)
            } catch (e: Exception) {
                msg = e.message ?: "Lỗi mạng"
            }
            runOnUiThread {
                if (ok) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 120)
                    tvLog.text = "$col$row  ←  $code\n" + tvLog.text
                    etRow.setText((row + 1).toString())
                    tvStatus.text = "Đã ghi $col$row. Dòng tiếp theo: ${row + 1}"
                    prefs.edit().putString("url", url).putString("sheet", sheet)
                        .putString("col", col).putInt("row", row + 1).apply()
                } else {
                    tone.startTone(ToneGenerator.TONE_CDMA_SOFT_ERROR_LITE, 300)
                    tvStatus.text = "Lỗi, chưa ghi được: $msg"
                    lastCode = ""
                }
                busy = false
            }
        }.start()
    }
}
