package com.nso.bindery

import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.util.concurrent.TimeUnit

object ArticleExtractor {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .followRedirects(true)
        .addInterceptor { chain ->
            val req = chain.request().newBuilder()
                .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36")
                .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                .header("Accept-Language", "en-US,en;q=0.9")
                .build()
            chain.proceed(req)
        }
        .build()

    // Noise patterns
    private val noisePatterns = listOf(
        Regex("^(save this story|sign up|you're reading|subscribe|skip to main|give a gift|open gallery|copy link)", RegexOption.IGNORE_CASE),
        Regex("^card image$", RegexOption.IGNORE_CASE),
        Regex("Copyright ©\\d{4} Dow Jones|All Rights Reserved", RegexOption.IGNORE_CASE),
        Regex("^[a-f0-9]{32}$"),
        Regex("photograph by|photo by|courtesy|getty|reuters|ap photo|illustration by|image may contain", RegexOption.IGNORE_CASE),
        Regex("SaveListen|am ET|pm ET"),
        Regex("^[A-Z][a-z]+ [A-Z][a-z]+ is (a|an) (reporter|editor|writer|correspondent)", RegexOption.IGNORE_CASE)
    )

    private fun isNoise(text: String): Boolean {
        if (text.length < 10) return true
        if (text.length > 400 && text.contains(Regex("cookie|consent|gdpr|privacy policy", RegexOption.IGNORE_CASE))) return true
        return noisePatterns.any { it.containsMatchIn(text) }
    }

    // Site-specific CSS selectors for paragraph elements
    private val siteParaSelectors = listOf(
        "[data-type=paragraph]",                          // WSJ
        "p.rt-Text",                                      // Star Tribune
        "p[class*=duet--article--dangerously-set-cms]",  // The Verge
        "[data-testid=paragraph]",                        // NYT
        "p[class*=article__body-text]",                   // The Economist
        "p[class*=ds-body-text]",                         // The Economist alt
        "[data-component=ArticleParagraph] p",            // The Economist
        "[data-component=paragraph] p",                   // Bloomberg
        "p[class*=story-text__paragraph]",                // Politico
        "[data-gu-name=body] p",                          // The Guardian
        "p[data-el=text]",                                // Washington Post
        "p[class*=article-body__paragraph]",              // NBC/Reuters
        ".RichTextStoryBody p",                           // AP News
        ".story-content p",                               // Axios
        ".available-content p",                           // Substack
        "p[class*=pw-post-body-paragraph]",               // Medium
        ".article-content p",                             // Fox News
        "p[class*=article__paragraph]",                   // NY Post
        ".entry-content p",                               // WordPress/Breitbart
        "[itemprop=articleBody] p"                        // Generic schema
    )

    // Container selectors for finding article body
    private val containerSelectors = listOf(
        "[itemprop=articleBody]",
        "[class*=article-content]",
        "[class*=ArticleBody-articleBody]",
        "[class*=ArticleBody]",
        "[class*=article-body-content]",
        "[class*=article-body]",
        "[class*=article-body__content]",
        ".article__body",
        "[class*=StoryBodyCompanionColumn]",
        "[name=articleBody]",
        "[class*=article__body]",
        "[class*=c-article-body]",
        "[class*=duet--article--article-body]",
        "[class*=story-body]",
        "[class*=content-body]",
        "article",
        "[role=main]",
        "main",
        ".post-content",
        ".entry-content",
        "#article-body",
        "#content"
    )

    fun extract(url: String): Result<Article> {
        return try {
            val html = fetchHtml(url) ?: return Result.failure(Exception("Could not fetch page"))
            val doc = Jsoup.parse(html, url)
            val title = extractTitle(doc)
            val paragraphs = extractParagraphs(doc)

            if (paragraphs.isEmpty()) {
                return Result.failure(Exception("No article content found"))
            }

            val plainText = paragraphs.joinToString("\n\n")
            val content = buildHtmlContent(title, paragraphs)

            Result.success(Article(
                url = url,
                title = title,
                content = content,
                plainText = plainText
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchHtml(url: String): String? {
        return try {
            val req = Request.Builder().url(url).build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.string() else null
            }
        } catch (e: Exception) { null }
    }

    private fun extractTitle(doc: Document): String {
        return doc.select("meta[property=og:title]").attr("content").trim().ifEmpty {
            doc.select("h1").firstOrNull()?.text()?.trim() ?: doc.title().trim()
        }
    }

    private fun extractParagraphs(doc: Document): List<String> {
        val seen = mutableSetOf<String>()
        val paragraphs = mutableListOf<String>()

        // Remove noise elements
        val noiseSelectors = listOf(
            "script", "style", "noscript", "iframe", "button", "nav",
            "header", "footer", "aside", "form",
            "[class*=sidebar]", "[class*=menu]", "[class*=ad-]",
            "[class*=cookie]", "[class*=consent]", "[class*=popup]",
            "[class*=modal]", "[class*=subscribe]", "[class*=newsletter]",
            "[class*=related]", "[class*=comment]", "[class*=share]",
            "[class*=author-bio]", "[class*=AuthorBio]", "[class*=byline]",
            "[class*=video]", "[role=dialog]", "#onetrust-consent-sdk"
        )
        noiseSelectors.forEach { sel ->
            try { doc.select(sel).remove() } catch (e: Exception) {}
        }

        // Try site-specific selectors first
        for (sel in siteParaSelectors) {
            try {
                val els = doc.select(sel)
                if (els.size >= 3) {
                    els.forEach { el ->
                        val text = el.text().trim()
                        if (!isNoise(text) && seen.add(text) && text.length > 20) {
                            // Stop at WSJ copyright line
                            if (text.contains(Regex("Copyright ©\\d{4} Dow Jones|All Rights Reserved", RegexOption.IGNORE_CASE))) return@forEach
                            paragraphs.add(text)
                        }
                    }
                    if (paragraphs.size >= 3) return paragraphs
                }
            } catch (e: Exception) {}
        }

        // Fall back to container-based extraction
        paragraphs.clear()
        seen.clear()

        var container = doc.body()
        for (sel in containerSelectors) {
            try {
                val el = doc.select(sel).firstOrNull()
                if (el != null && el.text().length > 200) { container = el; break }
            } catch (e: Exception) {}
        }

        var stopExtraction = false
        container.select("p, h1, h2, h3, h4, blockquote").forEach { el ->
            if (stopExtraction) return@forEach
            val text = el.text().trim()
            if (text.contains(Regex("Copyright ©\\d{4} Dow Jones|All Rights Reserved", RegexOption.IGNORE_CASE))) {
                stopExtraction = true; return@forEach
            }
            if (!isNoise(text) && seen.add(text) && text.length > 20) {
                paragraphs.add(text)
            }
        }

        // Last resort: split body text
        if (paragraphs.size < 3) {
            paragraphs.clear(); seen.clear()
            container.text().split("\n")
                .map { it.trim() }
                .filter { it.length > 60 && !isNoise(it) && seen.add(it) }
                .forEach { paragraphs.add(it) }
        }

        return paragraphs
    }

    private fun buildHtmlContent(title: String, paragraphs: List<String>): String {
        val sb = StringBuilder()
        sb.append("<h1>${title.escapeHtml()}</h1>\n")
        paragraphs.forEach { sb.append("<p>${it.escapeHtml()}</p>\n") }
        return sb.toString()
    }

    private fun String.escapeHtml() = this
        .replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;")
}
