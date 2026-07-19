package com.nso.bindery

import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

object EpubGenerator {

    fun generate(articles: List<Article>, bookTitle: String, outputFile: File) {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val uid = "bindery-${System.currentTimeMillis()}"

        ZipOutputStream(FileOutputStream(outputFile)).use { zip ->

            // mimetype — must be first, uncompressed
            zip.setMethod(ZipOutputStream.STORED)
            val mimetypeBytes = "application/epub+zip".toByteArray()
            val mimetypeEntry = ZipEntry("mimetype")
            mimetypeEntry.size = mimetypeBytes.size.toLong()
            mimetypeEntry.compressedSize = mimetypeBytes.size.toLong()
            mimetypeEntry.crc = java.util.zip.CRC32().also { it.update(mimetypeBytes) }.value
            zip.putNextEntry(mimetypeEntry)
            zip.write(mimetypeBytes)
            zip.closeEntry()

            zip.setMethod(ZipOutputStream.DEFLATED)

            // META-INF/container.xml
            zip.putNextEntry(ZipEntry("META-INF/container.xml"))
            zip.write("""<?xml version="1.0"?>
<container version="1.0" xmlns="urn:oasis:names:tc:opendocument:xmlns:container">
  <rootfiles>
    <rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
  </rootfiles>
</container>""".toByteArray())
            zip.closeEntry()

            // CSS
            zip.putNextEntry(ZipEntry("OEBPS/style.css"))
            zip.write("""
body { font-family: Georgia, serif; font-size: 1em; line-height: 1.75; margin: 1em 1.8em; color: #111; }
h1 { font-size: 1.5em; line-height: 1.3; margin-bottom: 0.4em; }
h2 { font-size: 1.2em; margin-top: 1.5em; }
h3 { font-size: 1.05em; margin-top: 1.2em; }
p { margin: 0.7em 0; }
blockquote { margin: 1em 0; padding-left: 1em; border-left: 3px solid #ccc; color: #555; font-style: italic; }
.source { font-size: 0.78em; color: #888; font-style: italic; margin-bottom: 1.5em; }
.back-link { font-size: 0.85em; margin-bottom: 1.2em; }
.back-link-bottom { font-size: 0.85em; margin-top: 2em; padding-top: 1em; border-top: 1px solid #ddd; }
a { color: #333; }
nav ol { padding-left: 1.2em; }
nav li { margin: 0.6em 0; font-size: 1.05em; line-height: 1.4; }
""".toByteArray())
            zip.closeEntry()

            // Article files
            val tocItems = mutableListOf<Pair<String, String>>() // filename -> title
            articles.forEachIndexed { i, article ->
                val filename = "article${i + 1}.xhtml"
                tocItems.add(filename to article.title)
                zip.putNextEntry(ZipEntry("OEBPS/$filename"))
                zip.write(buildArticleXhtml(article).toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }

            // nav.xhtml (TOC)
            zip.putNextEntry(ZipEntry("OEBPS/nav.xhtml"))
            val navItems = tocItems.joinToString("\n        ") { (fn, title) ->
                "<li><a href=\"$fn\">${title.escXml()}</a></li>"
            }
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xmlns:epub="http://www.idpf.org/2007/ops" xml:lang="en">
<head><meta charset="UTF-8"/><title>Contents</title><link rel="stylesheet" href="style.css"/></head>
<body>
<nav epub:type="toc">
  <h1>${bookTitle.escXml()}</h1>
  <ol>
    $navItems
  </ol>
</nav>
</body>
</html>""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // toc.ncx
            zip.putNextEntry(ZipEntry("OEBPS/toc.ncx"))
            val navPoints = tocItems.mapIndexed { i, (fn, title) -> """
    <navPoint id="np${i+1}" playOrder="${i+2}">
      <navLabel><text>${title.escXml()}</text></navLabel>
      <content src="$fn"/>
    </navPoint>""" }.joinToString("")
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/" version="2005-1">
<head><meta name="dtb:uid" content="$uid"/></head>
<docTitle><text>${bookTitle.escXml()}</text></docTitle>
<navMap>
  <navPoint id="np0" playOrder="1">
    <navLabel><text>Table of Contents</text></navLabel>
    <content src="nav.xhtml"/>
  </navPoint>$navPoints
</navMap>
</ncx>""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()

            // content.opf
            val manifestItems = tocItems.mapIndexed { i, (fn, _) ->
                """  <item id="article${i+1}" href="$fn" media-type="application/xhtml+xml"/>"""
            }.joinToString("\n")
            val spineItems = tocItems.indices.joinToString("\n  ") { i ->
                """<itemref idref="article${i+1}"/>"""
            }
            zip.putNextEntry(ZipEntry("OEBPS/content.opf"))
            zip.write("""<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" version="3.0" unique-identifier="uid">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/">
  <dc:identifier id="uid">$uid</dc:identifier>
  <dc:title>${bookTitle.escXml()}</dc:title>
  <dc:language>en</dc:language>
  <dc:date>$today</dc:date>
  <meta property="dcterms:modified">${SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).format(Date())}</meta>
</metadata>
<manifest>
  <item id="nav" href="nav.xhtml" media-type="application/xhtml+xml" properties="nav"/>
  <item id="ncx" href="toc.ncx" media-type="application/x-dtbncx+xml"/>
  <item id="css" href="style.css" media-type="text/css"/>
$manifestItems
</manifest>
<spine toc="ncx">
  <itemref idref="nav"/>
  $spineItems
</spine>
</package>""".toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    private fun buildArticleXhtml(article: Article): String {
        return """<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE html>
<html xmlns="http://www.w3.org/1999/xhtml" xml:lang="en">
<head><meta charset="UTF-8"/><title>${article.title.escXml()}</title>
<link rel="stylesheet" href="style.css"/>
</head>
<body>
<p class="back-link"><a href="nav.xhtml">&#8592; Table of Contents</a></p>
<p class="source">Source: ${article.url.escXml()}</p>
${article.content}
<p class="back-link-bottom"><a href="nav.xhtml">&#8592; Back to Table of Contents</a></p>
</body>
</html>"""
    }

    private fun String.escXml() = this
        .replace("&", "&amp;").replace("<", "&lt;")
        .replace(">", "&gt;").replace("\"", "&quot;")
}
