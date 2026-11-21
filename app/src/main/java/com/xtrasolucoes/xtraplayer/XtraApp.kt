// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
package com.xtrasolucoes.xtraplayer

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Coil com cache em memória/disco dimensionado: logos e pôsteres abrem instantaneamente na 2ª vez. */
class XtraApp : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
        .allowRgb565(true)
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()
}
