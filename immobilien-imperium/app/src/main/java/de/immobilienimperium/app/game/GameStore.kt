package de.immobilienimperium.app.game

import android.content.Context
import android.util.Base64
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

object GameStore {
    private const val PREFS = "immobilien_imperium_save"
    private const val KEY = "game_state_v1"

    fun save(context: Context, state: GameState) {
        runCatching {
            val bytes = ByteArrayOutputStream().use { bos ->
                ObjectOutputStream(bos).use { it.writeObject(state) }
                bos.toByteArray()
            }
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit().putString(KEY, Base64.encodeToString(bytes, Base64.NO_WRAP)).apply()
        }
    }

    fun load(context: Context): GameState? = runCatching {
        val text = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return null
        val bytes = Base64.decode(text, Base64.NO_WRAP)
        ObjectInputStream(ByteArrayInputStream(bytes)).use { it.readObject() as GameState }
    }.getOrNull()

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
    }
}
