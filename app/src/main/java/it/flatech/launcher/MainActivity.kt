package it.flatech.launcher

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
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
    companion object {
        const val URL = "https://flaviomatt03.github.io/fla-tech/"
        const val OFFLINE = """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<style>
html,body{margin:0;height:100%;background:#08131E;color:#E6F4FC;font-family:system-ui,sans-serif}
body{display:flex;flex-direction:column;align-items:center;justify-content:center;gap:14px;text-align:center;padding:24px;box-sizing:border-box}
svg{filter:drop-shadow(0 0 14px rgba(160,225,255,.8));margin-bottom:6px}
h1{font-size:22px;margin:0}
p{margin:0;color:#9FB6C6;font-size:15px;line-height:1.45;max-width:300px}
button{margin-top:10px;border:1px solid rgba(110,200,245,.6);background:rgba(10,30,46,.85);color:#E6F4FC;font-size:17px;font-weight:700;padding:14px 34px;border-radius:30px;box-shadow:0 0 18px rgba(90,200,245,.3)}
</style></head><body>
<svg width="86" height="78" viewBox="0 0 154 140"><path d="M77 20 L136 120 L18 120 Z" fill="#CBEBFC" stroke="#CBEBFC" stroke-width="18" stroke-linejoin="round"/></svg>
<h1>Nessuna connessione</h1>
<p>Non riesco a caricare FLA-Tech. Controlla internet: appena torna, riprovo da solo.</p>
<button onclick="FLAAndroid.refresh()">Riprova</button>
<script>window.addEventListener('online',function(){FLAAndroid.refresh()})</script>
</body></html>"""
    }
    private var offline = false

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
        val perms = mutableListOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.READ_CONTACTS)
        if (Build.VERSION.SDK_INT >= 33) perms.add(Manifest.permission.POST_NOTIFICATIONS)
        if (perms.any { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }) requestPermissions(perms.toTypedArray(), 1)

        web = WebView(this)
        setContentView(web)
        immersive()
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            mediaPlaybackRequiresUserGesture = false
            userAgentString = userAgentString + " FLATechLauncher"
        }
        CookieManager.getInstance().setAcceptThirdPartyCookies(web, true)
        web.addJavascriptInterface(Bridge(), "FLAAndroid")
        web.webViewClient = object : WebViewClient() {
            override fun onReceivedError(v: WebView, r: WebResourceRequest, e: WebResourceError) {
                if (r.isForMainFrame) {
                    offline = true
                    v.loadDataWithBaseURL("about:blank", OFFLINE, "text/html", "utf-8", null)
                }
            }
            override fun onPageFinished(v: WebView, u: String?) {
                if (u != null && u.startsWith("http")) offline = false
            }
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

    private fun immersive() {
        if (Build.VERSION.SDK_INT >= 28) {
            val a = window.attributes
            a.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            window.attributes = a
        }
        @Suppress("DEPRECATION")
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION)
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) immersive()
    }

    override fun onResume() {
        super.onResume()
        stopService(Intent(this, BubbleService::class.java))
        web.onResume()
        if (offline) web.loadUrl(URL)
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
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5)
            try { putStringArrayListExtra("android.speech.extra.BIASING_STRINGS", biasList()) } catch (_: Exception) {}
            putExtra("android.speech.extra.PREFER_OFFLINE", false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        })
    }


    private var biasCache: ArrayList<String>? = null
    private fun biasList(): ArrayList<String> {
        biasCache?.let { return it }
        val out = LinkedHashSet<String>()
        out.addAll(listOf("FLA-Tech", "Hey Fla", "torcia", "timer", "promemoria", "ricordami", "calendario", "Finanze", "Spotify", "WhatsApp", "Instagram", "YouTube", "ChatGPT", "Claude", "scrivi a", "chiama", "portami a", "metti la playlist", "aggiungi in coda", "meteo", "luna", "obiettivo", "buongiorno", "buonanotte", "cuore giallo", "cuore rosso", "cuore blu", "cuore verde"))
        try {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                contentResolver.query(android.provider.ContactsContract.Contacts.CONTENT_URI, arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME),
                    android.provider.ContactsContract.Contacts.STARRED + "=1", null, null)?.use { c ->
                    while (c.moveToNext() && out.size < 90) c.getString(0)?.let { out.add(it) }
                }
                contentResolver.query(android.provider.ContactsContract.Contacts.CONTENT_URI, arrayOf(android.provider.ContactsContract.Contacts.DISPLAY_NAME),
                    null, null, android.provider.ContactsContract.Contacts.TIMES_CONTACTED + " DESC")?.use { c ->
                    while (c.moveToNext() && out.size < 90) c.getString(0)?.let { out.add(it) }
                }
            }
        } catch (_: Exception) {}
        val r = ArrayList(out.toList().take(100)); biasCache = r; return r
    }

    // ---------- aggiornamento del launcher da GitHub Releases ----------
    private fun toastUi(m: String) { runOnUiThread { Toast.makeText(this, m, Toast.LENGTH_LONG).show() } }

    private fun doUpdate() {
        try {
            val base = "https://github.com/Flaviomatt03/fla-launcher/releases/download/latest/"
            val vc = URL(base + "version.txt").openConnection()
            vc.useCaches = false
            vc.setRequestProperty("Cache-Control", "no-cache")
            val remote = vc.getInputStream().bufferedReader().use { it.readText() }.trim().toLong()
            val pi = packageManager.getPackageInfo(packageName, 0)
            @Suppress("DEPRECATION")
            val local = if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else pi.versionCode.toLong()
            if (remote <= local) { toastUi("Launcher già aggiornato (ho " + local + ", online c\u2019è " + remote + ")"); return }
            toastUi("Nuovo launcher trovato: " + local + " \u2192 " + remote)
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
    private var torchOn = false

    inner class Bridge {
        @JavascriptInterface fun startListen() { runOnUiThread { listen() } }
        @JavascriptInterface fun stopListen() { runOnUiThread { killRec(); h.postDelayed(unmute, 1500) } }
        @JavascriptInterface fun ver(): String = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "?" } catch (e: Exception) { "?" }
        @JavascriptInterface fun checkUpdate() { Thread { doUpdate() }.start() }
        @JavascriptInterface fun battery(): String {
            val i = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
            val sc = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
            val st = i?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
            val ch = st == BatteryManager.BATTERY_STATUS_CHARGING || st == BatteryManager.BATTERY_STATUS_FULL
            val pct = if (l < 0 || sc <= 0) -1 else l * 100 / sc
            return "{\"l\":" + pct + ",\"c\":" + ch + "}"
        }
        @JavascriptInterface fun inset(): Double {
            val id = resources.getIdentifier("status_bar_height", "dimen", "android")
            return if (id > 0) resources.getDimensionPixelSize(id) / resources.displayMetrics.density.toDouble() else 28.0
        }
        @JavascriptInterface fun torch(on: Boolean): Boolean {
            return try {
                val cm = getSystemService(Context.CAMERA_SERVICE) as CameraManager
                val id = cm.cameraIdList.firstOrNull {
                    val c = cm.getCameraCharacteristics(it)
                    c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true && c.get(CameraCharacteristics.LENS_FACING) == CameraCharacteristics.LENS_FACING_BACK
                } ?: return false
                cm.setTorchMode(id, on); torchOn = on; true
            } catch (e: Exception) { false }
        }
        @JavascriptInterface fun torchState(): Boolean = torchOn
        @JavascriptInterface fun findContact(q: String): String {
            if (checkSelfPermission(Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) return ""
            fun nz(x: String) = java.text.Normalizer.normalize(x, java.text.Normalizer.Form.NFD).replace(Regex("\\p{M}+"), "").lowercase()
            val toks = nz(q).split(Regex("\\s+")).filter { it.isNotBlank() }
            if (toks.isEmpty()) return ""
            var best: Pair<String, String>? = null
            try {
                contentResolver.query(android.provider.ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(android.provider.ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, android.provider.ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null, null, null)?.use { c ->
                    while (c.moveToNext()) {
                        val n = c.getString(0) ?: continue
                        val num = c.getString(1) ?: continue
                        val nn = nz(n)
                        if (toks.all { nn.contains(it) } && (best == null || n.length < best!!.first.length)) best = Pair(n, num)
                    }
                }
            } catch (_: Exception) { return "" }
            val b = best ?: return ""
            return JSONObject().put("n", b.first).put("p", b.second).toString()
        }
        @JavascriptInterface fun refresh() { runOnUiThread { web.clearCache(true); web.loadUrl(URL) } }
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
