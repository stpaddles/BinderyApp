package com.nso.bindery

data class Article(
    val id: Long = System.currentTimeMillis(),
    val url: String,
    val title: String,
    val content: String,      // cleaned HTML
    val plainText: String,    // plain text paragraphs
    val status: Status = Status.DONE
) {
    enum class Status { FETCHING, DONE, ERROR }
}
