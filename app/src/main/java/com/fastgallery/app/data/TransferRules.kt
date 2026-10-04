package com.fastgallery.app.data

/** Copy/move ka ek kaam: item ko (folder, destPath) album me bhejo. Undo me destination wali copy wapas original album me jaati hai. */
class TransferJob(val item: MediaItem, val folder: String, val destPath: String?)

/** Copy/move ke Undo ke pure rules (Android ke bina test ho sakte hain). */
object TransferRules {
    /**
     * Undo tab: API 29+ (MediaStore relative path), kaam poora hua, aur Move me har source ka original path pata ho
     * (warna copy ko original album me wapas nahi bhej sakte). Copy ke Undo me sirf copies delete hoti hain.
     */
    fun canUndo(sdkInt: Int, move: Boolean, completed: Boolean, undoable: Boolean, sources: List<MediaItem>): Boolean =
        undoable && completed && sources.isNotEmpty() && sdkInt >= 29 &&
            (!move || sources.all { it.relativePath.isNotBlank() })

    /** Move ka Undo: (source, destination me bani copy) jodo se wapas-bhejne ke jobs; har copy apne original album me. */
    fun undoJobs(made: List<Pair<MediaItem, MediaItem>>): List<TransferJob> =
        made.map { (source, copy) -> TransferJob(copy, source.bucketName, source.relativePath) }

    /** Undo ke message ka naam: sab items ek hi album se aaye to uska naam, warna fallback ("original albums"). */
    fun undoLabel(sources: List<MediaItem>, fallback: String): String =
        sources.map { it.bucketName }.distinct().singleOrNull() ?: fallback
}
