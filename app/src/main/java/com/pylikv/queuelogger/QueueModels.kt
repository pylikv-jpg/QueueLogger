package com.pylikv.queuelogger

/**
 * Пункт пропуска.
 */
data class Checkpoint(
    val id: String,
    val name: String
)

/**
 * Транспортное средство, обнаруженное в электронной очереди.
 */
data class QueueVehicle(
    val registrationNumber: String,
    val position: Int?,
    val status: Int?,
    val orderId: String? = null,
    val vehicleType: Int? = null
)

/**
 * Один полный снимок состояния очереди.
 *
 * Каждый раз при опросе сервера QueueLogger будет создавать
 * новый QueueSnapshot и сохранять его в историю.
 */
data class QueueSnapshot(
    val checkpointId: String,
    val checkpointName: String,
    val timestamp: Long,
    val vehicles: List<QueueVehicle>
)

/**
 * Изменение положения конкретного автомобиля.
 *
 * Именно эти записи в дальнейшем позволят рассчитывать
 * реальную скорость движения очереди и строить прогнозы.
 */
data class VehicleMovement(
    val checkpointId: String,
    val registrationNumber: String,
    val timestamp: Long,
    val previousPosition: Int?,
    val currentPosition: Int?,
    val status: Int?
)
