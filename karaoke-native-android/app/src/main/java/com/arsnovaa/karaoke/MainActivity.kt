package com.arsnovaa.karaoke

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.*
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.os.Bundle
import android.os.Process
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceRequest
import android.net.Uri
import android.webkit.JavascriptInterface
import android.widget.*
import android.view.View
import android.view.ViewGroup
import android.text.InputType
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.max

class MainActivity : Activity() {
    private lateinit var mic: Button
    private lateinit var status: TextView
    private lateinit var volume: SeekBar
    private lateinit var player: WebView
    private var engine: MicEngine? = null
    private var enabled = false
    private val navy = Color.rgb(13, 16, 30)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = navy
        window.navigationBarColor = navy
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(navy); setPadding(18, 20, 18, 10) }
        fun label(t: String, size: Float): TextView = TextView(this).apply { text=t; textSize=size; setTextColor(Color.WHITE) }
        root.addView(label("KARAOKE PARTY", 26f))
        root.addView(label("YouTube lyrics + live microphone", 13f).apply { setTextColor(0xffa5aec8.toInt()) })
        val input = EditText(this).apply { hint="Paste YouTube link or video ID"; setSingleLine(); setTextColor(Color.WHITE); setHintTextColor(0xff8d96ae.toInt()); inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI }
        root.addView(input, LinearLayout.LayoutParams(-1, -2))
        val search = Button(this).apply { text="SEARCH YOUTUBE KARAOKE" }
        root.addView(search)
        val load = Button(this).apply { text="LOAD VIDEO LINK" }
        root.addView(load)
        player = WebView(this).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webChromeClient = WebChromeClient()
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    return !url.startsWith("https://www.youtube.com/") && !url.startsWith("https://m.youtube.com/") && !url.startsWith("https://youtube.com/") && !url.startsWith("https://youtu.be/")
                }
            }
            settings.mediaPlaybackRequiresUserGesture = true
            setBackgroundColor(Color.BLACK)
        }
        root.addView(player, LinearLayout.LayoutParams(-1, 0, 1f))
        search.setOnClickListener {
            val query = input.text.toString().trim().ifBlank { "karaoke instrumental lyrics" }
            player.loadUrl("https://m.youtube.com/results?search_query=" + Uri.encode(query + " karaoke instrumental lyrics"))
            input.clearFocus()
            (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager).hideSoftInputFromWindow(input.windowToken, 0)
        }
        load.setOnClickListener {
            val id = parseYouTubeId(input.text.toString())
            if (id == null) Toast.makeText(this, "Enter a valid YouTube video link", Toast.LENGTH_SHORT).show()
            else player.loadUrl("https://m.youtube.com/watch?v=$id")
        }
        status = label("Connect Bluetooth speaker in Android settings, then enable mic", 13f)
        status.setTextColor(0xffb6c2df.toInt()); root.addView(status)
        root.addView(label("Microphone volume", 14f))
        volume = SeekBar(this).apply { max=100; progress=45 }
        root.addView(volume)
        mic = Button(this).apply { text="MIC ON" }
        root.addView(mic)
        root.addView(label("Bluetooth may add noticeable voice delay. Headphones can cause feedback. Music volume uses your device volume buttons.", 12f).apply { setTextColor(0xff9da8c1.toInt()) })
        setContentView(root)
        mic.setOnClickListener {
            if (enabled) stopMic() else if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 8) else startMic()
        }
    }
    private fun iframe(id: String): String = """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1"></head><body style="margin:0;background:black"><iframe width="100%" height="100%" src="https://www.youtube.com/embed/$id?playsinline=1" title="YouTube karaoke" frameborder="0" allow="autoplay; encrypted-media; picture-in-picture; fullscreen" allowfullscreen></iframe></body></html>"""
    private fun parseYouTubeId(raw: String): String? {
        val s=raw.trim()
        if (s.matches(Regex("[a-zA-Z0-9_-]{11}"))) return s
        val patterns=listOf(Regex("(?:youtube\\.com/watch\\?[^#]*?v=|youtube\\.com/shorts/|youtu\\.be/|youtube\\.com/embed/)([a-zA-Z0-9_-]{11})"))
        return patterns.firstNotNullOfOrNull { it.find(s)?.groupValues?.get(1) }
    }
    private fun startMic() {
        try {
            val e=MicEngine { volume.progress / 100f }
            e.start()
            engine=e; enabled=true; mic.text="MIC OFF"; status.text="Microphone live — sing into your phone"
        } catch (e: Exception) { status.text="Mic failed: ${e.message}" }
    }
    private fun stopMic() { engine?.stop(); engine=null; enabled=false; mic.text="MIC ON"; status.text="Microphone off" }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode==8 && grantResults.firstOrNull()==PackageManager.PERMISSION_GRANTED) startMic()
    }
    override fun onPause() { super.onPause(); stopMic(); player.onPause() }
    override fun onResume() { super.onResume(); if (::player.isInitialized) player.onResume() }
    override fun onDestroy() { stopMic(); player.destroy(); super.onDestroy() }
}

class MicEngine(private val gain: () -> Float) {
    private val running = AtomicBoolean(false)
    private var record: AudioRecord? = null
    private var output: AudioTrack? = null
    private var worker: Thread? = null
    private var echo: AcousticEchoCanceler? = null
    private var noise: NoiseSuppressor? = null
    fun start() {
        val rate=48000
        val minimum=max(AudioRecord.getMinBufferSize(rate, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT), 4096)
        val rec=AudioRecord.Builder()
            .setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_IN_MONO).build())
            .setBufferSizeInBytes(minimum * 2).build()
        if (rec.state != AudioRecord.STATE_INITIALIZED) { rec.release(); error("AudioRecord initialization failed") }
        val track=AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(rate).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_MONO).build())
            .setBufferSizeInBytes(minimum * 2).setTransferMode(AudioTrack.MODE_STREAM).build()
        if (track.state != AudioTrack.STATE_INITIALIZED) { rec.release(); track.release(); error("AudioTrack initialization failed") }
        record=rec; output=track
        try { if (AcousticEchoCanceler.isAvailable()) echo = AcousticEchoCanceler.create(rec.audioSessionId)?.apply { enabled = true } } catch (_: Exception) {}
        try { if (NoiseSuppressor.isAvailable()) noise = NoiseSuppressor.create(rec.audioSessionId)?.apply { enabled = true } } catch (_: Exception) {}
        rec.startRecording(); track.play(); running.set(true)
        worker=thread(name="karaoke-mic", isDaemon=true) {
            Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
            val samples=ShortArray(1024)
            while (running.get()) {
                val n=rec.read(samples, 0, samples.size, AudioRecord.READ_BLOCKING)
                if (n>0) {
                    val v=gain().coerceIn(0f, 1f)
                    for (i in 0 until n) samples[i]=(samples[i]*v).toInt().coerceIn(-32768,32767).toShort()
                    var offset=0
                    while (offset<n && running.get()) {
                        val written=track.write(samples,offset,n-offset,AudioTrack.WRITE_BLOCKING)
                        if (written<=0) break
                        offset+=written
                    }
                }
            }
        }
    }
    fun stop() {
        running.set(false)
        try { record?.stop() } catch (_: Exception) {}
        try { output?.pause(); output?.flush() } catch (_: Exception) {}
        worker?.join(350)
        echo?.release(); noise?.release(); echo=null; noise=null
        record?.release(); output?.release(); record=null; output=null; worker=null
    }
}
