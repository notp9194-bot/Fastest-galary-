package com.fastgallery.app.ui

import android.content.Context
import android.util.Log
import com.fastgallery.app.R
import java.io.FileNotFoundException
import java.io.IOException

/**
 * Failure ko user ke liye ek simple line me badalta hai. Raw exception message kabhi dikhaya nahi jaata
 * ("ENOSPC", class names wagairah); asli error sirf Logcat me jaata hai.
 * whenNull: error object hi na ho (jaise operation ne null result diya) to kaunsi line dikhani hai.
 */
fun friendlyError(ctx: Context, error: Throwable?, whenNull: Int = R.string.err_generic): String {
    if (error != null) Log.w("FastGallery", "Operation failed", error)
    var cause: Throwable? = error
    var res: Int? = null
    var depth = 0
    while (cause != null && res == null && depth < 5) {
        res = when {
            cause is SecurityException -> R.string.err_permission
            cause is FileNotFoundException -> R.string.err_not_found
            cause is OutOfMemoryError -> R.string.err_memory
            cause is IOException && (cause.message.orEmpty().contains("ENOSPC") ||
                cause.message.orEmpty().contains("No space left", ignoreCase = true)) -> R.string.err_storage_full
            cause is UnsupportedOperationException -> R.string.err_unsupported
            else -> null
        }
        cause = cause.cause
        depth++
    }
    return ctx.getString(res ?: if (error == null) whenNull else R.string.err_generic)
}
