package com.nso.bindery

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ArticleAdapter(
    private var articles: List<Article>,
    private val onRemove: (Long) -> Unit
) : RecyclerView.Adapter<ArticleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val number: TextView = view.findViewById(R.id.tvNumber)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val domain: TextView = view.findViewById(R.id.tvDomain)
        val status: View = view.findViewById(R.id.statusDot)
        val remove: ImageButton = view.findViewById(R.id.btnRemove)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_article, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val article = articles[position]
        holder.number.text = String.format("%02d", position + 1)
        holder.title.text = article.title
        try {
            holder.domain.text = java.net.URL(article.url).host
        } catch (e: Exception) {
            holder.domain.text = article.url.take(40)
        }

        val color = when (article.status) {
            Article.Status.DONE -> 0xFF7AB89A.toInt()
            Article.Status.FETCHING -> 0xFFC9A96E.toInt()
            Article.Status.ERROR -> 0xFFC97070.toInt()
        }
        holder.status.setBackgroundColor(color)
        holder.remove.setOnClickListener { onRemove(article.id) }
    }

    override fun getItemCount() = articles.size

    fun update(newArticles: List<Article>) {
        articles = newArticles
        notifyDataSetChanged()
    }
}
