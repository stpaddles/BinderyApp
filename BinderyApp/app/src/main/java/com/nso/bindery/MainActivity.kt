package com.nso.bindery

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.nso.bindery.databinding.ActivityMainBinding
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var viewModel: MainViewModel
    private lateinit var adapter: ArticleAdapter

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)

        viewModel = ViewModelProvider(this)[MainViewModel::class.java]

        // Setup RecyclerView
        adapter = ArticleAdapter(emptyList()) { id -> viewModel.removeArticle(id) }
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        // Observe articles
        viewModel.articles.observe(this) { articles ->
            adapter.update(articles)
            binding.emptyState.visibility = if (articles.isEmpty()) android.view.View.VISIBLE else android.view.View.GONE
            binding.recyclerView.visibility = if (articles.isEmpty()) android.view.View.GONE else android.view.View.VISIBLE
            val doneCount = articles.count { it.status == Article.Status.DONE }
            binding.btnGenerateEpub.isEnabled = doneCount > 0
            binding.btnGeneratePdf.isEnabled = doneCount > 0
            binding.btnGenerateEpub.text = if (doneCount > 0) "Generate EPUB ($doneCount)" else "Generate EPUB"
            binding.btnGeneratePdf.text = if (doneCount > 0) "Generate PDF ($doneCount)" else "Generate PDF"
        }

        // Observe status
        viewModel.status.observe(this) { msg ->
            if (msg.isNotEmpty()) {
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }

        // Observe generating state
        viewModel.isGenerating.observe(this) { generating ->
            binding.btnGenerateEpub.isEnabled = !generating && (viewModel.articles.value?.any { it.status == Article.Status.DONE } == true)
            binding.btnGeneratePdf.isEnabled = !generating && (viewModel.articles.value?.any { it.status == Article.Status.DONE } == true)
            binding.progressBar.visibility = if (generating) android.view.View.VISIBLE else android.view.View.GONE
        }

        // Generate buttons
        binding.btnGenerateEpub.setOnClickListener { promptTitleAndGenerate("epub") }
        binding.btnGeneratePdf.setOnClickListener { promptTitleAndGenerate("pdf") }

        // Handle incoming share intent
        handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT) ?: return
            // Extract URL from shared text
            val urlRegex = Regex("https?://[^\\s]+")
            val url = urlRegex.find(sharedText)?.value ?: sharedText.trim()
            if (url.startsWith("http")) {
                viewModel.addUrl(url)
            } else {
                Toast.makeText(this, "Could not find a URL in shared text", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun promptTitleAndGenerate(format: String) {
        val today = java.text.SimpleDateFormat("EEEE", java.util.Locale.US).format(java.util.Date())
        val input = android.widget.EditText(this).apply {
            setText(today)
            hint = "Book title"
        }

        MaterialAlertDialogBuilder(this)
            .setTitle("Name your $format".uppercase())
            .setView(input)
            .setPositiveButton("Generate") { _, _ ->
                val title = input.text.toString().trim().ifEmpty { today }
                val outputDir = getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: filesDir

                if (format == "epub") {
                    viewModel.generateEpub(title, outputDir) { file ->
                        file?.let { shareFile(it, "application/epub+zip") }
                    }
                } else {
                    viewModel.generatePdf(title, outputDir) { file ->
                        file?.let { shareFile(it, "application/pdf") }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun shareFile(file: File, mimeType: String) {
        val uri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", file)
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share ${file.name}"))
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main_menu, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_clear -> {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Clear all articles?")
                    .setMessage("This will remove all articles from your queue.")
                    .setPositiveButton("Clear") { _, _ -> viewModel.clearAll() }
                    .setNegativeButton("Cancel", null)
                    .show()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
}
