package com.pylikv.queuelogger

import android.content.Context

/**
 * Управляет выгрузкой локальной базы QueueLogger
 * в репозиторий QueueLoggerData.
 *
 * Для каждой таблицы отдельно запоминается
 * последний успешно отправленный ID.
 *
 * Если отправка не удалась,
 * курсор НЕ изменяется.
 */
class QueueSyncManager(
    context: Context
) {

    companion object {

        private const val PREFS_NAME =
            "queue_logger_sync_state"

        private const val KEY_LAST_SAMPLE_ID =
            "last_queue_sample_id"

        private const val KEY_LAST_EVENT_ID =
            "last_vehicle_event_id"

        private const val KEY_LAST_MOVEMENT_ID =
            "last_movement_id"

        /**
         * За одну попытку отправляем
         * максимум один пакет каждого типа.
         *
         * Таким образом приложение не создаёт
         * слишком большую сетевую нагрузку.
         */
        private const val BATCH_SIZE =
            QueueSyncDataSource.DEFAULT_BATCH_SIZE
    }

    private val appContext =
        context.applicationContext

    private val database =
        QueueDatabase(
            appContext
        )

    private val dataSource =
        QueueSyncDataSource(
            database
        )

    private val prefs =
        appContext.getSharedPreferences(
            PREFS_NAME,
            Context.MODE_PRIVATE
        )

    /**
     * Выполнить одну полную попытку синхронизации.
     *
     * Последовательно:
     *
     * 1. минутные снимки;
     * 2. полные события;
     * 3. старые движения.
     */
    fun syncOnce(): QueueSyncResult {

        if (
            !GitHubSync.hasToken(
                appContext
            )
        ) {

            return QueueSyncResult(
                success = false,
                uploadedRows = 0,
                message =
                    "GitHub token не настроен"
            )
        }

        var uploadedRows =
            0

        /*
         * Сначала снимки очереди.
         */
        val samplesResult =
            syncQueueSamples()

        uploadedRows +=
            samplesResult.uploadedRows

        if (!samplesResult.success) {

            return QueueSyncResult(
                success = false,
                uploadedRows =
                    uploadedRows,
                message =
                    samplesResult.message
            )
        }

        /*
         * Затем события машин.
         */
        val eventsResult =
            syncVehicleEvents()

        uploadedRows +=
            eventsResult.uploadedRows

        if (!eventsResult.success) {

            return QueueSyncResult(
                success = false,
                uploadedRows =
                    uploadedRows,
                message =
                    eventsResult.message
            )
        }

        /*
         * Последними отправляем старые
         * movement-записи.
         */
        val movementsResult =
            syncMovements()

        uploadedRows +=
            movementsResult.uploadedRows

        if (!movementsResult.success) {

            return QueueSyncResult(
                success = false,
                uploadedRows =
                    uploadedRows,
                message =
                    movementsResult.message
            )
        }

        return QueueSyncResult(
            success = true,
            uploadedRows =
                uploadedRows,
            message =
                if (uploadedRows == 0) {
                    "Новых данных для отправки нет"
                } else {
                    "Отправлено строк: $uploadedRows"
                }
        )
    }

    /**
     * Минутные снимки.
     */
    private fun syncQueueSamples(): TableSyncResult {

        val lastId =
            prefs.getLong(
                KEY_LAST_SAMPLE_ID,
                0L
            )

        val batch =
            dataSource.getQueueSamples(
                afterId = lastId,
                limit = BATCH_SIZE
            )

        if (batch.isEmpty) {

            return TableSyncResult(
                success = true,
                uploadedRows = 0,
                message =
                    "Новых снимков нет"
            )
        }

        val upload =
            GitHubSync.uploadBatch(
                context =
                    appContext,
                content =
                    batch.content,
                dataType =
                    "queue_samples"
            )

        if (!upload.success) {

            return TableSyncResult(
                success = false,
                uploadedRows = 0,
                message =
                    "Ошибка отправки снимков: " +
                        upload.message
            )
        }

        /*
         * Очень важно:
         * ID сохраняем ТОЛЬКО после
         * успешного ответа GitHub.
         */
        prefs
            .edit()
            .putLong(
                KEY_LAST_SAMPLE_ID,
                batch.lastId
            )
            .commit()

        return TableSyncResult(
            success = true,
            uploadedRows =
                batch.rowCount,
            message =
                "Снимки отправлены"
        )
    }

    /**
     * Полные события автомобилей.
     */
    private fun syncVehicleEvents(): TableSyncResult {

        val lastId =
            prefs.getLong(
                KEY_LAST_EVENT_ID,
                0L
            )

        val batch =
            dataSource.getVehicleEvents(
                afterId = lastId,
                limit = BATCH_SIZE
            )

        if (batch.isEmpty) {

            return TableSyncResult(
                success = true,
                uploadedRows = 0,
                message =
                    "Новых событий нет"
            )
        }

        val upload =
            GitHubSync.uploadBatch(
                context =
                    appContext,
                content =
                    batch.content,
                dataType =
                    "vehicle_events"
            )

        if (!upload.success) {

            return TableSyncResult(
                success = false,
                uploadedRows = 0,
                message =
                    "Ошибка отправки событий: " +
                        upload.message
            )
        }

        prefs
            .edit()
            .putLong(
                KEY_LAST_EVENT_ID,
                batch.lastId
            )
            .commit()

        return TableSyncResult(
            success = true,
            uploadedRows =
                batch.rowCount,
            message =
                "События отправлены"
        )
    }

    /**
     * Старые записи движения.
     */
    private fun syncMovements(): TableSyncResult {

        val lastId =
            prefs.getLong(
                KEY_LAST_MOVEMENT_ID,
                0L
            )

        val batch =
            dataSource.getMovements(
                afterId = lastId,
                limit = BATCH_SIZE
            )

        if (batch.isEmpty) {

            return TableSyncResult(
                success = true,
                uploadedRows = 0,
                message =
                    "Новых движений нет"
            )
        }

        val upload =
            GitHubSync.uploadBatch(
                context =
                    appContext,
                content =
                    batch.content,
                dataType =
                    "vehicle_movements"
            )

        if (!upload.success) {

            return TableSyncResult(
                success = false,
                uploadedRows = 0,
                message =
                    "Ошибка отправки движений: " +
                        upload.message
            )
        }

        prefs
            .edit()
            .putLong(
                KEY_LAST_MOVEMENT_ID,
                batch.lastId
            )
            .commit()

        return TableSyncResult(
            success = true,
            uploadedRows =
                batch.rowCount,
            message =
                "Движения отправлены"
        )
    }

    /**
     * Текущие позиции синхронизации.
     *
     * Позже выведем их на экран
     * для диагностики.
     */
    fun getState(): QueueSyncState {

        return QueueSyncState(
            lastQueueSampleId =
                prefs.getLong(
                    KEY_LAST_SAMPLE_ID,
                    0L
                ),

            lastVehicleEventId =
                prefs.getLong(
                    KEY_LAST_EVENT_ID,
                    0L
                ),

            lastMovementId =
                prefs.getLong(
                    KEY_LAST_MOVEMENT_ID,
                    0L
                )
        )
    }

    /**
     * Сброс курсоров.
     *
     * Сейчас кнопки для этого в интерфейсе
     * не будет. Функция оставлена только
     * для диагностики.
     *
     * После сброса вся база будет считаться
     * ещё не отправленной.
     */
    fun resetSyncState() {

        prefs
            .edit()
            .remove(
                KEY_LAST_SAMPLE_ID
            )
            .remove(
                KEY_LAST_EVENT_ID
            )
            .remove(
                KEY_LAST_MOVEMENT_ID
            )
            .commit()
    }

    fun close() {

        database.close()
    }
}

/**
 * Итог одной полной синхронизации.
 */
data class QueueSyncResult(
    val success: Boolean,
    val uploadedRows: Int,
    val message: String
)

/**
 * Итог синхронизации одной таблицы.
 */
private data class TableSyncResult(
    val success: Boolean,
    val uploadedRows: Int,
    val message: String
)

/**
 * Сохранённые курсоры.
 */
data class QueueSyncState(
    val lastQueueSampleId: Long,
    val lastVehicleEventId: Long,
    val lastMovementId: Long
)
