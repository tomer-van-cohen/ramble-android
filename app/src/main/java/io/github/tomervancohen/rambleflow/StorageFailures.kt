package io.github.tomervancohen.rambleflow

import android.database.sqlite.SQLiteFullException
import android.system.ErrnoException
import android.system.OsConstants
import java.io.IOException

internal object StorageFailures {
    fun isFull(error: Throwable): Boolean = generateSequence(error) { it.cause }.take(12).any {
        it.message=="storage" || it is SQLiteFullException ||
            (it is ErrnoException && it.errno in setOf(OsConstants.ENOSPC,OsConstants.EDQUOT)) ||
            (it is IOException && Regex("ENOSPC|EDQUOT|no space left",RegexOption.IGNORE_CASE).containsMatchIn(it.message.orEmpty()))
    }
}
