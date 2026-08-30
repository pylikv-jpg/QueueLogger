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
     * Автомобили, которые пропали из очереди,
     * но исчезновение ещё не подтверждено.
     */
    private data class MissingVehicle(
        val vehicle: QueueVehicle,
        val firstMissingTimestamp: Long,
        val missingCount: Int
    )

    /**
     * Ожидающие подтверждения исчезновения машины.
     *
     * Ключ:
     * checkpointId + номер + тип транспорта.
     */
    private val missingVehicles =
        mutableMapOf<String, MissingVehicle>()

    data class TrackingResult(
        val movements: List<VehicleMovement>,
        val events: List<VehicleEvent>
    )

    fun processSnapshotDetailed(
        currentSnapshot: QueueSnapshot
    ): TrackingResult {

        val checkpointId =
            currentSnapshot.checkpointId

        val previousSnapshot =
            previousSnapshots[
                checkpointId
            ]

        val movements =
            mutableListOf<VehicleMovement>()

        val events =
            mutableListOf<VehicleEvent>()

        val currentVehicles =
            currentSnapshot.vehicles
                .filter {
                    it.vehicleType !=
                        QueueParser.VEHICLE_TYPE_MOTORCYCLE
                }
                .associateBy {
                    vehicleKey(
                        checkpointId,
                        it
                    )
                }

        /*
         * Первый снимок после запуска.
         *
         * Мы не можем знать реальное время,
         * когда уже находящиеся здесь автомобили
         * вошли в очередь.
         *
         * Но фиксируем начало нашего наблюдения
         * вместе с их текущей позицией.
         */
        if (previousSnapshot == null) {

            for (
                vehicle in
                currentVehicles.values
            ) {

                events +=
                    createArrivalEvent(
                        snapshot =
                            currentSnapshot,
                        vehicle =
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
                .filter {
                    it.vehicleType !=
                        QueueParser.VEHICLE_TYPE_MOTORCYCLE
                }
                .associateBy {
                    vehicleKey(
                        checkpointId,
                        it
                    )
                }

        /*
         * 1. Обрабатываем машины,
         * которые есть в текущем снимке.
         */
        for (
            currentVehicle in
            currentVehicles.values
        ) {

            val key =
                vehicleKey(
                    checkpointId,
                    currentVehicle
                )

            /*
             * Если автомобиль снова появился,
             * отменяем ожидающее исчезновение.
             */
            val pendingMissing =
                missingVehicles.remove(
                    key
                )

            val previousVehicle =
                previousVehicles[
                    key
                ]

            /*
             * Если машины не было
             * в предыдущем снимке.
             */
            if (
                previousVehicle == null
            ) {

                /*
                 * Если она отсутствовала только
                 * один цикл и снова появилась,
                 * это считаем временным сбоем API,
                 * а не новым ARRIVAL.
                 */
                if (
                    pendingMissing != null
                ) {

                    val lastKnown =
                        pendingMissing.vehicle

                    val positionChanged =
                        lastKnown.position !=
                            currentVehicle.position

                    val statusChanged =
                        lastKnown.status !=
                            currentVehicle.status

                    val becameCalled =
                        lastKnown.status != 3 &&
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
                                    lastKnown,
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
                                    lastKnown,
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
                                    lastKnown,
                                eventType =
                                    VehicleEventType.STATUS_CHANGE
                            )
                    }

                    if (
                        positionChanged ||
                        statusChanged
                    ) {

                        movements +=
                            VehicleMovement(
                                checkpointId =
                                    checkpointId,
                                registrationNumber =
                                    currentVehicle.registrationNumber,
                                timestamp =
                                    currentSnapshot.timestamp,
                                previousPosition =
                                    lastKnown.position,
                                currentPosition =
                                    currentVehicle.position,
                                status =
                                    currentVehicle.status
                            )
                    }

                    continue
                }

                /*
                 * Настоящее новое появление.
                 */
                events +=
                    createArrivalEvent(
                        snapshot =
                            currentSnapshot,
                        vehicle =
                            currentVehicle
                    )

                continue
            }

            val positionChanged =
                previousVehicle.position !=
                    currentVehicle.position

            val statusChanged =
                previousVehicle.status !=
                    currentVehicle.status

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

            if (
                positionChanged ||
                statusChanged
            ) {

                movements +=
                    VehicleMovement(
                        checkpointId =
                            checkpointId,
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
        }

        /*
         * 2. Находим машины,
         * которые были в предыдущем снимке,
         * но отсутствуют сейчас.
         */
        for (
            previousVehicle in
            previousVehicles.values
        ) {

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

            /*
             * Это первое отсутствие.
             */
            if (
                !missingVehicles.containsKey(
                    key
                )
            ) {

                missingVehicles[
                    key
                ] =
                    MissingVehicle(
                        vehicle =
                            previousVehicle,
                        firstMissingTimestamp =
                            currentSnapshot.timestamp,
                        missingCount =
                            1
                    )
            }
        }

        /*
         * 3. Проверяем машины,
         * которые уже отсутствовали
         * на предыдущем цикле.
         */
        val pendingKeys =
            missingVehicles.keys.toList()

        for (
            key in pendingKeys
        ) {

            val missing =
                missingVehicles[
                    key
                ] ?: continue

            /*
             * Нас интересует только текущий КПП.
             */
            if (
                !key.startsWith(
                    "$checkpointId|"
                )
            ) {
                continue
            }

            /*
             * Если машина снова появилась,
             * она уже была удалена
             * из missingVehicles выше.
             */
            if (
                currentVehicles.containsKey(
                    key
                )
            ) {
                continue
            }

            /*
             * Если это отсутствие было создано
             * именно на текущем цикле,
             * пока ничего не делаем.
             */
            if (
                missing.firstMissingTimestamp ==
                currentSnapshot.timestamp
            ) {
                continue
            }

            val newMissingCount =
                missing.missingCount + 1

            if (
                newMissingCount >= 2
            ) {

                val vehicle =
                    missing.vehicle

                events +=
                    VehicleEvent(
                        checkpointId =
                            checkpointId,
                        checkpointName =
                            currentSnapshot.checkpointName,
                        registrationNumber =
                            vehicle.registrationNumber,
                        vehicleType =
                            vehicle.vehicleType,
                        typeQueue =
                            vehicle.typeQueue,

                        /*
                         * Время события ставим
                         * по первому отсутствию.
                         *
                         * Подтверждение произошло
                         * вторым снимком, но фактически
                         * машина исчезла между предыдущим
                         * присутствием и первым отсутствием.
                         */
                        timestamp =
                            missing.firstMissingTimestamp,

                        eventType =
                            VehicleEventType.DISAPPEARED,

                        previousPosition =
                            vehicle.position,

                        currentPosition =
                            null,

                        previousStatus =
                            vehicle.status,

                        currentStatus =
                            null,

                        registrationDate =
                            vehicle.registrationDate,

                        changedDate =
                            vehicle.changedDate
                    )

                missingVehicles.remove(
                    key
                )

            } else {

                missingVehicles[
                    key
                ] =
                    missing.copy(
                        missingCount =
                            newMissingCount
                    )
            }
        }

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
     * Старый интерфейс сохраняем,
     * пока QueueCollector не переведён
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

        missingVehicles.keys
            .filter {
                it.startsWith(
                    prefix
                )
            }
            .forEach {
                missingVehicles.remove(
                    it
                )
            }
    }

    fun resetAll() {

        previousSnapshots.clear()

        missingVehicles.clear()
    }
}
