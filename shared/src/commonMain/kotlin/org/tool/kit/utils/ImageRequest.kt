package org.tool.kit.utils

import androidx.compose.ui.graphics.ImageBitmap
import coil3.request.ImageRequest

// Request construction only; Coil owns loading and decoding.
expect fun getImageRequest(data: ImageBitmap): ImageRequest
expect fun getFileImageRequest(path: String): ImageRequest
