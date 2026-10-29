package com.lss.lotusaloud

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore

/** Finds your newest screenshot (for double-tapping the lotus bubble). */
object Screenshots {

    /** The permission Android needs before an app can see your screenshots. */
    fun permission(): String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_IMAGES
        else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canRead(ctx: Context): Boolean =
        ctx.checkSelfPermission(permission()) == PackageManager.PERMISSION_GRANTED

    /** Newest image in a Screenshots folder, or null. */
    fun latest(ctx: Context): Uri? {
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        val (where, column) = if (Build.VERSION.SDK_INT >= 29) {
            "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to MediaStore.Images.Media.RELATIVE_PATH
        } else {
            @Suppress("DEPRECATION")
            "${MediaStore.Images.Media.DATA} LIKE ?" to MediaStore.Images.Media.DATA
        }
        return try {
            ctx.contentResolver.query(
                collection,
                arrayOf(MediaStore.Images.Media._ID, column),
                where,
                arrayOf("%Screenshots%"),
                "${MediaStore.Images.Media.DATE_ADDED} DESC",
            )?.use { c ->
                if (c.moveToFirst()) ContentUris.withAppendedId(collection, c.getLong(0)) else null
            }
        } catch (e: Exception) {
            null
        }
    }
}
