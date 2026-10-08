package de.ahnsen.kartentrainer

import android.content.Context
import coil.ImageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object CardImageCache {
    private var loader: ImageLoader? = null

    private fun imageLoader(context: Context): ImageLoader {
        return loader ?: ImageLoader.Builder(context.applicationContext)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
            .also { loader = it }
    }

    suspend fun prefetch(context: Context, card: CardData): Boolean = withContext(Dispatchers.IO) {
        val url = card.imageUrl ?: return@withContext false
        val request = ImageRequest.Builder(context.applicationContext)
            .data(url)
            .diskCachePolicy(CachePolicy.ENABLED)
            .memoryCachePolicy(CachePolicy.ENABLED)
            .networkCachePolicy(CachePolicy.ENABLED)
            .build()
        runCatching {
            imageLoader(context).execute(request)
        }.isSuccess
    }

    suspend fun prefetch(context: Context, cards: Collection<CardData>): Int {
        var loaded = 0
        cards.distinctBy { it.id }.forEach { card ->
            if (prefetch(context, card)) loaded += 1
        }
        return loaded
    }
}
