package com.wifimic

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Bundle
import android.os.PowerManager
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.wifimic.databinding.ActivityMainBinding
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    companion object {
        private const val TAG            = "WiFiMic"
        private const val MIC_PERMISSION = Manifest.permission.RECORD_AUDIO
        private const val PERM_REQ       = 101

        // Audio settings — must match receiver.py
        const val SAMPLE_RATE      = 44100
        const val CHANNEL_CONFIG   = AudioFormat.CHANNEL_IN_MONO
        const val AUDIO_FORMAT     = AudioFormat.ENCODING_PCM_16BIT
        const val BYTES_PER_SAMPLE = 2
        const val CHUNK_BYTES      = 4096   // bytes per UDP packet payload
        const val DEFAULT_PORT     = 9999
    }

    private lateinit var binding: ActivityMainBinding
    private val streaming = AtomicBoolean(false)
    private var streamThread: Thread? = null
    private var wakeLock: PowerManager.WakeLock? = null

    // ── Lifecycle ─────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.portInput.setText(DEFAULT_PORT.toString())
        updateUI(false)

        binding.btnToggle.setOnClickListener {
            if (streaming.get()) stopStreaming() else attemptStart()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopStreaming()
    }

    // ── Permissions ───────────────────────────────────────────────────────────

    private fun attemptStart() {
        if (ContextCompat.checkSelfPermission(this, MIC_PERMISSION)
            == PackageManager.PERMISSION_GRANTED) {
            startStreaming()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(MIC_PERMISSION), PERM_REQ)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == PERM_REQ &&
            grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            startStreaming()
        } else {
            setStatus("Microphone permission denied.")
        }
    }

    // ── Streaming ─────────────────────────────────────────────────────────────

    private fun startStreaming() {
        val ip   = binding.ipInput.text.toString().trim()
        val port = binding.portInput.text.toString().trim().toIntOrNull() ?: DEFAULT_PORT

        if (ip.isEmpty()) {
            setStatus("Please enter your PC's IP address.")
            return
        }

        streaming.set(true)
        updateUI(true)
        acquireWakeLock()
        streamThread = Thread({ streamLoop(ip, port) }, "wifimic-stream").also { it.start() }
    }

    private fun stopStreaming() {
        streaming.set(false)
        streamThread?.join(2000)
        streamThread = null
        releaseWakeLock()
        runOnUiThread { updateUI(false) }
    }

    /**
     * Core loop: capture raw PCM from mic → send over UDP.
     * No encoding — raw 16-bit PCM at 44100 Hz mono.
     * At 44100 Hz × 2 bytes = 88 KB/s, trivial for Wi-Fi.
     */
    private fun streamLoop(ip: String, port: Int) {
        val minBuf  = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT)
        val bufSize = maxOf(minBuf, CHUNK_BYTES * 4)

        val recorder = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            SAMPLE_RATE,
            CHANNEL_CONFIG,
            AUDIO_FORMAT,
            bufSize
        )

        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            runOnUiThread { setStatus("Failed to open microphone.") }
            streaming.set(false)
            return
        }

        val socket  = DatagramSocket()
        val address = InetAddress.getByName(ip)
        val pcmBuf  = ByteArray(CHUNK_BYTES)
        var sequence = 0

        recorder.startRecording()
        runOnUiThread { setStatus("● Streaming to $ip:$port") }
        Log.d(TAG, "Stream started → $ip:$port  (raw PCM, no codec)")

        try {
            while (streaming.get()) {
                // Fill the chunk buffer with PCM samples
                var offset = 0
                while (offset < CHUNK_BYTES && streaming.get()) {
                    val n = recorder.read(pcmBuf, offset, CHUNK_BYTES - offset)
                    if (n > 0) offset += n
                }
                if (!streaming.get()) break

                // Packet = [4-byte big-endian sequence number] + [raw PCM]
                val packet = ByteBuffer
                    .allocate(4 + CHUNK_BYTES)
                    .order(ByteOrder.BIG_ENDIAN)
                    .putInt(sequence++)
                    .put(pcmBuf)
                    .array()

                socket.send(DatagramPacket(packet, packet.size, address, port))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Stream error", e)
            runOnUiThread { setStatus("Error: ${e.message}") }
        } finally {
            recorder.stop()
            recorder.release()
            socket.close()
            Log.d(TAG, "Stream stopped")
        }
    }

    // ── Wake lock (keeps CPU alive while streaming) ────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "WiFiMic:stream")
        wakeLock?.acquire(60 * 60 * 1000L)  // up to 1 hour
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    // ── UI ────────────────────────────────────────────────────────────────────

    private fun updateUI(streaming: Boolean) {
        binding.btnToggle.text      = if (streaming) "Stop" else "Start Streaming"
        binding.ipInput.isEnabled   = !streaming
        binding.portInput.isEnabled = !streaming
        if (!streaming) setStatus("Ready")
    }

    private fun setStatus(msg: String) {
        binding.statusText.text = msg
    }
}
