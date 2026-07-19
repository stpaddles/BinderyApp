package com.nso.bindery

import com.itextpdf.io.font.constants.StandardFonts
import com.itextpdf.kernel.colors.ColorConstants
import com.itextpdf.kernel.colors.DeviceRgb
import com.itextpdf.kernel.events.Event
import com.itextpdf.kernel.events.IEventHandler
import com.itextpdf.kernel.events.PdfDocumentEvent
import com.itextpdf.kernel.font.PdfFontFactory
import com.itextpdf.kernel.geom.PageSize
import com.itextpdf.kernel.geom.Rectangle
import com.itextpdf.kernel.pdf.PdfDocument
import com.itextpdf.kernel.pdf.PdfPage
import com.itextpdf.kernel.pdf.PdfWriter
import com.itextpdf.kernel.pdf.action.PdfAction
import com.itextpdf.kernel.pdf.canvas.PdfCanvas
import com.itextpdf.kernel.pdf.navigation.PdfExplicitDestination
import com.itextpdf.layout.Canvas
import com.itextpdf.layout.Document
import com.itextpdf.layout.element.AreaBreak
import com.itextpdf.layout.element.Paragraph
import com.itextpdf.layout.element.Text
import com.itextpdf.layout.properties.AreaBreakType
import com.itextpdf.layout.properties.TextAlignment
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

object PdfGenerator {

    // A5 dimensions in points
    private val PAGE_SIZE = PageSize.A5
    private const val MARGIN_TOP = 36f
    private const val MARGIN_BOTTOM = 40f
    private const val MARGIN_LEFT = 30f
    private const val NOTE_COL_W = 95f
    private const val GUTTER = 10f
    private val TEXT_COL_W get() = PAGE_SIZE.width - MARGIN_LEFT - 24f - NOTE_COL_W - GUTTER

    private val gray100 = DeviceRgb(0.1f, 0.1f, 0.1f)
    private val gray50 = DeviceRgb(0.5f, 0.5f, 0.5f)
    private val gray70 = DeviceRgb(0.7f, 0.7f, 0.7f)
    private val gray85 = DeviceRgb(0.85f, 0.85f, 0.85f)

    fun generate(articles: List<Article>, bookTitle: String, outputFile: File) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val writer = PdfWriter(outputFile)
        val pdfDoc = PdfDocument(writer)
        val doc = Document(pdfDoc, PAGE_SIZE)
        doc.setMargins(MARGIN_TOP, 24f, MARGIN_BOTTOM, MARGIN_LEFT)

        val helvetica = PdfFontFactory.createFont(StandardFonts.HELVETICA)
        val helveticaBold = PdfFontFactory.createFont(StandardFonts.HELVETICA_BOLD)
        val helveticaOblique = PdfFontFactory.createFont(StandardFonts.HELVETICA_OBLIQUE)

        // Page event handler for margins, notes column, page numbers
        val pageHandler = PageHandler(bookTitle, helvetica, helveticaBold)
        pdfDoc.addEventHandler(PdfDocumentEvent.END_PAGE, pageHandler)

        // ── TOC page (page 1) ─────────────────────────────────────────────────
        // Book title
        doc.add(Paragraph(bookTitle)
            .setFont(helveticaBold).setFontSize(20f)
            .setFontColor(gray100)
            .setMarginBottom(6f))

        doc.add(Paragraph(today)
            .setFont(helvetica).setFontSize(8f)
            .setFontColor(gray50)
            .setMarginBottom(16f))

        doc.add(Paragraph("CONTENTS")
            .setFont(helveticaBold).setFontSize(8f)
            .setFontColor(gray50)
            .setMarginBottom(10f))

        // TOC entries (we'll add them as links after we know page numbers)
        val tocPageRefs = mutableListOf<Int>() // article start pages

        // ── Article pages ─────────────────────────────────────────────────────
        articles.forEachIndexed { idx, article ->
            doc.add(AreaBreak(AreaBreakType.NEXT_PAGE))
            tocPageRefs.add(pdfDoc.numberOfPages)

            // ← Contents link
            doc.add(Paragraph("← Contents")
                .setFont(helvetica).setFontSize(7f)
                .setFontColor(gray50)
                .setMarginBottom(4f))

            // Domain
            try {
                val domain = java.net.URL(article.url).host
                doc.add(Paragraph(domain)
                    .setFont(helvetica).setFontSize(7f)
                    .setFontColor(gray70)
                    .setMarginBottom(6f))
            } catch (e: Exception) {}

            // Divider
            addDivider(doc, pdfDoc)

            // Article title
            doc.add(Paragraph(article.title)
                .setFont(helveticaBold).setFontSize(13f)
                .setFontColor(gray100)
                .setMarginBottom(8f)
                .setWidth(TEXT_COL_W))

            addDivider(doc, pdfDoc)

            // Paragraphs
            article.plainText.split("\n\n").forEach { para ->
                val text = para.trim()
                if (text.isNotEmpty()) {
                    doc.add(Paragraph(text)
                        .setFont(helvetica).setFontSize(8f)
                        .setFontColor(gray100)
                        .setMarginBottom(3f)
                        .setMultipliedLeading(1.55f)
                        .setWidth(TEXT_COL_W))
                }
            }
        }

        // ── Go back to page 1 and add TOC links ──────────────────────────────
        // We use direct canvas drawing for TOC entries since we need page links
        if (pdfDoc.numberOfPages >= 1) {
            val tocPage = pdfDoc.getPage(1)
            val canvas = PdfCanvas(tocPage)
            val pageW = PAGE_SIZE.width
            var y = PAGE_SIZE.height - MARGIN_TOP - 80f // below title/date/CONTENTS

            articles.forEachIndexed { i, article ->
                val targetPage = if (i < tocPageRefs.size) tocPageRefs[i] else 2
                val num = String.format("%02d", i + 1)
                val title = "$num  ${article.title}"
                val pageNum = targetPage.toString()

                // Page number on right
                canvas.beginText()
                    .setFontAndSize(helvetica, 8f)
                    .setColor(gray50, true)
                    .moveText(MARGIN_LEFT + TEXT_COL_W - 5f, y)
                    .showText(pageNum)
                    .endText()

                // Title text
                canvas.beginText()
                    .setFontAndSize(helvetica, 9f)
                    .setColor(gray100, true)
                    .moveText(MARGIN_LEFT.toFloat(), y)
                    .showText(title.take(52))
                    .endText()

                // Dotted leader
                canvas.setStrokeColor(gray85)
                var dotX = MARGIN_LEFT + (title.take(52).length * 4.5f) + 4f
                val dotEnd = MARGIN_LEFT + TEXT_COL_W - 20f
                while (dotX < dotEnd) {
                    canvas.beginText()
                        .setFontAndSize(helvetica, 7f)
                        .setColor(gray85, true)
                        .moveText(dotX, y)
                        .showText(".")
                        .endText()
                    dotX += 4.5f
                }

                y -= 16f
                if (y < MARGIN_BOTTOM) break
            }
            canvas.release()
        }

        doc.close()
    }

    private fun addDivider(doc: Document, pdfDoc: PdfDocument) {
        // Add a thin rule using a zero-height paragraph with a border
        doc.add(Paragraph("")
            .setMarginTop(2f).setMarginBottom(6f)
            .setBorderBottom(com.itextpdf.layout.borders.SolidBorder(gray85, 0.3f))
            .setWidth(TEXT_COL_W))
    }

    // Draws the vertical line, NOTES label, and page number on every page
    private class PageHandler(
        private val bookTitle: String,
        private val font: com.itextpdf.kernel.font.PdfFont,
        private val boldFont: com.itextpdf.kernel.font.PdfFont
    ) : IEventHandler {
        override fun handleEvent(event: Event) {
            val docEvent = event as PdfDocumentEvent
            val pdfDoc = docEvent.document
            val page = docEvent.page
            val pageNum = pdfDoc.getPageNumber(page)
            val pageSize = page.pageSize
            val pageW = pageSize.width
            val pageH = pageSize.height
            val noteColX = MARGIN_LEFT + TEXT_COL_W + GUTTER

            val canvas = PdfCanvas(page)
            val gray85rgb = DeviceRgb(0.85f, 0.85f, 0.85f)
            val gray70rgb = DeviceRgb(0.7f, 0.7f, 0.7f)
            val gray50rgb = DeviceRgb(0.5f, 0.5f, 0.5f)

            // Vertical line separating text col from notes
            canvas.setStrokeColor(gray85rgb)
                .setLineWidth(0.3f)
                .moveTo(noteColX - 5.0, MARGIN_BOTTOM.toDouble())
                .lineTo(noteColX - 5.0, (pageH - MARGIN_TOP).toDouble())
                .stroke()

            // "NOTES" label at top of note column
            canvas.beginText()
                .setFontAndSize(font, 6f)
                .setColor(gray70rgb, true)
                .moveText(noteColX.toDouble(), (pageH - MARGIN_TOP - 8).toDouble())
                .showText("NOTES")
                .endText()

            // Page number centered at bottom
            canvas.beginText()
                .setFontAndSize(font, 7f)
                .setColor(gray50rgb, true)
                .moveText((pageW / 2 - 5).toDouble(), 18.0)
                .showText(pageNum.toString())
                .endText()

            canvas.release()
        }
    }
}
