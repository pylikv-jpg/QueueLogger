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

    private val api =
        QueueApi()

    private val parser =
        QueueParser()

    private val movementTracker =
        QueueMovementTracker()

    private val database =
        QueueDatabase(
            context.applicationContext
        )

    data class CollectionResult(

        val checkpointId: String,

        val checkpointName: String,

        /**
         * Сколько автомобилей сейчас
         * находится в очереди.
         */
        val vehicleCount: Int,

        /**
         * Сколько автомобилей изменили
         * позицию или статус относительно
         * предыдущего опроса.
         */
        val movementCount: Int,

        /**
         * Общее количество сохранённых
         * событий движения.
         */
        val totalStoredMovements: Long,

        /**
         * Общее количество сохранённых
         * минутных снимков очередей.
         */
        val totalStoredSamples: Long,

        val timestamp: Long
    )

    /**
     * Выполнить один опрос одного КПП.
     */
    suspend fun collectOnce(
        checkpoint: Checkpoint
    ): Result<CollectionResult> {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                /*
                 * 1. Получаем актуальный JSON.
                 */
                val jsonResult =
                    api.getMonitoring(
                        checkpointId =
                            checkpoint.id
                    )

                if (
                    jsonResult.isFailure
                ) {

                    return@withContext
                        Result.failure(
                            jsonResult
                                .exceptionOrNull()
                                ?: IllegalStateException(
                                    "Неизвестная ошибка API"
                                )
                        )
                }

                val json =
                    jsonResult
                        .getOrThrow()

                /*
                 * 2. Формируем полный снимок
                 * текущего состояния очереди.
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
                 * 3. Каждый успешный опрос
                 * обязательно сохраняем
                 * как компактный queue sample.
                 *
                 * Даже если очередь не двигалась,
                 * это важная статистическая информация.
                 */
                database.saveQueueSample(
                    snapshot
                )

                /*
                 * 4. Сравниваем с предыдущим снимком
                 * и получаем только реальные изменения.
                 */
                val movements =
                    movementTracker
                        .processSnapshot(
                            snapshot
                        )

                /*
                 * 5. Сохраняем изменения автомобилей.
                 */
                database.saveMovements(
                    movements
                )

                /*
                 * 6. Получаем общую статистику базы.
                 */
                val totalStoredMovements =
                    database
                        .getMovementCount()

                val totalStoredSamples =
                    database
                        .getQueueSampleCount()

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
    }

    /**
     * Количество сохранённых движений.
     */
    suspend fun getStoredMovementCount():
        Long {

        return withContext(
            Dispatchers.IO
        ) {

            database
                .getMovementCount()
        }
    }

    /**
     * Количество сохранённых снимков очередей.
     */
    suspend fun getStoredQueueSampleCount():
        Long {

        return withContext(
            Dispatchers.IO
        ) {

            database
                .getQueueSampleCount()
        }
    }

    /**
     * Последние события движения.
     */
    suspend fun getRecentMovements(
        limit: Int = 100
    ): List<VehicleMovement> {

        return withContext(
            Dispatchers.IO
        ) {

            database
                .getRecentMovements(
                    limit
                )
        }
    }

    /**
     * Сбросить временную точку сравнения
     * только для одного КПП.
     *
     * Данные из SQLite при этом
     * не удаляются.
     */
    fun resetCheckpoint(
        checkpointId: String
    ) {

        movementTracker.reset(
            checkpointId
        )
    }

    /**
     * Сбросить все временные
     * точки сравнения.
     *
     * Историческая база сохраняется.
     */
    fun resetAllTracking() {

        movementTracker.resetAll()
    }
}
