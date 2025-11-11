package com.thewizrd.simplewear.helpers

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.database.getBlobOrNull

object ContactsHelper {
    fun getContactPhotoData(context: Context, photoUri: Uri): ByteArray? {
        val contentResolver = context.contentResolver

        val cursor = contentResolver.query(
            photoUri,
            arrayOf(ContactsContract.CommonDataKinds.Photo.PHOTO),
            null, null, null
        )

        return cursor?.use {
            if (!it.moveToNext()) {
                null
            } else {
                it.getBlobOrNull(0)
            }
        }
    }
}