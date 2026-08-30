package com.pylikv.queuelogger

/**
 * Сравнивает последовательные снимки очереди
 * и определяет реальные изменения автомобилей.
 */
class QueueMovementTracker {

    /**
     * Последний известный снимок для каждого КПП.
     */
    private val previousSnapshots =
        mutableMapOf<String, QueueSnapshot>()

    /**
     * Обработать новый снимок очереди.
     *
     * Возвращает только те автомобили,
     * у которых изменилась позиция или статус.
     */
    fun processSnapshot(
        currentSnapshot: QueueSnapshot
    ): List<VehicleMovement> {

        val previousSnapshot =
            previousSnapshots[
                currentSnapshot.checkpointId
            ]

        previousSnapshots[
            currentSnapshot.checkpointId
        ] = currentSnapshot

        /**
         * Первый снимок нужен только как исходная точка.
         * Движения пока вычислить невозможно.
         */
        if (previousSnapshot == null) {
            return emptyList()
        }

        val previousVehicles =
            previousSnapshot.vehicles.associateBy {
                vehicleKey(it)
            }

        val movements =
            mutableListOf<VehicleMovement>()

        for (currentVehicle in currentSnapshot.vehicles) {

            val key =
                vehicleKey(currentVehicle)

            val previousVehicle =
                previousVehicles[key]
                    ?: continue

            val positionChanged =
                previousVehicle.position !=
                    currentVehicle.position

            val statusChanged =
                previousVehicle.status !=
                    currentVehicle.status

            if (!positionChanged && !statusChanged) {
                continue
            }

            movements +=
                VehicleMovement(
                    checkpointId =
                        currentSnapshot.checkpointId,

                    registrationNumber =
                        currentVehicle.registrationNumber,

                    timestamp =
                        currentSnapshot.timestamp,

                    previousPosition =
                        previousVehicle.position,

                    currentPosition =
                        currentVehicle.position,

                    status =
                        currentVehicle.status
                )
        }

        return movements
    }

    /**
     * Создаёт устойчивый ключ автомобиля.
     *
     * Номер нормализуется:
     * пробелы и дефисы не учитываются.
     * Тип транспорта добавляется, чтобы одинаковые
     * номера из разных очередей не смешивались.
     */
    private fun vehicleKey(
        vehicle: QueueVehicle
    ): String {

        val normalizedNumber =
            vehicle.registrationNumber
                .uppercase()
                .replace(
                    "\\s".toRegex(),
                    ""
                )
                .replace(
                    "-",
                    ""
                )
                .trim()

        return buildString {

            append(normalizedNumber)

            append("|")

            append(
                vehicle.vehicleType
                    ?: 0
            )
        }
    }

    /**
     * Очистить сохранённый предыдущий снимок.
     *
     * При следующем запросе новый снимок снова
     * станет исходной точкой.
     */
    fun reset(
        checkpointId: String
    ) {

        previousSnapshots.remove(
            checkpointId
        )
    }

    /**
     * Полностью очистить временную историю.
     */
    fun resetAll() {

        previousSnapshots.clear()
    }
}
