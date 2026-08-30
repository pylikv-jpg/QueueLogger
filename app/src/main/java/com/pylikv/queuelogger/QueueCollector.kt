package com.pylikv.queuelogger

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Выполняет один полный цикл сбора данных
 * для выбранного пункта пропуска.
 */
class QueueCollector(
    context: Context
) {

    private val api = QueueApi()

    private val parser = QueueParser()

    private val movementTracker =
        QueueMovementTracker()

    private val database =
        QueueDatabase(
            context.applicationContext
        )

    data class CollectionResult(
        val checkpointId: String,
        val checkpointName: String,
        val vehicleCount: Int,
        val movementCount: Int,
        val totalStoredMovements: Long,
        val totalStoredSamples: Long,
        val timestamp: Long
    )

    /**
     * Один полный опрос одного КПП.
     */
    suspend fun collectOnce(
        checkpoint: Checkpoint
    ): Result<CollectionResult> =
        withContext(Dispatchers.IO) {

            try {

                /*
                 * 1. Получаем данные сервера.
                 */
                val apiResult =
                    api.getMonitoring(
                        checkpointId =
                            checkpoint.id
                    )

                /*
                 * Если запрос завершился ошибкой,
                 * выбрасываем её внутрь try.
                 * Catch ниже преобразует её
                 * обратно в Result.failure.
                 */
                val json =
                    apiResult.getOrThrow()

                /*
                 * 2. Разбираем полный снимок очереди.
                 */
                val snapshot =
                    parser.parseSnapshot(
                        json = json,
                        checkpointId =
                            checkpoint.id,
                        checkpointName =
                            checkpoint.name
                    )

                /*
                 * 3. Сохраняем компактный
                 * минутный снимок очереди.
                 *
                 * Он сохраняется даже тогда,
                 * когда автомобили не двигались.
                 */
                database.saveQueueSample(
                    snapshot
                )

                /*
                 * 4. Определяем изменения
                 * относительно предыдущего опроса.
                 */
                val movements =
                    movementTracker
                        .processSnapshot(
                            snapshot
                        )

                /*
                 * 5. Сохраняем движения.
                 */
                database.saveMovements(
                    movements
                )

                /*
                 * 6. Получаем общие счётчики.
                 */
                val totalStoredMovements =
                    database
                        .getMovementCount()

                val totalStoredSamples =
                    database
                        .getQueueSampleCount()

                /*
                 * 7. Возвращаем результат опроса.
                 */
                Result.success(
                    CollectionResult(
                        checkpointId =
                            checkpoint.id,
                        checkpointName =
                            checkpoint.name,
                        vehicleCount =
                            snapshot.vehicleCount,
                        movementCount =
                            movements.size,
                        totalStoredMovements =
                            totalStoredMovements,
                        totalStoredSamples =
                            totalStoredSamples,
                        timestamp =
                            snapshot.timestamp
                    )
                )

            } catch (
                exception: Exception
            ) {

                Result.failure(
                    exception
                )
            }
        }

    /**
     * Количество сохранённых движений.
     */
    suspend fun getStoredMovementCount():
        Long =
        withContext(Dispatchers.IO) {

            database
                .getMovementCount()
        }

    /**
     * Количество сохранённых
     * минутных снимков очереди.
     */
    suspend fun getStoredQueueSampleCount():
        Long =
        withContext(Dispatchers.IO) {

            database
                .getQueueSampleCount()
        }

    /**
     * Последние движения автомобилей.
     */
    suspend fun getRecentMovements(
        limit: Int = 100
    ): List<VehicleMovement> =
        withContext(Dispatchers.IO) {

            database
                .getRecentMovements(
                    limit
                )
        }

    /**
     * Сбросить точку сравнения одного КПП.
     * История SQLite не удаляется.
     */
    fun resetCheckpoint(
        checkpointId: String
    ) {

        movementTracker.reset(
            checkpointId
        )
    }

    /**
     * Сбросить все временные точки сравнения.
     * История SQLite остаётся.
     */
    fun resetAllTracking() {

        movementTracker.resetAll()
    }
}
