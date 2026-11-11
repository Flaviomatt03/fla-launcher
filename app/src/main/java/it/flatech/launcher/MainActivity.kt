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
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL
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
        "rubrica" to listOf("com.samsung.android.app.contacts", "com.google.android.contacts", "com.android.contacts"),
        "galleria" to listOf("com.sec.android.gallery3d", "com.google.android.apps.photos", "com.android.gallery3d"),
        "camera" to listOf("com.sec.android.app.camera", "com.android.camera"),
        "chat" to listOf("com.samsung.android.messaging", "com.google.android.apps.messaging"),
        "note" to listOf("com.samsung.android.app.notes", "com.google.android.keep"),
        "impostazioni" to listOf("com.android.settings"),
        "intesa" to listOf("com.latuabancaperandroid"),
        "claude" to listOf("com.anthropic.claude")
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
                if (s == "intent") {
                    try {
                        val i = Intent.parseUri(r.url.toString(), Intent.URI_INTENT_SCHEME)
                        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        i.setComponent(null); i.setSelector(null)
                        startActivity(i)
                    } catch (e: Exception) {
                        Toast.makeText(this@MainActivity, "Impossibile aprire: " + (e.message ?: "errore"), Toast.LENGTH_LONG).show()
                    }
                    return true
                }
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


    // ---------- aggiornamento del launcher da GitHub Releases ----------
    private fun toastUi(m: String) { runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() } }

    private fun doUpdate() {
        try {
            val base = "https://github.com/Flaviomatt03/fla-launcher/releases/download/latest/"
            val remote = URL(base + "version.txt").openStream().bufferedReader().use { it.readText() }.trim().toLong()
            val pi = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val local = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            if (remote <= local) { toastUi("Launcher già aggiornato"); return }
            if (!packageManager.canRequestPackageInstalls()) {
                toastUi("Consenti a FLA-Tech di installare app, poi tocca di nuovo l\u2019ingranaggio")
                runOnUiThread { startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:$packageName"))) }
                return
            }
            toastUi("Scarico il nuovo launcher\u2026")
            val dir = File(cacheDir, "updates"); dir.mkdirs()
            val f = File(dir, "app.apk")
            URL(base + "app-debug.apk").openStream().use { i -> f.outputStream().use { o -> i.copyTo(o) } }
            val uri = FileProvider.getUriForFile(this, "$packageName.fp", f)
            runOnUiThread {
                startActivity(
                    Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        } catch (e: Exception) {
            toastUi("Aggiornamento launcher non riuscito: " + (e.message ?: "errore"))
        }
    }

    // ---------- ponte JS ----------
    inner class Bridge {
        @JavascriptInterface fun startListen() { runOnUiThread { listen() } }
        @JavascriptInterface fun stopListen() { runOnUiThread { killRec(); h.postDelayed(unmute, 1500) } }
        @JavascriptInterface fun ver(): String = "1.4"
        @JavascriptInterface fun checkUpdate() { Thread { doUpdate() }.start() }
        @JavascriptInterface fun refresh() { runOnUiThread { web.clearCache(true); web.reload() } }
        @JavascriptInterface fun finishListen() { runOnUiThread { try { sr?.stopListening() } catch (_: Exception) {} } }

        @JavascriptInterface fun openApp(id: String) { launch(id, apps[id] ?: return) }
        @JavascriptInterface fun openPkgs(id: String, csv: String) { launch(id, csv.split(",").filter { it.isNotBlank() }) }

        private fun launch(id: String, pk: List<String>) {
            runOnUiThread {
                val intent = pk.firstNotNullOfOrNull { packageManager.getLaunchIntentForPackage(it) }
                if (intent == null) { Toast.makeText(this@MainActivity, "App non installata", Toast.LENGTH_SHORT).show(); return@runOnUiThread }
                if (!Settings.canDrawOverlays(this@MainActivity)) {
                    Toast.makeText(this@MainActivity, "Consenti \u201CMostra sopra altre app\u201D per la bollina, poi riprova", Toast.LENGTH_LONG).show()
                    startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                    return@runOnUiThread
                }
                try { startForegroundService(Intent(this@MainActivity, BubbleService::class.java)) } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Bollina non attiva: " + (e.message ?: "?"), Toast.LENGTH_LONG).show()
                }
                try {
                    startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                } catch (e: Exception) {
                    Toast.makeText(this@MainActivity, "Errore apertura: " + (e.message ?: "?"), Toast.LENGTH_LONG).show()
                    return@runOnUiThread
                }
            }
        }
    }
}
