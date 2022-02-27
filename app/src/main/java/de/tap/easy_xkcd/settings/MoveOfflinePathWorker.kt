package de.tap.easy_xkcd.settings

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import timber.log.Timber
import java.io.File

@HiltWorker
class MoveOfflinePathWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted parameters: WorkerParameters,
) : CoroutineWorker(context, parameters) {
    companion object {
        const val PARAM_OLD_PATH = "param_old_path"
        const val PARAM_NEW_PATH = "param_new_path"
    }

    override suspend fun doWork(): Result {
        val oldPath = File(inputData.getString(PARAM_OLD_PATH)!!)
        val newPath = File(inputData.getString(PARAM_NEW_PATH)!!)

        if (oldPath.absolutePath != newPath.absolutePath) {
            Timber.i("Move from $oldPath to $newPath")
            oldPath.copyRecursively(newPath)
            oldPath.deleteRecursively()
        }

        return Result.success()
    }
}