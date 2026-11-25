package com.hhst.youtubelite.tvlink

import android.content.Context
import com.hhst.youtubelite.core.CredentialCipher
import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Pairing keys on disk. The phone and the TV role keep separate files, so one install can be
 * both (the same APK runs on each). Keys are sealed with the Android Keystore key the app
 * already uses for sign-in data, and stay out of backups with it.
 */
internal class AndroidTvLinkStore(context: Context, role: String) : TvLinkStore {
    private val prefs = context.applicationContext.getSharedPreferences("tv-link-$role", Context.MODE_PRIVATE)

    @Serializable private data class Stored(val name: String, val secret: String)

    @Synchronized override fun selfId(): String = prefs.getString(KEY_ID, null) ?: UUID.randomUUID().toString().also {
        prefs.edit().putString(KEY_ID, it).apply()
    }

    @Synchronized override fun secret(peerId: String): ByteArray? =
        read()[peerId]?.let { CredentialCipher.decrypt(it.secret) }?.let(Hex::decodeOrNull)

    @Synchronized override fun save(peerId: String, name: String, secret: ByteArray) =
        write(read() + (peerId to Stored(name, CredentialCipher.encrypt(Hex.encode(secret)))))

    @Synchronized override fun remove(peerId: String) = write(read() - peerId)

    @Synchronized override fun peers(): List<TvLinkStore.Peer> = read().map { (id, stored) -> TvLinkStore.Peer(id, stored.name) }

    private fun read(): Map<String, Stored> = prefs.getString(KEY_PEERS, null)?.let {
        runCatching { json.decodeFromString<Map<String, Stored>>(it) }.getOrNull()
    }.orEmpty()

    private fun write(peers: Map<String, Stored>) {
        prefs.edit().putString(KEY_PEERS, json.encodeToString(peers)).apply()
    }

    private companion object {
        const val KEY_ID = "self-id"
        const val KEY_PEERS = "peers"
        val json = Json { ignoreUnknownKeys = true }
    }
}
