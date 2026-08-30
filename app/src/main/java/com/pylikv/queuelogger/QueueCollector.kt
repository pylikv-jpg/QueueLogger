package com.pylikv.queuelogger

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Основной сборщик данных QueueLogger.
 *
 * Цепочка работы:
 *
 * API
 * -> JSON
 * -> QueueSnapshot
 * -> сравнение с прошлым снимком
 * -> VehicleMovement
 * -> SQLite
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

    /**
     * Результат одного опроса КПП.
     */
    data class CollectionResult(
        val checkpointId: String,
        val checkpointName: String,
        val vehicleCount: Int,
        val movementCount: Int,
        val totalStoredMovements: Long,
        val timestamp: Long
    )

    /**
     * Выполнить один полный цикл сбора данных.
     */
    suspend fun collectOnce(
        checkpoint: Checkpoint
    ): Result<CollectionResult> {

        return withContext(
            Dispatchers.IO
        ) {

            try {

                /*
                 * 1. Получаем реальный JSON.
                 */
                val jsonResult =
                    api.getMonitoring(
                        checkpointId =
                            checkpoint.id
                    )

                if (jsonResult.isFailure) {

                    return@withContext Result.failure(
                        jsonResult.exceptionOrNull()
                            ?: IllegalStateException(
                                "Неизвестная ошибка API"
                            )
                    )
                }

                val json =
                    jsonResult.getOrThrow()

                /*
                 * 2. Превращаем JSON
                 * в снимок очереди.
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
                 * 3. Сравниваем с предыдущим снимком.
                 */
                val movements =
                    movementTracker.processSnapshot(
                        snapshot
                    )

                /*
                 * 4. Сохраняем найденные изменения
                 * в постоянную SQLite-базу.
                 */
                database.saveMovements(
                    movements
                )

                /*
                 * 5. Получаем текущее количество
                 * накопленных записей.
                 */
                val totalStored =
                    database.getMovementCount()

                Result.success(
                    CollectionResult(
                        checkpointId =
                            checkpoint.id,

                        checkpointName =
                            checkpoint.name,

                        vehicleCount =
                            snapshot.vehicles.size,

                        movementCount =
                            movements.size,

                        totalStoredMovements =
                            totalStored,

                        timestamp =
                            snapshot.timestamp
                    )
                )

            } catch (e: Exception) {

                Result.failure(e)
            }
        }
    }

    /**
     * Получить количество накопленных
     * записей движения.
     */
    suspend fun getStoredMovementCount(): Long {

        return withContext(
            Dispatchers.IO
        ) {

            database.getMovementCount()
        }
    }

    /**
     * Получить последние движения.
     */
    suspend fun getRecentMovements(
        limit: Int = 100
    ): List<VehicleMovement> {

        return withContext(
            Dispatchers.IO
        ) {

            database.getRecentMovements(
                limit
            )
        }
    }

    /**
     * Очистить только временную точку сравнения.
     *
     * Записи SQLite при этом НЕ удаляются.
     */
    fun resetCheckpoint(
        checkpointId: String
    ) {

        movementTracker.reset(
            checkpointId
        )
    }

    /**
     * Очистить все временные точки сравнения.
     *
     * Накопленная база SQLite остаётся.
     */
    fun resetAllTracking() {

        movementTracker.resetAll()
    }
}
