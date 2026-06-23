package com.onenation.recommender

import android.content.Context

object CallStateManager {
    fun setCallInProgress(context: Context, inProgress: Boolean) {
        context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_CALL_IN_PROGRESS, inProgress)
            .apply()
    }

    fun isCallInProgress(context: Context): Boolean {
        return context.getSharedPreferences(SETTINGS_PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_CALL_IN_PROGRESS, false)
    }
}
