/*
 * MIT License
 * Copyright (c) 2026 Zhyllan Fyllah (Jirankun) - Aokaze Studio
 */

package net.aokaze.osupanel.feature.cardgen

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import net.aokaze.osupanel.R
import java.io.File
import java.io.FileOutputStream

/**
 * Share the generated card through the SYSTEM share sheet — no storage
 * permission needed. The PNG is written to the app's cache dir and exposed
 * via [FileProvider] with a temporary read grant.
 */
object CardShare {

    /** Save the bitmap into `cacheDir/cardgen/` and return its content Uri. */
    fun save(context: Context, bitmap: Bitmap, fileName: String): Uri? {
        return runCatching {
            val dir = File(context.cacheDir, "cardgen").apply { mkdirs() }
            val file = File(dir, fileName)
            FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        }.getOrNull()
    }

    /** Open the Android share sheet (ACTION_SEND, image/png) for [uri]. */
    fun share(context: Context, uri: Uri) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            // Some apps only honor the grant from ClipData — set both.
            clipData = ClipData.newRawUri(null, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(intent, context.getString(R.string.cardgen_share_title)),
        )
    }
}
