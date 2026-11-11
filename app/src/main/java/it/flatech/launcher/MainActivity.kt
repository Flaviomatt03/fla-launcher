package it.flatech.launcher

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject

class MainActivity : Activity() {
    companion object { const val URL = "https://flaviomatt03.github.io/fla-tech/" }

    private lateinit var web: WebView
    private var sr: SpeechRecognizer? = null
    private val h = Handler(Looper.getMainLooper())
    private val unmute = Runnable { mute(false) }

    private val apps = mapOf(
        "whatsapp" to listOf("com.whatsapp"),
        "instagram" to listOf("com.instagram.android"),
        "chrome" to listOf("com.android.chrome"),
        "rubrica" to listOf("com.samsung.android.app.contacts", "com.google.android.contacts", "com.android.contacts")
    )

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(perms.toTypedArray(), 1)

        web = WebView(this)
        setContentView(web)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString + " FLATechLauncher"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "FLAAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(v: WebView, r: WebResourceRequest): Boolean {
                val s = r.url.scheme
                if (s == "http" || s == "https") return false
                try { startActivity(Intent(Intent.ACTION_VIEW, r.url).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } catch (_: Exception) {}
                return true
            }
        }
        web.webChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(r: PermissionRequest) { runOnUiThread { r.grant(r.resources) } }
        }
        web.loadUrl(URL)
    }

    override fun onResume() {
        super.onResume()
        stopService(Intent(this, BubbleService::class.java))
        web.onResume()
    }

    override fun onPause() {
        super.onPause()
        killRec()
        mute(false)
    }

    override fun onNewIntent(i: Intent?) { super.onNewIntent(i) }

    @Deprecated("back")
    override fun onBackPressed() { if (web.canGoBack()) web.goBack() }

    override fun onDestroy() { killRec(); mute(false); super.onDestroy() }

    // ---------- voce nativa ----------
    private fun js(type: String, data: String = "null") {
        runOnUiThread { web.evaluateJavascript("window.__flaSR&&window.__flaSR('$type',$data)", null) }
    }

    private fun mute(on: Boolean) {
        try {
            val am = getSystemService(AUDIO_SERVICE) as AudioManager
            val dir = if (on) AudioManager.ADJUST_MUTE else AudioManager.ADJUST_UNMUTE
            am.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, dir, 0)
            am.adjustStreamVolume(AudioManager.STREAM_SYSTEM, dir, 0)
        } catch (_: Exception) {}
    }

    private fun killRec() { try { sr?.destroy() } catch (_: Exception) {}; sr = null }

    private fun endLater() { h.postDelayed({ js("end") }, 150) }

    private fun listen() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            js("error", "\"not-allowed\""); endLater(); return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) { js("error", "\"service-not-allowed\""); endLater(); return }
        killRec()
        h.removeCallbacks(unmute); mute(true)
        val r = SpeechRecognizer.createSpeechRecognizer(this)
        sr = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(p: Bundle?) { js("start") }
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(v: Float) {}
            override fun onBufferReceived(b: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onEvent(t: Int, b: Bundle?) {}
            override fun onError(e: Int) {
                h.postDelayed(unmute, 1500)
                val code = when (e) {
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "not-allowed"
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> "network"
                    else -> null
                }
                if (code != null) js("error", "\"$code\"")
                endLater()
            }
            override fun onResults(b: Bundle?) {
                h.postDelayed(unmute, 1500)
                val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: arrayListOf()
                js("final", JSONArray(l).toString()); endLater()
            }
            override fun onPartialResults(b: Bundle?) {
                val l = b?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION) ?: return
                if (l.isNotEmpty()) js("partial", JSONArray(l).toString())
            }
        })
        r.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "it-IT")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 4)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        })
    }

    // ---------- ponte JS ----------
    inner class Bridge {
        @JavascriptInterface fun startListen() { runOnUiThread { listen() } }
        @JavascriptInterface fun stopListen() { runOnUiThread { killRec(); h.postDelayed(unmute, 1500) } }
        @JavascriptInterface fun finishListen() { runOnUiThread { try { sr?.stopListening() } catch (_: Exception) {} } }

        @JavascriptInterface fun openApp(id: String) {
            runOnUiThread {
                val pk = apps[id] ?: return@runOnUiThread
                val intent = pk.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
                if (intent == null) { Toast.makeText(this@MainActivity, "App non installata", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Consenti “Mostra sopra altre app” per la bollina, poi riprova", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@runOnUiThread
                }
                try { startForegroundService(Intent(this@MainActivity, BubbleService::class.java)) } catch (_: Exception) {}
                startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            }
        }
    }
}
