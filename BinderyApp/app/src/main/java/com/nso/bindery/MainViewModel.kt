package com.nso.bindery

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val articles = MutableLiveData<List<Article>>(emptyList())
    val status = MutableLiveData<String>("")
    val isGenerating = MutableLiveData(false)

    private val prefs = app.getSharedPreferences("bindery", Context.MODE_PRIVATE)

    init {
        loadSaved()
    }

    fun addUrl(url: String) {
        viewModelScope.launch {
            val current = articles.value ?: emptyList()
            if (current.any { it.url == url }) {
                status.value = "Already in queue"
                return@launch
            }

            // Add placeholder
            val placeholder = Article(url = url, title = "Fetching…", content = "", plainText = "", status = Article.Status.FETCHING)
            articles.value = current + placeholder
            status.value = "Fetching article…"

            val result = withContext(Dispatchers.IO) {
                ArticleExtractor.extract(url)
            }

            val updated = articles.value?.toMutableList() ?: mutableListOf()
            val idx = updated.indexOfFirst { it.id == placeholder.id }

            if (result.isSuccess) {
                val article = result.getOrNull()!!
                if (idx >= 0) updated[idx] = article else updated.add(article)
                articles.value = updated
                status.value = "Added: ${article.title.take(40)}"
                save()
            } else {
                if (idx >= 0) updated[idx] = placeholder.copy(
                    title = "Error: ${url.take(40)}",
                    status = Article.Status.ERROR
                )
                articles.value = updated
                status.value = "Failed to fetch article"
            }
        }
    }

    fun removeArticle(id: Long) {
        articles.value = articles.value?.filter { it.id != id }
        save()
    }

    fun clearAll() {
        articles.value = emptyList()
        save()
    }

    fun generateEpub(bookTitle: String, outputDir: File, onDone: (File?) -> Unit) {
        val done = articles.value?.filter { it.status == Article.Status.DONE } ?: return
        if (done.isEmpty()) { onDone(null); return }

        isGenerating.value = true
        status.value = "Generating EPUB…"

        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val filename = "${bookTitle.sanitize()}-$today.epub"
            val file = File(outputDir, filename)

            withContext(Dispatchers.IO) {
                EpubGenerator.generate(done, bookTitle, file)
            }

            isGenerating.value = false
            status.value = "EPUB ready: $filename"
            onDone(file)
        }
    }

    fun generatePdf(bookTitle: String, outputDir: File, onDone: (File?) -> Unit) {
        val done = articles.value?.filter { it.status == Article.Status.DONE } ?: return
        if (done.isEmpty()) { onDone(null); return }

        isGenerating.value = true
        status.value = "Generating PDF…"

        viewModelScope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
            val filename = "${bookTitle.sanitize()}-$today.pdf"
            val file = File(outputDir, filename)

            withContext(Dispatchers.IO) {
                PdfGenerator.generate(done, bookTitle, file)
            }

            isGenerating.value = false
            status.value = "PDF ready: $filename"
            onDone(file)
        }
    }

    private fun save() {
        val arr = JSONArray()
        articles.value?.filter { it.status == Article.Status.DONE }?.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("url", a.url)
                put("title", a.title)
                put("content", a.content)
                put("plainText", a.plainText)
            })
        }
        prefs.edit().putString("articles", arr.toString()).apply()
    }

    private fun loadSaved() {
        val json = prefs.getString("articles", "[]") ?: "[]"
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<Article>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(Article(
                    id = obj.getLong("id"),
                    url = obj.getString("url"),
                    title = obj.getString("title"),
                    content = obj.getString("content"),
                    plainText = obj.getString("plainText")
                ))
            }
            articles.value = list
        } catch (e: Exception) { /* ignore */ }
    }

    private fun String.sanitize() = replace(Regex("[^a-zA-Z0-9-]"), "-").lowercase()
}
