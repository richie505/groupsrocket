package com.appsc.prep.platform

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import com.appsc.prep.data.Storage
import com.appsc.prep.ui.components.Platform

class PrefsStorage(context: Context) : Storage {
    private val prefs = context.getSharedPreferences("progress", Context.MODE_PRIVATE)

    override fun getStringSet(key: String): Set<String> = prefs.getStringSet(key, emptySet())!!.toSet()
    override fun getString(key: String): String? = prefs.getString(key, null)
    override fun getFloat(key: String, default: Float) = prefs.getFloat(key, default)
    override fun putStringSet(key: String, value: Set<String>) = prefs.edit().putStringSet(key, value).apply()
    override fun putString(key: String, value: String) = prefs.edit().putString(key, value).apply()
    override fun putFloat(key: String, value: Float) = prefs.edit().putFloat(key, value).apply()
}

class AndroidPlatform(private val context: Context) : Platform {
    override val desktop = false

    override val speech: ReadAloud get() = ReadAloud.init(context)

    override fun share(title: String, text: String) {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, title)
            putExtra(Intent.EXTRA_TEXT, text)
        }
        context.startActivity(Intent.createChooser(send, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    @Composable
    override fun BackHandler(enabled: Boolean, onBack: () -> Unit) =
        androidx.activity.compose.BackHandler(enabled, onBack)

    @Composable
    override fun Shortcuts(onKey: (String) -> Boolean) = Unit
}
