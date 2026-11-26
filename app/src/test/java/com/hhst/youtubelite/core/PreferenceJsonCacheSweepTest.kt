package com.hhst.youtubelite.core

import android.content.SharedPreferences
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class PreferenceJsonCacheSweepTest {
    /** A map behind SharedPreferences: reads, putString, remove and apply/commit. */
    private fun store(values: MutableMap<String, Any?>): PreferenceStore {
        lateinit var editor: SharedPreferences.Editor
        editor = Proxy.newProxyInstance(SharedPreferences.Editor::class.java.classLoader, arrayOf(SharedPreferences.Editor::class.java)) { _, method, args ->
            when (method.name) {
                "putString" -> { values[args[0] as String] = args[1]; editor }
                "remove" -> { values.remove(args[0] as String); editor }
                "commit" -> true
                "apply" -> Unit
                else -> editor
            }
        } as SharedPreferences.Editor
        val prefs = Proxy.newProxyInstance(SharedPreferences::class.java.classLoader, arrayOf(SharedPreferences::class.java)) { _, method, args ->
            when (method.name) {
                "getAll" -> values.toMap()
                "getString" -> values[args[0] as String] as? String ?: args[1]
                "contains" -> values.containsKey(args[0] as String)
                "edit" -> editor
                else -> null
            }
        } as SharedPreferences
        return PreferenceStore.of(object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int) = prefs
        }, "media-cache-test")
    }

    @Test
    fun sweepDropsExpiredAndBrokenEntriesAndKeepsLiveOnes() {
        val values = mutableMapOf<String, Any?>()
        val cache = PreferenceJsonCache(store(values))
        values["extractor:stream:live"] = """{"until":60000,"str":"kept"}"""
        values["extractor:stream:old"] = """{"until":1000,"str":"gone"}"""
        values["broken"] = "not json"
        assertEquals(2, cache.sweepExpired(now = 5_000))
        assertNull(values["extractor:stream:old"])
        assertEquals(setOf("extractor:stream:live"), values.keys)
    }
}
