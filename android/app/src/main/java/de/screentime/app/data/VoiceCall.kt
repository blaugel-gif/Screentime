package de.screentime.app.data

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * Sprachanruf zum PC des Kindes: Mikrofon des Handys → Lautsprecher am PC,
 * Mikrofon am PC → Handy. Läuft über dieselbe SSH-Verbindung wie die übrige App.
 */
class VoiceCall(context: Context, private val store: Store, private val childName: String) {

    enum class Phase { CONNECTING, RINGING, ACTIVE, ENDED }

    var phase by mutableStateOf(Phase.CONNECTING)
        private set
    var endMessage by mutableStateOf<String?>(null)
        private set
    var startedAt by mutableStateOf(0L)
        private set
    var muted by mutableStateOf(false)
    var speaker by mutableStateOf(true)
        private set

    private val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    @Volatile private var running = true
    private var stream: SshStream? = null

    fun start() {
        Thread({ runCall() }, "voice-call").start()
    }

    fun hangUp() {
        if (phase == Phase.ENDED) return
        finish(if (phase == Phase.ACTIVE) "Aufgelegt" else "Abgebrochen")
    }

    @Suppress("DEPRECATION")
    fun toggleSpeaker() {
        speaker = !speaker
        audio.isSpeakerphoneOn = speaker
    }

    private fun finish(msg: String) {
        if (!running) return
        running = false
        endMessage = msg
        phase = Phase.ENDED
        stream?.close()
    }

    @SuppressLint("MissingPermission")
    @Suppress("DEPRECATION")
    private fun runCall() {
        val c = store.conn()
        if (!c.complete) {
            finish("Bitte zuerst unter „Einstellungen“ die Verbindung eintragen."); return
        }
        val s = try {
            Ssh.stream(c, "sudo -n /usr/local/sbin/screentime-ctl voice")
        } catch (e: Exception) {
            finish(e.message ?: "Verbindung fehlgeschlagen"); return
        }
        stream = s
        if (c.hostKey.isBlank() && s.hostKey != null) store.hostKey = s.hostKey
        if (!running) { s.close(); return }
        phase = Phase.RINGING

        // Erste Zeile ist JSON: angenommen oder nicht
        val head = try { readLine(s.input) } catch (e: Exception) { null }
        if (head == null) {
            finish(if (running) "Keine Antwort vom PC. Bitte das Installationsskript erneut ausführen." else "Abgebrochen")
            return
        }
        val j = runCatching { JSONObject(head) }.getOrNull()
        if (j == null || !j.optBoolean("ok")) {
            val err = j?.optString("error").orEmpty()
            finish(
                if (err == "Unbekannter Befehl") "Der PC kennt Anrufe noch nicht. Bitte das Installationsskript erneut ausführen."
                else err.ifBlank { "Anruf nicht möglich" }
            )
            return
        }
        val rate = j.optInt("rate", 16000)

        val inMin = AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val outMin = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val rec = AudioRecord(
            MediaRecorder.AudioSource.VOICE_COMMUNICATION, rate,
            AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(inMin, 3200),
        )
        val track = AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                    .setSampleRate(rate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build()
            )
            .setBufferSizeInBytes(maxOf(outMin, 3200))
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        val aec = if (AcousticEchoCanceler.isAvailable()) AcousticEchoCanceler.create(rec.audioSessionId) else null
        val ns = if (NoiseSuppressor.isAvailable()) NoiseSuppressor.create(rec.audioSessionId) else null
        aec?.setEnabled(true)
        ns?.setEnabled(true)

        val oldMode = audio.mode
        audio.mode = AudioManager.MODE_IN_COMMUNICATION
        audio.isSpeakerphoneOn = speaker

        startedAt = System.currentTimeMillis()
        phase = Phase.ACTIVE
        try {
            rec.startRecording()
            track.play()
        } catch (e: Exception) {
            finish("Mikrofon nicht verfügbar")
        }

        val sender = Thread({
            val buf = ByteArray(640) // 20 ms
            val silence = ByteArray(640)
            try {
                while (running) {
                    val n = rec.read(buf, 0, buf.size)
                    if (n <= 0) continue
                    s.output.write(if (muted) silence else buf, 0, n)
                    s.output.flush()
                }
            } catch (e: Exception) {
                finish("Verbindung getrennt")
            }
        }, "voice-send")
        sender.start()

        try {
            val buf = ByteArray(1280)
            while (running) {
                val n = s.input.read(buf)
                if (n < 0) break
                if (n > 0) track.write(buf, 0, n)
            }
            finish("$childName hat aufgelegt")
        } catch (e: Exception) {
            finish(if (running) "Verbindung getrennt" else endMessage ?: "Aufgelegt")
        } finally {
            runCatching { sender.join(500) }
            runCatching { rec.stop() }; rec.release()
            runCatching { track.stop() }; track.release()
            aec?.release(); ns?.release()
            audio.isSpeakerphoneOn = false
            audio.mode = oldMode
        }
    }

    private fun readLine(input: InputStream): String? {
        val out = ByteArrayOutputStream()
        while (out.size() < 4096) {
            val b = input.read()
            if (b < 0) return null
            if (b == '\n'.code) return out.toString("UTF-8")
            out.write(b)
        }
        return null
    }
}
