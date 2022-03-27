package de.tap.easy_xkcd.database

sealed class ProgressStatus {
    data class SetProgress(val value: Int, val max: Int) : ProgressStatus()
    object ResetProgress : ProgressStatus()
    object Finished : ProgressStatus()
}

