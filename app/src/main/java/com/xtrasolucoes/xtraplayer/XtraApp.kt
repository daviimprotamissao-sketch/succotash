// xTra Player - Autoria: xTra Soluções (todos os direitos reservados)
package com.xtrasolucoes.xtraplayer

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache

/** Coil com cache em memória/disco dimensionado: logos e pôsteres abrem instantaneamente na 2ª vez. */
class XtraApp : Application(), ImageLoaderFactory {
    override fun onCreate() {
        super.onCreate()
        if (FirebaseApp.getApps(this).isEmpty()) {
            val options = FirebaseOptions.Builder()
                .setApiKey("AIzaSyAuaApXF6JvKaqaULv22oDiJwIBI6wJncc")
                .setApplicationId("1:296794667697:android:686707b8bca9a734bc0090")
                .setProjectId("xtrasoluctions")
                .setDatabaseUrl("https://xtrasoluctions-default-rtdb.europe-west1.firebasedatabase.app")
                .setGcmSenderId("296794667697")
                .build()
            FirebaseApp.initializeApp(this, options)
        }
    }

    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("img")).maxSizeBytes(150L * 1024 * 1024).build() }
        .allowRgb565(true)
        .respectCacheHeaders(false)
        .crossfade(150)
        .build()
}
