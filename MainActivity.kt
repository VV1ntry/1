package com.novelflow.reader

import android.app.Activity
import android.os.Bundle
import android.graphics.Color
import android.graphics.Typeface
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.net.URL

class MainActivity : Activity() {
    private lateinit var urlInput: EditText
    private lateinit var webView: WebView
    private lateinit var status: TextView
    private lateinit var extractedPreview: TextView
    private lateinit var originalButton: Button
    private lateinit var prepareButton: Button
    private var extractedTitle: String = "Chapter"
    private var extractedText: String = ""
    private var showingOriginal = false
    private var translationText: String = ""
    private var showingTranslation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(247, 245, 250)
        window.navigationBarColor = Color.rgb(247, 245, 250)
        buildUi()
        webView.settings.javaScriptEnabled = true
        webView.settings.domStorageEnabled = true
        webView.settings.loadsImagesAutomatically = false
        webView.webChromeClient = WebChromeClient()
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                status.text = "Страница загружена. Нажми «Извлечь главу»."
            }
        }
    }

    private fun buildUi() {
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(18, 18, 18, 12); setBackgroundColor(Color.rgb(247,245,250)) }
        val heading = TextView(this).apply { text = "NovelFlow"; textSize = 26f; setTypeface(null, Typeface.BOLD); setTextColor(Color.rgb(35,31,42)) }
        root.addView(heading)
        root.addView(TextView(this).apply { text = "Читалка и подготовка глав к переводу"; textSize = 14f; setTextColor(Color.DKGRAY) })
        urlInput = EditText(this).apply { hint = "Вставь ссылку на главу Foxaholic или другой сайт"; setSingleLine(true); textSize = 14f }
        root.addView(urlInput, lp(match = true, height = ViewGroup.LayoutParams.WRAP_CONTENT, top = 12))
        root.addView(button("Открыть страницу") { openPage() }, lp(true, 48, 6))
        status = TextView(this).apply { text = "Ожидаю ссылку на главу"; textSize = 13f; setTextColor(Color.DKGRAY); setPadding(0, 8, 0, 8) }
        root.addView(status)
        webView = WebView(this).apply { visibility = View.GONE }
        root.addView(webView, LinearLayout.LayoutParams(1, 1))
        root.addView(button("Извлечь текст главы") { extractChapter() }, lp(true, 48, 4))
        extractedPreview = TextView(this).apply { text = "Здесь появится начало извлечённой главы."; textSize = 14f; setTextColor(Color.rgb(45,42,50)); setPadding(12,12,12,12); setBackgroundColor(Color.WHITE) }
        val previewScroll = ScrollView(this).apply { addView(extractedPreview) }
        root.addView(previewScroll, LinearLayout.LayoutParams(-1, 0, 1f))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        originalButton = button("Оригинал") { showOriginal() }
        prepareButton = button("Подготовить TXT") { prepareForDeepSeek() }
        row.addView(originalButton, LinearLayout.LayoutParams(0, 48, 1f).apply { marginEnd = 6 })
        row.addView(prepareButton, LinearLayout.LayoutParams(0, 48, 1f).apply { marginStart = 6 })
        root.addView(row)
        root.addView(button("Поделиться файлом с DeepSeek") { shareToDeepSeek() }, lp(true, 48, 6))
        root.addView(button("Импортировать готовый перевод (.txt)") { importTranslation() }, lp(true, 48, 6))
        root.addView(button("Показать перевод") { showTranslation() }, lp(true, 48, 6))
        setContentView(root)
    }

    private fun lp(match: Boolean, height: Int, top: Int) = LinearLayout.LayoutParams(if (match) -1 else -2, height).apply { topMargin = top }
    private fun button(label: String, action: () -> Unit) = Button(this).apply { text = label; isAllCaps = false; setOnClickListener { action() } }

    private fun openPage() {
        val raw = urlInput.text.toString().trim()
        if (raw.isBlank()) { toast("Сначала вставь ссылку"); return }
        val valid = runCatching { val u = URL(raw); u.protocol == "https" || u.protocol == "http" }.getOrDefault(false)
        if (!valid) { toast("Нужна полная ссылка, начинающаяся с https://"); return }
        webView.visibility = View.VISIBLE
        status.text = "Загружаю страницу…"
        webView.loadUrl(raw)
    }

    private fun extractChapter() {
        val js = """(function(){
          const selectors=['.entry-content','.reading-content','.chapter-content','.text-left','article .content','article','main'];
          const clean=(el)=>{const c=el.cloneNode(true); c.querySelectorAll('script,style,nav,header,footer,aside,form,button,iframe,.ads,.ad,.sharedaddy,.jp-relatedposts,.navigation,.post-navigation,.comments-area').forEach(x=>x.remove()); return c;};
          let candidates=selectors.map(s=>document.querySelector(s)).filter(Boolean).map(el=>clean(el));
          candidates.sort((a,b)=>(b.innerText||'').length-(a.innerText||'').length);
          let body=candidates[0]||document.body;
          let text=(body.innerText||'').replace(/\\n{3,}/g,'\\n\\n').trim();
          return JSON.stringify({title:document.title||'Chapter',text:text});
        })();"""
        status.text = "Извлекаю текст…"
        webView.evaluateJavascript(js) { value ->
            try {
                val jsonString = org.json.JSONTokener(value).nextValue() as? String ?: throw Exception("empty")
                val obj = org.json.JSONObject(jsonString)
                extractedTitle = obj.optString("title", "Chapter")
                extractedText = obj.optString("text", "").trim()
                if (extractedText.length < 300) throw Exception("Мало текста")
                File(filesDir, "original_chapter.txt").writeText(extractedText, Charsets.UTF_8)
                extractedPreview.text = extractedText.take(2200) + if (extractedText.length > 2200) "\n\n…" else ""
                status.text = "Извлечено ${extractedText.length} символов. Проверь, что в тексте нет меню и рекламы."
                toast("Глава извлечена")
            } catch (e: Exception) {
                status.text = "Не удалось надёжно определить текст. На этом сайте понадобится отдельный профиль извлечения."
                toast("Не удалось извлечь главу")
            }
        }
    }

    private fun showOriginal() {
        if (extractedText.isBlank()) { toast("Сначала извлеки главу"); return }
        showingOriginal = !showingOriginal
        showingTranslation = false
        extractedPreview.text = if (showingOriginal) extractedText else extractedText.take(2200) + if (extractedText.length > 2200) "\n\n…" else ""
        originalButton.text = if (showingOriginal) "Свернуть" else "Оригинал"
    }

    private fun showTranslation() {
        if (translationText.isBlank()) { toast("Сначала импортируй готовый перевод TXT-файлом"); return }
        showingTranslation = true
        showingOriginal = false
        extractedPreview.text = translationText
        originalButton.text = "Оригинал"
    }

    private fun importTranslation() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
        }
        startActivityForResult(intent, 42)
    }

    @Deprecated("Deprecated by Android, retained for broad device compatibility")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 42 && resultCode == RESULT_OK) {
            val uri = data?.data ?: return
            try {
                translationText = contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
                if (translationText.isBlank()) throw Exception("empty translation")
                File(filesDir, "translated_chapter.txt").writeText(translationText, Charsets.UTF_8)
                showTranslation()
                status.text = "Перевод импортирован (${translationText.length} символов)."
                toast("Перевод загружен")
            } catch (e: Exception) {
                toast("Не удалось прочитать TXT-файл")
            }
        }
    }

    private fun prepareForDeepSeek() {
        if (extractedText.isBlank()) { toast("Сначала извлеки главу"); return }
        val prompt = "Переведи следующий текст на русский литературно и полностью. Не сокращай и не пересказывай. Сохраняй абзацы, диалоги, имена и терминологию единообразно. Если текст слишком длинный для одного ответа, переводи строго последовательно и укажи место остановки.\n\nЗаголовок: $extractedTitle\n\n"
        val file = File(cacheDir, "${safeName(extractedTitle)}_for_DeepSeek.txt")
        file.writeText(prompt + extractedText, Charsets.UTF_8)
        status.text = "Подготовлен TXT-файл (${file.length()} байт). Следующий шаг — отправить его в DeepSeek."
        toast("TXT-файл подготовлен")
        shareFile(file)
    }

    private fun shareToDeepSeek() {
        if (extractedText.isBlank()) { toast("Сначала извлеки главу"); return }
        val file = File(cacheDir, "${safeName(extractedTitle)}_for_DeepSeek.txt")
        if (!file.exists()) { prepareForDeepSeek(); return }
        shareFile(file)
    }

    private fun shareFile(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Перевод главы: $extractedTitle")
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(contentResolver, file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(send, "Передать TXT-файл в DeepSeek или другое приложение"))
    }

    private fun safeName(value: String) = value.replace(Regex("[^A-Za-zА-Яа-я0-9_-]"), "_").take(60).ifBlank { "chapter" }
    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()

    override fun onBackPressed() {
        if (::webView.isInitialized && webView.canGoBack()) webView.goBack() else super.onBackPressed()
    }

    override fun onDestroy() {
        if (::webView.isInitialized) webView.destroy()
        super.onDestroy()
    }
}
