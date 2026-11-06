package io.github.tomervancohen.rambleflow

/** Older Play services builds can gate AuthorizationClient before contacting OAuth. */
internal object DriveAuthErrors {
    fun needsServicesUpdate(status: Int?, detail: String?): Boolean =
        status == 10 && detail.orEmpty().contains("Caller not whitelisted to call this API", ignoreCase = true)
}
