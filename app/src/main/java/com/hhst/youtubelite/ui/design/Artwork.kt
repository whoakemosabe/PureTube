package com.hhst.youtubelite.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.hhst.youtubelite.R

/** What [Artwork] draws for an asset string: a remote image, a bundled sample, or nothing. */
internal sealed interface ArtworkSource {
    data class Remote(val url: String) : ArtworkSource
    data class Sample(val id: String) : ArtworkSource
    data object Blank : ArtworkSource
}

/** The bundled sample pictures exist only for the offline fixture ids, never as a fallback. */
internal val SampleArtworkIds = setOf("bunny", "sintel", "dream")

internal fun artworkSource(asset: String): ArtworkSource {
    val trimmed = asset.trim()
    return when {
        trimmed.startsWith("https://") -> ArtworkSource.Remote(trimmed)
        trimmed.startsWith("http://") -> ArtworkSource.Remote("https://" + trimmed.removePrefix("http://"))
        trimmed.startsWith("//") -> ArtworkSource.Remote("https:$trimmed")
        trimmed in SampleArtworkIds -> ArtworkSource.Sample(trimmed)
        else -> ArtworkSource.Blank
    }
}

@Composable
fun Artwork(asset: String, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop, fallback: String? = null) {
    when (val source = artworkSource(asset)) {
        is ArtworkSource.Remote -> com.hhst.youtubelite.ui.YoutubeThumb(source.url,
            modifier, contentScale = contentScale, placeholderColor = DesignTokens.Surface,
            fallbackUrl = fallback?.let { (artworkSource(it) as? ArtworkSource.Remote)?.url })
        is ArtworkSource.Sample -> Image(
            painterResource(when (source.id) {
                "dream" -> R.drawable.sample_dream
                "sintel" -> R.drawable.sample_sintel
                else -> R.drawable.sample_bunny
            }),
            contentDescription = null,
            modifier = modifier,
            contentScale = contentScale,
        )
        ArtworkSource.Blank -> Box(modifier.background(DesignTokens.Surface))
    }
}
