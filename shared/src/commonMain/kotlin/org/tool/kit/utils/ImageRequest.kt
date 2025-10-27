package org.tool.kit.utils

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.Options
import coil3.size.Size
import org.jetbrains.skia.Bitmap

fun getImageRequest(data: Any?) =
    ImageRequest.Builder(PlatformContext.INSTANCE)
        .data(data = data)
        .size(Size.ORIGINAL)
        .memoryCachePolicy(CachePolicy.DISABLED)
        .networkCachePolicy(CachePolicy.DISABLED)
        .diskCachePolicy(CachePolicy.DISABLED)
        .fetcherFactory(SkiaBitmapFetcher.Factory())
        .build()

class SkiaBitmapFetcher(
    private val data: Bitmap
) : Fetcher {
    override suspend fun fetch(): FetchResult {
        return ImageFetchResult(
            image = data.asImage(),
            isSampled = false,
            dataSource = DataSource.MEMORY,
        )
    }

    class Factory : Fetcher.Factory<Bitmap> {
        override fun create(data: Bitmap, options: Options, imageLoader: ImageLoader): Fetcher {
            return SkiaBitmapFetcher(data)
        }
    }
}