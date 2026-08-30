package com.pylikv.queuelogger

/**
 * Сравнивает последовательные снимки очереди
 * и восстанавливает историю каждого автомобиля.
 */
class QueueMovementTracker {

    /**
     * Последний успешный снимок каждого КПП.
     */
    private val previousSnapshots =
        mutableMapOf<String, QueueSnapshot>()

    /**
     * Сколько последовательных успешных снимков
     * автомобиль отсутствует.
     *
     * Ключ:
     * checkpointId + номер + тип транспорта.
     */
    private val missingCounts =
        mutableMapOf<String, Int>()

    /**
     * Последнее известное состояние автомобиля.
     *
     * Оно необходимо, чтобы после второго
     * отсутствия сохранить DISAPPEARED
     * с последней известной позицией.
     */
    private val lastKnownVehicles =
        mutableMapOf<String, QueueVehicle>()

    data class TrackingResult(
        val movements: List<VehicleMovement>,
        val events: List<VehicleEvent>
    )

    /**
     * Обработать очередной успешный снимок.
     */
    fun processSnapshotDetailed(
        currentSnapshot: QueueSnapshot
    ): TrackingResult {

        val checkpointId =
            currentSnapshot.checkpointId

        val previousSnapshot =
            previousSnapshots[
                checkpointId
            ]

        val events =
            mutableListOf<VehicleEvent>()

        val movements =
            mutableListOf<VehicleMovement>()

        val currentVehicles =
            currentSnapshot.vehicles
                .associateBy {
                    vehicleKey(
                        checkpointId,
                        it
                    )
                }

        /*
         * Первый снимок после запуска программы
         * считаем исходной точкой наблюдения.
         *
         * Все находящиеся в нём автомобили
         * фиксируем как ARRIVAL, потому что именно
         * с этого момента QueueLogger начал
         * наблюдать их траекторию.
         */
        if (previousSnapshot == null) {

            for (
                vehicle in
                currentSnapshot.vehicles
            ) {

                /*
                 * Мотоциклы не включаем
                 * в прогностическую историю.
                 */
                if (
                    vehicle.vehicleType ==
                    QueueParser.VEHICLE_TYPE_MOTORCYCLE
                ) {
                    continue
                }

                val key =
                    vehicleKey(
                        checkpointId,
                        vehicle
                    )

                lastKnownVehicles[key] =
                    vehicle

                missingCounts.remove(
                    key
                )

                events +=
                    createArrivalEvent(
                        currentSnapshot,
                        vehicle
                    )
            }

            previousSnapshots[
                checkpointId
            ] = currentSnapshot

            return TrackingResult(
                movements =
                    emptyList(),
                events =
                    events
            )
        }

        val previousVehicles =
            previousSnapshot.vehicles
                .associateBy {
                    vehicleKey(
                        checkpointId,
                        it
                    )
                }

        /*
         * Сначала обрабатываем все автомобили,
         * которые присутствуют сейчас.
         */
        for (
            currentVehicle in
            currentSnapshot.vehicles
        ) {

            /*
             * Мотоциклы для статистики
             * прогнозирования игнорируем.
             */
            if (
                currentVehicle.vehicleType ==
                QueueParser.VEHICLE_TYPE_MOTORCYCLE
            ) {
                continue
            }

            val key =
                vehicleKey(
                    checkpointId,
                    currentVehicle
                )

            /*
             * Если автомобиль снова виден,
             * счётчик отсутствия обнуляем.
             */
            missingCounts.remove(
                key
            )

            val previousVehicle =
                previousVehicles[
                    key
                ]

            /*
             * Машины не было в предыдущем снимке.
             *
             * Значит фиксируем появление
             * и обязательно сохраняем позицию,
             * на которой она появилась.
             */
            if (
                previousVehicle == null
            ) {

                events +=
                    createArrivalEvent(
                        currentSnapshot,
                        currentVehicle
                    )

                lastKnownVehicles[
                    key
                ] = currentVehicle

                continue
            }

            val positionChanged =
                previousVehicle.position !=
                    currentVehicle.position

            val statusChanged =
                previousVehicle.status !=
                    currentVehicle.status

            /*
             * Явный status == 3 считаем
             * подтверждённым вызовом.
             */
            val becameCalled =
                previousVehicle.status != 3 &&
                    currentVehicle.status == 3

            if (
                positionChanged
            ) {

                events +=
                    createEvent(
                        snapshot =
                            currentSnapshot,
                        vehicle =
                            currentVehicle,
                        previousVehicle =
                            previousVehicle,
                        eventType =
                            VehicleEventType.MOVE
                    )
            }

            if (
                becameCalled
            ) {

                events +=
                    createEvent(
                        snapshot =
                            currentSnapshot,
                        vehicle =
                            currentVehicle,
                        previousVehicle =
                            previousVehicle,
                        eventType =
                            VehicleEventType.CALLED
                    )

            } else if (
                statusChanged
            ) {

                events +=
                    createEvent(
                        snapshot =
                            currentSnapshot,
                        vehicle =
                            currentVehicle,
                        previousVehicle =
                            previousVehicle,
                        eventType =
                            VehicleEventType.STATUS_CHANGE
                    )
            }

            /*
             * Старый журнал movements пока
             * продолжаем заполнять для совместимости.
             */
            if (
                positionChanged ||
                statusChanged
            ) {

                movements +=
                    VehicleMovement(
                        checkpointId =
                            checkpointId,
                        registrationNumber =
                            currentVehicle
                                .registrationNumber,
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

            lastKnownVehicles[
                key
            ] = currentVehicle
        }

        /*
         * Теперь ищем автомобили,
         * которые были в предыдущем снимке,
         * но отсутствуют в текущем.
         */
        for (
            previousVehicle in
            previousSnapshot.vehicles
        ) {

            if (
                previousVehicle.vehicleType ==
                QueueParser.VEHICLE_TYPE_MOTORCYCLE
            ) {
                continue
            }

            val key =
                vehicleKey(
                    checkpointId,
                    previousVehicle
                )

            if (
                currentVehicles.containsKey(
                    key
                )
            ) {
                continue
            }

            val missingCount =
                (
                    missingCounts[
                        key
                    ] ?: 0
                ) + 1

            missingCounts[
                key
            ] = missingCount

            /*
             * Одно отсутствие ничего не означает.
             *
             * Только второе последовательное
             * успешное отсутствие подтверждает
             * DISAPPEARED.
             */
            if (
                missingCount == 2
            ) {

                val lastKnown =
                    lastKnownVehicles[
                        key
                    ] ?: previousVehicle

                events +=
                    VehicleEvent(
                        checkpointId =
                            checkpointId,
                        checkpointName =
                            currentSnapshot
                                .checkpointName,
                        registrationNumber =
                            lastKnown
                                .registrationNumber,
                        vehicleType =
                            lastKnown.vehicleType,
                        typeQueue =
                            lastKnown.typeQueue,
                        timestamp =
                            currentSnapshot.timestamp,
                        eventType =
                            VehicleEventType
                                .DISAPPEARED,
                        previousPosition =
                            lastKnown.position,
                        currentPosition =
                            null,
                        previousStatus =
                            lastKnown.status,
                        currentStatus =
                            null,
                        registrationDate =
                            lastKnown
                                .registrationDate,
                        changedDate =
                            lastKnown.changedDate
                    )
            }
        }

        /*
         * Очень важный момент:
         *
         * previousSnapshots обновляем только
         * после полного сравнения.
         */
        previousSnapshots[
            checkpointId
        ] = currentSnapshot

        return TrackingResult(
            movements =
                movements,
            events =
                events
        )
    }

    /**
     * Старый интерфейс оставляем,
     * чтобы проект продолжал собираться,
     * пока QueueCollector ещё не переведён
     * на VehicleEvent.
     */
    fun processSnapshot(
        currentSnapshot: QueueSnapshot
    ): List<VehicleMovement> {

        return processSnapshotDetailed(
            currentSnapshot
        ).movements
    }

    private fun createArrivalEvent(
        snapshot: QueueSnapshot,
        vehicle: QueueVehicle
    ): VehicleEvent {

        return VehicleEvent(
            checkpointId =
                snapshot.checkpointId,
            checkpointName =
                snapshot.checkpointName,
            registrationNumber =
                vehicle.registrationNumber,
            vehicleType =
                vehicle.vehicleType,
            typeQueue =
                vehicle.typeQueue,
            timestamp =
                snapshot.timestamp,
            eventType =
                VehicleEventType.ARRIVAL,
            previousPosition =
                null,
            currentPosition =
                vehicle.position,
            previousStatus =
                null,
            currentStatus =
                vehicle.status,
            registrationDate =
                vehicle.registrationDate,
            changedDate =
                vehicle.changedDate
        )
    }

    private fun createEvent(
        snapshot: QueueSnapshot,
        vehicle: QueueVehicle,
        previousVehicle: QueueVehicle,
        eventType: VehicleEventType
    ): VehicleEvent {

        return VehicleEvent(
            checkpointId =
                snapshot.checkpointId,
            checkpointName =
                snapshot.checkpointName,
            registrationNumber =
                vehicle.registrationNumber,
            vehicleType =
                vehicle.vehicleType,
            typeQueue =
                vehicle.typeQueue,
            timestamp =
                snapshot.timestamp,
            eventType =
                eventType,
            previousPosition =
                previousVehicle.position,
            currentPosition =
                vehicle.position,
            previousStatus =
                previousVehicle.status,
            currentStatus =
                vehicle.status,
            registrationDate =
                vehicle.registrationDate,
            changedDate =
                vehicle.changedDate
        )
    }

    /**
     * Ключ автомобиля внутри конкретного КПП.
     */
    private fun vehicleKey(
        checkpointId: String,
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

            append(
                checkpointId
            )

            append("|")

            append(
                normalizedNumber
            )

            append("|")

            append(
                vehicle.vehicleType ?: 0
            )
        }
    }

    fun reset(
        checkpointId: String
    ) {

        previousSnapshots.remove(
            checkpointId
        )

        val prefix =
            "$checkpointId|"

        missingCounts.keys
            .filter {
                it.startsWith(
                    prefix
                )
            }
            .forEach {
                missingCounts.remove(
                    it
                )
            }

        lastKnownVehicles.keys
            .filter {
                it.startsWith(
                    prefix
                )
            }
            .forEach {
                lastKnownVehicles.remove(
                    it
                )
            }
    }

    fun resetAll() {

        previousSnapshots.clear()

        missingCounts.clear()

        lastKnownVehicles.clear()
    }
}
