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
        val vehicleCount: Int,

        /**
         * Количество старых movement-записей,
         * созданных на этом цикле.
         */
        val movementCount: Int,

        /**
         * Количество новых полных событий
         * ARRIVAL / MOVE / CALLED /
         * STATUS_CHANGE / DISAPPEARED
         * на этом цикле.
         */
        val eventCount: Int,

        val totalStoredMovements: Long,
        val totalStoredSamples: Long,
        val totalStoredEvents: Long,
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

                val json =
                    apiResult.getOrThrow()

                /*
                 * 2. Разбираем полный
                 * снимок очереди.
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
                 * минутный снимок.
                 */
                database.saveQueueSample(
                    snapshot
                )

                /*
                 * 4. Получаем полную историю
                 * изменений относительно
                 * предыдущего снимка.
                 */
                val trackingResult =
                    movementTracker
                        .processSnapshotDetailed(
                            snapshot
                        )

                /*
                 * 5. Старый журнал движений
                 * продолжаем сохранять,
                 * чтобы не ломать уже
                 * существующую статистику.
                 */
                database.saveMovements(
                    trackingResult.movements
                )

                /*
                 * 6. Сохраняем новую
                 * полноценную историю событий.
                 *
                 * Здесь уже фиксируются:
                 *
                 * ARRIVAL — появление
                 * с начальной позицией;
                 *
                 * MOVE — изменение позиции;
                 *
                 * STATUS_CHANGE;
                 *
                 * CALLED — status == 3;
                 *
                 * DISAPPEARED —
                 * подтверждённое исчезновение.
                 */
                database.saveVehicleEvents(
                    trackingResult.events
                )

                /*
                 * 7. Получаем накопленные
                 * счётчики базы.
                 */
                val totalStoredMovements =
                    database
                        .getMovementCount()

                val totalStoredSamples =
                    database
                        .getQueueSampleCount()

                val totalStoredEvents =
                    database
                        .getVehicleEventCount()

                /*
                 * 8. Возвращаем результат
                 * текущего опроса.
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
                            trackingResult
                                .movements
                                .size,

                        eventCount =
                            trackingResult
                                .events
                                .count {
                                    it.vehicleType !=
                                        QueueParser
                                            .VEHICLE_TYPE_MOTORCYCLE
                                },

                        totalStoredMovements =
                            totalStoredMovements,

                        totalStoredSamples =
                            totalStoredSamples,

                        totalStoredEvents =
                            totalStoredEvents,

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
     * Количество сохранённых
     * старых движений.
     */
    suspend fun getStoredMovementCount():
        Long =
        withContext(Dispatchers.IO) {

            database
                .getMovementCount()
        }

    /**
     * Количество минутных снимков.
     */
    suspend fun getStoredQueueSampleCount():
        Long =
        withContext(Dispatchers.IO) {

            database
                .getQueueSampleCount()
        }

    /**
     * Количество полных событий
     * автомобилей.
     */
    suspend fun getStoredVehicleEventCount():
        Long =
        withContext(Dispatchers.IO) {

            database
                .getVehicleEventCount()
        }

    /**
     * Последние старые движения.
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
     * Сбросить точку сравнения
     * одного КПП.
     *
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
     * Сбросить все временные
     * точки сравнения.
     *
     * История SQLite остаётся.
     */
    fun resetAllTracking() {

        movementTracker.resetAll()
    }
}
