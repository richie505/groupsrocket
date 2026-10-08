package com.appsc.prep.desktop

import com.appsc.prep.data.Storage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.floatOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.util.concurrent.Executors

/** Progress kept in one JSON file; every change is written in the background, replacing the file whole. */
class FileStorage(private val file: Path) : Storage {
    private val values = HashMap<String, JsonElement>()
    private val writer = Executors.newSingleThreadExecutor { Thread(it, "save-progress").apply { isDaemon = true } }

    init {
        runCatching { values += Json.parseToJsonElement(Files.readString(file)).jsonObject }
        // Let the last save finish when the window closes.
        Runtime.getRuntime().addShutdownHook(Thread {
            writer.shutdown()
            writer.awaitTermination(5, java.util.concurrent.TimeUnit.SECONDS)
        })
    }

    override fun getStringSet(key: String): Set<String> =
        synchronized(values) { values[key] as? JsonArray }?.map { it.jsonPrimitive.content }?.toSet() ?: emptySet()

    override fun getString(key: String): String? =
        synchronized(values) { values[key] as? JsonPrimitive }?.content

    override fun getFloat(key: String, default: Float): Float =
        synchronized(values) { values[key] as? JsonPrimitive }?.floatOrNull ?: default

    override fun putStringSet(key: String, value: Set<String>) = put(key, JsonArray(value.map(::JsonPrimitive)))
    override fun putString(key: String, value: String) = put(key, JsonPrimitive(value))
    override fun putFloat(key: String, value: Float) = put(key, JsonPrimitive(value))

    private fun put(key: String, value: JsonElement) {
        val snapshot = synchronized(values) {
            values[key] = value
            JsonObject(HashMap(values))
        }
        writer.execute {
            val tmp = file.resolveSibling("${file.fileName}.tmp")
            Files.writeString(tmp, snapshot.toString())
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
        }
    }
}
