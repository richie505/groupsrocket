package com.appsc.prep.data

/** Small key-value store behind ProgressStore: SharedPreferences on Android, a file on Windows. */
interface Storage {
    fun getStringSet(key: String): Set<String>
    fun getString(key: String): String?
    fun getFloat(key: String, default: Float): Float
    fun putStringSet(key: String, value: Set<String>)
    fun putString(key: String, value: String)
    fun putFloat(key: String, value: Float)
}
