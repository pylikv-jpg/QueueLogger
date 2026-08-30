package com.pylikv.queuelogger

/**
 * Пункт пропуска.
 */
data class Checkpoint(
    val id: String,
    val name: String
)

/**
 * Тип события автомобиля.
 */
enum class VehicleEventType {

    /**
     * Автомобиль впервые появился
     * в наблюдаемой очереди.
     */
    ARRIVAL,

    /**
     * Изменилась позиция автомобиля.
     */
    MOVE,

    /**
     * Изменился серверный статус.
     */
    STATUS_CHANGE,

    /**
     * Сервер явно сообщил status == 3.
     */
    CALLED,

    /**
     * Автомобиль отсутствует уже
     * в двух последовательных
     * успешных снимках.
     */
    DISAPPEARED
}

/**
 * Транспортное средство из одного
 * снимка электронной очереди.
 */
data class QueueVehicle(

    val registrationNumber: String,

    /**
     * Текущая позиция.
     * Берётся непосредственно из order_id.
     */
    val position: Int?,

    val status: Int?,

    val orderId: String? = null,

    /**
     * 1 — легковой
     * 2 — грузовой
     * 3 — автобус
     * 4 — мотоцикл
     */
    val vehicleType: Int? = null,

    /**
     * Исходный серверный type_queue.
     *
     * Пока не интерпретируем его,
     * а сохраняем как есть.
     */
    val typeQueue: Int? = null,

    val registrationDate: String? = null,

    val changedDate: String? = null
)

/**
 * Полный снимок очереди одного КПП.
 */
data class QueueSnapshot(

    val checkpointId: String,

    val checkpointName: String,

    val timestamp: Long,

    val vehicles: List<QueueVehicle>
) {

    val vehicleCount: Int
        get() = vehicles.size
}

/**
 * Старый компактный журнал движения.
 *
 * Пока оставляем для совместимости
 * с уже работающей базой и сервисом.
 */
data class VehicleMovement(

    val checkpointId: String,

    val registrationNumber: String,

    val timestamp: Long,

    val previousPosition: Int?,

    val currentPosition: Int?,

    val status: Int?
)

/**
 * Полное историческое событие автомобиля.
 *
 * Именно эти записи впоследствии позволят
 * восстановить весь путь автомобиля
 * через очередь.
 */
data class VehicleEvent(

    val checkpointId: String,

    val checkpointName: String,

    val registrationNumber: String,

    /**
     * Тип транспорта.
     *
     * Позволит полностью разделить
     * статистику легковых и грузовых.
     */
    val vehicleType: Int?,

    /**
     * Исходный серверный type_queue.
     *
     * Нужен для последующего разделения
     * обычной и приоритетной очереди.
     */
    val typeQueue: Int?,

    val timestamp: Long,

    val eventType: VehicleEventType,

    /**
     * Позиция до события.
     *
     * Для ARRIVAL будет null.
     */
    val previousPosition: Int?,

    /**
     * Позиция после события.
     *
     * Для ARRIVAL здесь будет сохранена
     * позиция, на которой автомобиль
     * впервые появился.
     */
    val currentPosition: Int?,

    val previousStatus: Int?,

    val currentStatus: Int?,

    /**
     * Серверное время регистрации.
     */
    val registrationDate: String?,

    /**
     * Серверное время последнего изменения.
     */
    val changedDate: String?
)
