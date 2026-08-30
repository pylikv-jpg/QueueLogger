package com.pylikv.queuelogger

import android.content.Context
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Управляет автоматическим опросом электронной очереди.
 */
class QueuePollingManager(
    context: Context
) {

    companion object {

        /**
         * Интервал между полными циклами опроса.
         *
         * 60 секунд.
         */
        const val DEFAULT_INTERVAL_MS =
            60_000L
    }

    private val collector =
        QueueCollector(
            context.applicationContext
        )

    private val scope =
        CoroutineScope(
            SupervisorJob() +
                Dispatchers.IO
        )

    private var pollingJob: Job? =
        null

    /**
     * true — автоматический сбор сейчас запущен.
     */
    val isRunning: Boolean
        get() =
            pollingJob?.isActive == true

    /**
     * Запустить автоматический сбор.
     *
     * checkpoints — список КПП,
     * которые необходимо контролировать.
     */
    fun start(
        checkpoints: List<Checkpoint>,
        intervalMs: Long = DEFAULT_INTERVAL_MS,
        onResult: (
            QueueCollector.CollectionResult
        ) -> Unit = {},
        onError: (
            Checkpoint,
            Throwable
        ) -> Unit = { _, _ -> }
    ) {

        if (isRunning) {
            return
        }

        if (checkpoints.isEmpty()) {
            return
        }

        val safeInterval =
            intervalMs.coerceAtLeast(
                10_000L
            )

        pollingJob =
            scope.launch {

                while (isActive) {

                    val cycleStartedAt =
                        System.currentTimeMillis()

                    /*
                     * Последовательно опрашиваем
                     * каждый выбранный КПП.
                     */
                    for (checkpoint in checkpoints) {

                        if (!isActive) {
                            break
                        }

                        try {

                            val result =
                                collector.collectOnce(
                                    checkpoint
                                )

                            result.fold(

                                onSuccess = {
                                    collectionResult ->

                                    onResult(
                                        collectionResult
                                    )
                                },

                                onFailure = {
                                    throwable ->

                                    onError(
                                        checkpoint,
                                        throwable
                                    )
                                }
                            )

                        } catch (
                            cancellation:
                            CancellationException
                        ) {

                            throw cancellation

                        } catch (
                            throwable:
                            Throwable
                        ) {

                            onError(
                                checkpoint,
                                throwable
                            )
                        }
                    }

                    /*
                     * Интервал считаем от начала
                     * предыдущего цикла.
                     *
                     * Поэтому время сетевых запросов
                     * не прибавляется сверху
                     * к каждой минуте.
                     */
                    val cycleDuration =
                        System.currentTimeMillis() -
                            cycleStartedAt

                    val remainingDelay =
                        safeInterval -
                            cycleDuration

                    if (remainingDelay > 0) {

                        delay(
                            remainingDelay
                        )
                    }
                }
            }
    }

    /**
     * Остановить автоматический сбор.
     */
    fun stop() {

        pollingJob?.cancel()

        pollingJob = null
    }

    /**
     * Получить количество записей,
     * накопленных в SQLite.
     */
    suspend fun getStoredMovementCount(): Long {

        return collector
            .getStoredMovementCount()
    }

    /**
     * Получить последние движения.
     */
    suspend fun getRecentMovements(
        limit: Int = 100
    ): List<VehicleMovement> {

        return collector
            .getRecentMovements(
                limit
            )
    }
}
