package com.pylikv.queuelogger

/**
 * Пункт пропуска.
 */
data class Checkpoint(
    val id: String,
    val name: String
)

/**
 * Транспортное средство из одного
 * снимка электронной очереди.
 */
data class QueueVehicle(

    /**
     * Регистрационный номер автомобиля.
     */
    val registrationNumber: String,

    /**
     * Текущая позиция автомобиля.
     *
     * Берётся непосредственно
     * из серверного order_id.
     */
    val position: Int?,

    /**
     * Серверный статус.
     *
     * В частности status == 3
     * используется как подтверждённый вызов.
     */
    val status: Int?,

    /**
     * Исходное значение order_id.
     *
     * Пока сохраняем строкой
     * для совместимости с уже написанным кодом.
     */
    val orderId: String? = null,

    /**
     * Тип транспорта:
     *
     * 1 — легковой
     * 2 — грузовой
     * 3 — автобус
     * 4 — мотоцикл
     */
    val vehicleType: Int? = null,

    /**
     * Серверный type_queue.
     */
    val typeQueue: Int? = null,

    /**
     * Дата регистрации автомобиля,
     * возвращённая сервером.
     */
    val registrationDate: String? = null,

    /**
     * Дата последнего изменения записи
     * на сервере.
     */
    val changedDate: String? = null
)

/**
 * Один полный снимок состояния очереди
 * конкретного пункта пропуска.
 *
 * Такой снимок будет формироваться
 * при каждом успешном опросе сервера.
 */
data class QueueSnapshot(

    /**
     * UUID пункта пропуска.
     */
    val checkpointId: String,

    /**
     * Название пункта пропуска.
     */
    val checkpointName: String,

    /**
     * Время получения данных QueueLogger.
     */
    val timestamp: Long,

    /**
     * Все автомобили, которые сервер
     * вернул для этого КПП в данный момент.
     */
    val vehicles: List<QueueVehicle>
) {

    /**
     * Полный размер очереди
     * в момент снимка.
     */
    val vehicleCount: Int
        get() =
            vehicles.size
}

/**
 * Изменение положения конкретного автомобиля.
 *
 * Эту таблицу оставляем как быстрый журнал
 * фактических движений очереди.
 *
 * Одновременно с ней позже будем хранить
 * полные снимки QueueSnapshot.
 */
data class VehicleMovement(

    val checkpointId: String,

    val registrationNumber: String,

    val timestamp: Long,

    val previousPosition: Int?,

    val currentPosition: Int?,

    val status: Int?
)
