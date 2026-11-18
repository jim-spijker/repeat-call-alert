package io.github.jimspijker.repeatcallalert.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract.PhoneLookup
import io.github.jimspijker.repeatcallalert.logic.CallerType

/**
 * Looks up whether a number belongs to a contact and whether that contact is starred (a favourite).
 * Uses the official PhoneLookup API, which matches numbers the same way the Phone app does.
 * Only the STARRED flag is read: no names or other contact details.
 */
object ContactLookup {

    fun hasPermission(context: Context): Boolean =
        context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    /** Returns `null` when the lookup is not possible (no permission or no number). */
    fun callerType(context: Context, rawNumber: String?): CallerType? {
        if (rawNumber.isNullOrBlank() || !hasPermission(context)) return null
        val uri = Uri.withAppendedPath(PhoneLookup.CONTENT_FILTER_URI, Uri.encode(rawNumber))
        return runCatching {
            context.contentResolver.query(uri, arrayOf(PhoneLookup.STARRED), null, null, null)?.use { c ->
                var type = CallerType.UNKNOWN
                // A number can match several contacts: starred if any of them is starred
                while (c.moveToNext()) {
                    type = if (c.getInt(0) == 1) CallerType.STARRED else maxOf(type, CallerType.CONTACT)
                    if (type == CallerType.STARRED) break
                }
                type
            } ?: CallerType.UNKNOWN
        }.getOrNull()
    }
}
