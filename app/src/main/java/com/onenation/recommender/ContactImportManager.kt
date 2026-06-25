package com.onenation.recommender

import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ContactImportState(
    val isRunning: Boolean = false,
    val processed: Int = 0,
    val total: Int = 0,
    val added: Int = 0,
    val skippedDuplicate: Int = 0,
    val skippedInvalid: Int = 0,
    val message: String = "",
)

object ContactImportManager {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _state = MutableStateFlow(ContactImportState())
    val state: StateFlow<ContactImportState> = _state

    fun start(context: Context) {
        if (_state.value.isRunning) return

        _state.value = ContactImportState(isRunning = true, message = "Starting import...")
        val appContext = context.applicationContext

        scope.launch {
            runCatching {
                val date = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())
                val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
                val phones = linkedSetOf<String>()
                var processed = 0
                var invalid = 0

                val cursor: Cursor? = appContext.contentResolver.query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                    null,
                    null,
                    null,
                )

                cursor?.use {
                    val total = it.count.coerceAtLeast(0)
                    _state.value = _state.value.copy(total = total, message = "Reading contacts...")

                    val numberColumn = it.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    if (numberColumn < 0) {
                        throw IllegalStateException("Phone number column not found")
                    }
                    while (it.moveToNext()) {
                        ensureActive()
                        processed++

                        val cleaned = it.getString(numberColumn).orEmpty()
                            .replace(Regex("[^0-9+]"), "")
                            .replace("+254", "0")
                            .trim()

                        if (cleaned.matches(Regex("^0[17]\\d{8}$"))) {
                            phones.add(cleaned)
                        } else {
                            invalid++
                        }

                        if (processed % 50 == 0) {
                            _state.value = _state.value.copy(
                                processed = processed,
                                skippedInvalid = invalid,
                                message = "Reading contacts...",
                            )
                        }
                    }

                    _state.value = _state.value.copy(processed = processed, skippedInvalid = invalid)
                }

                val result = ContactManager.addImportedContactsBatch(
                    context = appContext,
                    phones = phones,
                    date = date,
                    time = time,
                )

                _state.value = _state.value.copy(
                    isRunning = false,
                    added = result.added,
                    skippedDuplicate = result.skippedDuplicate,
                    message = "Imported ${result.added} numbers",
                )
            }.onFailure { e ->
                _state.value = _state.value.copy(
                    isRunning = false,
                    message = "Import failed: ${e.message.orEmpty()}",
                )
                saveLog(appContext, "[IMPORT ERROR] ${e.javaClass.simpleName}: ${e.message.orEmpty()}")
            }
        }
    }
}
