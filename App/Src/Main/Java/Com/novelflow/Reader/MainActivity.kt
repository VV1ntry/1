package com.novelflow.reader

import android.app.Activity
import android.os.Bundle
import android.content.Intent
import android.graphics.Color
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.File
import java.net.URL

class MainActivity : Activity() {
    private lateinit var browser: WebView
    private lateinit var address: EditText
    private lateinit var preview: TextView
    private lateinit var status: TextView
    private var original = ""
    private var translated = ""
    private var titleText = "Chapter"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(10))
            setBackgroundColor(Color.rgb(247,245,250))
        }
        // Android 15+ draws edge-to-edge: keep content clear of status/navigation bars and cutouts.
        ViewCompat.setOnApplyWindowInsetsListener(root) { v, insets ->
            val bars = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout()
            )
            v.setPadding(dp(14) + bars.left, dp(12) + bars.top, dp(14) + bars.right, dp(10) + bars.bottom)
            WindowInsetsCompat.CONSUMED
        }
        root.addView(TextView(this).apply { text = "NovelFlow"; textSize = 25f })
        address = EditText(this).apply { hint = "Ссылка на главу"; setSingleLine(true) }
        root.addView(address)
        root.addView(btn("Открыть страницу") { openPage() })
        status = TextView(this).apply { text = "Вставь ссылку на главу" }
        root.addView(status)
        browser = WebView(this)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                status.text = "Страница загружена"
            }
        }
        root.addView(browser, LinearLayout.LayoutParams(-1, dp(180)))
        root.addView(btn("Извлечь текст главы") { extract() })
        preview = TextView(this).apply { text = "Текст главы появится здесь"; setPadding(dp(10), dp(10), dp(10), dp(10)) }
        val scroll = ScrollView(this).apply { addView(preview) }
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(btn("Экспортировать TXT для DeepSeek") { exportText() })
        root.addView(btn("Импортировать перевод TXT") { importText() })
        root.addView(btn("Показать оригинал") { showOriginal() })
        root.addView(btn("Показать перевод") { showTranslation() })
        setContentView(root)
        restoreState()
    }

    private val prefs by lazy { getSharedPreferences("novelflow", MODE_PRIVATE) }

    private fun restoreState() {
        original = prefs.getString("original", "").orEmpty()
        translated = prefs.getString("translated", "").orEmpty()
        titleText = prefs.getString("title", "Chapter").orEmpty()
        address.setText(prefs.getString("url", "").orEmpty())
        if (translated.isNotBlank()) preview.text = translated
        else if (original.isNotBlank()) preview.text = original
        if (original.isNotBlank()) status.text = "Восстановлена последняя глава: $titleText"
    }

    override fun onPause() {
        super.onPause()
        prefs.edit()
            .putString("original", original)
            .putString("translated", translated)
            .putString("title", titleText)
            .putString("url", address.text.toString())
            .apply()
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun btn(label: String, action: () -> Unit) =
        Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }

    private fun openPage() {
        val url = address.text.toString().trim()
        val valid = runCatching { val u = URL(url); u.protocol == "https" || u.protocol == "http" }.getOrDefault(false)
        if (!valid) { toast("Введи полную ссылку https:// или http://"); return }
        browser.loadUrl(url)
        status.text = "Загружаю страницу…"
    }

    private fun extract() {
        val js = """(function(){
          const ss=['.entry-content','.reading-content','.chapter-content','.text-left','article .content','article','main'];
          const clean=e=>{let c=e.cloneNode(true);c.querySelectorAll('script,style,nav,header,footer,aside,form,button,iframe,.ads,.ad').forEach(x=>x.remove());return c};
          let a=ss.map(s=>document.querySelector(s)).filter(Boolean).map(clean);
          a.sort((x,y)=>(y.innerText||'').length-(x.innerText||'').length);
          let e=a[0]||document.body;
          return JSON.stringify({title:document.title||'Chapter',text:(e.innerText||'').replace(/\n{3,}/g,'\n\n').trim()});
        })();"""
        browser.evaluateJavascript(js) { result ->
            try {
                val json = org.json.JSONTokener(result).nextValue() as String
                val obj = org.json.JSONObject(json)
                titleText = obj.optString("title", "Chapter")
                original = obj.optString("text", "").trim()
                if (original.length < 300) throw Exception("short")
                preview.text = original.take(3500)
                status.text = "Извлечено символов: ${original.length}. Проверь текст."
            } catch (_: Exception) {
                status.text = "Не удалось определить текст главы на этом сайте"
            }
        }
    }

    private fun exportText() {
        if (original.isBlank()) { toast("Сначала извлеки главу"); return }
        val prompt = "Переведи на русский полностью, литературно, без сокращений и пересказа. Сохраняй абзацы, диалоги, имена и термины.\n\nЗаголовок: $titleText\n\n"
        val file = File(cacheDir, "chapter_for_DeepSeek.txt")
        file.writeText(prompt + original, Charsets.UTF_8)
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Перевод главы: $titleText")
            clipData = android.content.ClipData.newUri(contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Отправить TXT или сохранить файл"))
    }

    private fun importText() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(i, 42)
    }

    @Deprecated("Legacy result API for broad compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            try {
                translated = contentResolver.openInputStream(data?.data ?: return)
                    ?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (translated.isBlank()) throw Exception("empty")
                preview.text = translated
                status.text = "Перевод импортирован"
            } catch (_: Exception) { toast("Не удалось прочитать TXT") }
        }
    }

    private fun showOriginal() {
        if (original.isBlank()) toast("Сначала извлеки главу") else preview.text = original
    }
    private fun showTranslation() {
        if (translated.isBlank()) toast("Сначала импортируй перевод") else preview.text = translated
    }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
    override fun onDestroy() { browser.destroy(); super.onDestroy() }
}
