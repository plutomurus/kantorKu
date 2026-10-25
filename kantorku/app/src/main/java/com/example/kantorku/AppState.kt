package com.example.kantorku

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

data class Recent(val uri: String, val name: String)

class AppState(context: Context) {
    private val ctx = context.applicationContext
    private val prefs = ctx.getSharedPreferences("kantorku", Context.MODE_PRIVATE)

    var tab by mutableIntStateOf(0)
    var text by mutableStateOf("")
    var pdf by mutableStateOf<PdfDoc?>(null)
    val cells = mutableStateMapOf<String, String>()
    val recents = mutableStateListOf<Recent>()

    init {
        (prefs.getString("recents", "") ?: "").split("\n").forEach { line ->
            val p = line.split("\t", limit = 2)
            if (p.size == 2) recents.add(Recent(uri = p[1], name = p[0]))
        }
    }

    private fun displayName(uri: Uri): String =
        runCatching {
            ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
                if (it.moveToFirst()) it.getString(0) else null
            }
        }.getOrNull() ?: uri.lastPathSegment ?: "berkas"

    fun openUri(uri: Uri) {
        val name = displayName(uri)
        val lower = name.lowercase()
        try {
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            when {
                lower.endsWith(".pdf") -> {
                    val d = PdfDoc(ctx, uri)
                    pdf?.close()
                    pdf = d
                    tab = 1
                }
                lower.endsWith(".csv") -> {
                    cells.clear()
                    cells.putAll(parseCsv(readText(ctx, uri)))
                    tab = 3
                }
                lower.endsWith(".txt") || lower.endsWith(".md") -> {
                    text = readText(ctx, uri)
                    tab = 2
                }
                else -> {
                    toast(ctx, "Format belum didukung: $name (saat ini PDF, TXT, MD, CSV)")
                    return
                }
            }
            addRecent(Recent(uri.toString(), name))
        } catch (e: Exception) {
            toast(ctx, "Gagal membuka: ${e.message}")
        }
    }

    private fun addRecent(r: Recent) {
        recents.removeAll { it.uri == r.uri }
        recents.add(0, r)
        while (recents.size > 10) recents.removeAt(recents.size - 1)
        prefs.edit().putString(
            "recents",
            recents.joinToString("\n") { it.name.replace('\t', ' ').replace('\n', ' ') + "\t" + it.uri }
        ).apply()
    }

    fun close() {
        pdf?.close()
    }
}
