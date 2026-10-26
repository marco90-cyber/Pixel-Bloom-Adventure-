package com.marco.pixelbloom.android

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import com.marco.pixelbloom.Host
import com.marco.pixelbloom.Sound
import com.marco.pixelbloom.Storage
import com.marco.pixelbloom.Synth

/** Salvataggi su SharedPreferences. */
class PrefsStorage(ctx: Context) : Storage {
    private val prefs = ctx.getSharedPreferences("pixelbloom", Context.MODE_PRIVATE)
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(key: String) { prefs.edit().remove(key).apply() }
    override fun keys(prefix: String): List<String> = prefs.all.keys.filter { it.startsWith(prefix) }.sorted()
}

/** Audio chiptune: il Synth genera i campioni, un thread li scrive su AudioTrack. */
class AndroidSound : Sound {
    private val synth = Synth(22050)
    @Volatile private var running = false
    @Volatile private var paused = false
    private var thread: Thread? = null
    private var track: AudioTrack? = null

    override fun sfx(id: Int) { synth.sfx(id) }
    override fun music(id: Int) { synth.music(id) }
    override fun setVolumes(music: Float, sfx: Float) { synth.musicVol = music; synth.sfxVol = sfx }

    @Synchronized fun start() {
        if (running) { paused = false; try { track?.play() } catch (e: Exception) {}; return }
        val rate = synth.rate
        val minBuf = AudioTrack.getMinBufferSize(rate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val bufBytes = maxOf(minBuf, 4096 * 2)
        val t = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(rate)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(bufBytes)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
        track = t
        running = true
        paused = false
        t.play()
        thread = Thread({
            val buf = ShortArray(1024)
            while (running) {
                if (paused) { try { Thread.sleep(30) } catch (e: InterruptedException) {}; continue }
                synth.render(buf, buf.size)
                try { t.write(buf, 0, buf.size) } catch (e: Exception) { break }
            }
        }, "pb-audio").also { it.isDaemon = true; it.start() }
    }

    @Synchronized fun pause() {
        paused = true
        try { track?.pause() } catch (e: Exception) {}
    }

    @Synchronized fun stop() {
        running = false
        try { thread?.join(300) } catch (e: Exception) {}
        thread = null
        try { track?.stop(); track?.release() } catch (e: Exception) {}
        track = null
    }
}

class AndroidHost(private val act: Activity) : Host {
    override fun exit() { act.runOnUiThread { act.finish() } }

    override fun share(text: String) {
        act.runOnUiThread {
            val i = Intent(Intent.ACTION_SEND)
            i.type = "text/plain"
            i.putExtra(Intent.EXTRA_TEXT, text)
            act.startActivity(Intent.createChooser(i, "Condividi livello"))
        }
    }

    override fun clipboard(): String? {
        return try {
            val cm = act.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) clip.getItemAt(0).coerceToText(act)?.toString() else null
        } catch (e: Exception) { null }
    }

    @Suppress("DEPRECATION")
    override fun vibrate(ms: Int) {
        try {
            val v = act.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
            if (!v.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= 26) v.vibrate(VibrationEffect.createOneShot(ms.toLong(), VibrationEffect.DEFAULT_AMPLITUDE))
            else v.vibrate(ms.toLong())
        } catch (e: Exception) {}
    }
}
