/*
 * Copyright (C) 2017 Moez Bhatti <moez.bhatti@gmail.com>
 *
 * This file is part of QKSMS.
 *
 * QKSMS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * QKSMS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with QKSMS.  If not, see <http://www.gnu.org/licenses/>.
 */
package dev.octoshrimpy.quik.feature.scheduled.group

import android.content.Context
import com.uber.autodispose.android.lifecycle.scope
import com.uber.autodispose.autoDisposable
import dev.octoshrimpy.quik.R
import dev.octoshrimpy.quik.common.base.QkViewModel
import dev.octoshrimpy.quik.interactor.CreateScheduledGroupWithMessages
import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.rxkotlin.plusAssign
import io.reactivex.rxkotlin.withLatestFrom
import io.reactivex.schedulers.Schedulers
import timber.log.Timber
import javax.inject.Inject

class ScheduledGroupCreateViewModel @Inject constructor(
    private val context: Context,
    private val csvImportParser: CsvImportParser,
    private val createScheduledGroupWithMessages: CreateScheduledGroupWithMessages
) : QkViewModel<ScheduledGroupCreateView, ScheduledGroupCreateState>(
    ScheduledGroupCreateState()
) {

    companion object {
        private const val MAX_NAME_LENGTH = 100
        private const val MAX_DESCRIPTION_LENGTH = 500
    }

    private var importedRows: List<CsvImportParser.Row> = emptyList()

    override fun bindView(view: ScheduledGroupCreateView) {
        super.bindView(view)

        view.nameChanges
            .autoDisposable(view.scope())
            .subscribe { name ->
                val nameString = name.toString()
                newState {
                    copy(
                        name = nameString,
                        nameError = validateName(nameString)
                    )
                }
            }

        view.descriptionChanges
            .autoDisposable(view.scope())
            .subscribe { description ->
                val descriptionString = description.toString()
                newState {
                    copy(
                        description = descriptionString,
                        descriptionError = validateDescription(descriptionString)
                    )
                }
            }

        disposables += state
            .subscribe { current ->
                val canCreate = current.name.isNotBlank() &&
                        current.nameError == null &&
                        current.descriptionError == null &&
                        !current.creating &&
                        !current.importing &&
                        current.importError.isNullOrBlank()
                if (current.canCreate != canCreate) {
                    newState { copy(canCreate = canCreate) }
                }
            }

        view.csvImportSelections
            .doOnNext { selection ->
                importedRows = emptyList()
                newState {
                    copy(
                        importing = true,
                        importError = null,
                        importFileName = selection.displayName ?: selection.uri.lastPathSegment,
                        importRowCount = 0
                    )
                }
            }
            .observeOn(Schedulers.io())
            .flatMap { selection ->
                Observable.fromCallable {
                    val result = context.contentResolver.openInputStream(selection.uri)?.use { input ->
                        csvImportParser.parse(input)
                    } ?: throw IllegalStateException("Unable to open CSV input stream")

                    ImportComputation(selection, result, null)
                }.onErrorReturn { error ->
                    ImportComputation(selection, null, error)
                }
            }
            .observeOn(AndroidSchedulers.mainThread())
            .autoDisposable(view.scope())
            .subscribe { computation ->
                val selection = computation.selection
                val displayName = selection.displayName ?: selection.uri.lastPathSegment

                if (computation.error != null) {
                    Timber.w(computation.error, "Failed to import CSV file")
                    importedRows = emptyList()
                    newState {
                        copy(
                            importing = false,
                            importError = context.getString(R.string.scheduled_group_import_error_open_failed),
                            importRowCount = 0,
                            importFileName = displayName
                        )
                    }
                    return@subscribe
                }

                val result = computation.result
                if (result == null) {
                    importedRows = emptyList()
                    newState {
                        copy(
                            importing = false,
                            importError = context.getString(R.string.scheduled_group_import_error_open_failed),
                            importRowCount = 0,
                            importFileName = displayName
                        )
                    }
                    return@subscribe
                }

                if (result.errors.isNotEmpty()) {
                    importedRows = emptyList()
                    val errorMessage = result.errors.joinToString("\n") { formatRowError(it) }
                    newState {
                        copy(
                            importing = false,
                            importError = errorMessage,
                            importRowCount = 0,
                            importFileName = displayName
                        )
                    }
                    return@subscribe
                }

                if (result.rows.isEmpty()) {
                    importedRows = emptyList()
                    newState {
                        copy(
                            importing = false,
                            importError = context.getString(R.string.scheduled_group_import_error_no_rows),
                            importRowCount = 0,
                            importFileName = displayName
                        )
                    }
                    return@subscribe
                }

                importedRows = result.rows
                newState {
                    copy(
                        importing = false,
                        importError = null,
                        importRowCount = result.rows.size,
                        importFileName = displayName
                    )
                }
            }

        view.createIntent
            .withLatestFrom(state) { _, currentState -> currentState }
            .filter { it.canCreate }
            .autoDisposable(view.scope())
            .subscribe { currentState ->
                createGroup(currentState.name, currentState.description, view)
            }

        view.backPressedIntent
            .autoDisposable(view.scope())
            .subscribe { view.finishActivity() }
    }

    private fun validateName(name: String): String? {
        return when {
            name.isBlank() -> context.getString(R.string.scheduled_group_name_required)
            name.length > MAX_NAME_LENGTH -> context.getString(
                R.string.scheduled_group_name_too_long,
                MAX_NAME_LENGTH
            )
            else -> null
        }
    }

    private fun validateDescription(description: String): String? {
        return when {
            description.length > MAX_DESCRIPTION_LENGTH -> context.getString(
                R.string.scheduled_group_description_too_long,
                MAX_DESCRIPTION_LENGTH
            )
            else -> null
        }
    }

    private fun createGroup(name: String, description: String, view: ScheduledGroupCreateView) {
        newState { copy(creating = true, importError = null) }

        val params = CreateScheduledGroupWithMessages.Params(
            name = name,
            description = description,
            messages = importedRows.map { row ->
                CreateScheduledGroupWithMessages.Message(
                    scheduledAtMillis = row.scheduledAtMillis,
                    phoneNumber = row.phoneNumber,
                    body = row.body,
                    name = row.name.takeIf { it.isNotBlank() }
                )
            }
        )

        disposables += createScheduledGroupWithMessages
            .buildObservable(params)
            .subscribeOn(Schedulers.io())
            .observeOn(AndroidSchedulers.mainThread())
            .subscribe(
                { groupId ->
                    newState { copy(creating = false) }
                    // Force main thread Realm to refresh before navigating
                    // This ensures the list activity can see the newly created data
                    try {
                        io.realm.Realm.getDefaultInstance().use { realm ->
                            realm.refresh()
                        }
                    } catch (e: Exception) {
                        Timber.w(e, "Failed to refresh Realm before navigation")
                    }
                    view.showGroupCreated(groupId as Long)
                },
                { error ->
                    Timber.e(error, "Failed to create scheduled group with messages")
                    newState {
                        copy(
                            creating = false,
                            importError = error.localizedMessage
                                ?: context.getString(R.string.scheduled_group_import_error_open_failed)
                        )
                    }
                }
            )
    }

    private fun formatRowError(error: CsvImportParser.RowError): String {
        return when (val kind = error.kind) {
            CsvImportParser.RowError.Kind.MissingPhoneNumber ->
                context.getString(R.string.scheduled_group_import_error_missing_phone, error.lineNumber)

            CsvImportParser.RowError.Kind.MissingTime ->
                context.getString(R.string.scheduled_group_import_error_missing_time, error.lineNumber)

            is CsvImportParser.RowError.Kind.InvalidTime ->
                context.getString(
                    R.string.scheduled_group_import_error_invalid_time,
                    error.lineNumber,
                    kind.rawValue,
                    CsvImportParser.SUPPORTED_FORMATS.joinToString(", ")
                )

            CsvImportParser.RowError.Kind.MissingBody ->
                context.getString(R.string.scheduled_group_import_error_missing_body, error.lineNumber)
        }
    }

    private data class ImportComputation(
        val selection: CsvImportSelection,
        val result: CsvImportParser.Result?,
        val error: Throwable?
    )
}
