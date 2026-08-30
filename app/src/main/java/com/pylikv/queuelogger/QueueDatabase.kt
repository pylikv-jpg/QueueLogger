package com.pylikv.queuelogger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Локальная база статистики QueueLogger.
 */
class QueueDatabase(
    context: Context
) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION
) {

    companion object {

        private const val DATABASE_NAME =
            "queue_logger.db"

        /**
         * Версия 3:
         *
         * добавлена таблица vehicle_events.
         *
         * Старые таблицы НЕ удаляем,
         * поэтому уже собранная статистика
         * остаётся на телефоне.
         */
        private const val DATABASE_VERSION =
            3

        private const val TABLE_MOVEMENTS =
            "vehicle_movements"

        private const val TABLE_QUEUE_SAMPLES =
            "queue_samples"

        private const val TABLE_VEHICLE_EVENTS =
            "vehicle_events"

        /*
         * Общие поля.
         */
        private const val COLUMN_ID =
            "id"

        private const val COLUMN_CHECKPOINT_ID =
            "checkpoint_id"

        private const val COLUMN_CHECKPOINT_NAME =
            "checkpoint_name"

        private const val COLUMN_TIMESTAMP =
            "timestamp"

        private const val COLUMN_REGISTRATION_NUMBER =
            "registration_number"

        /*
         * Старый movement.
         */
        private const val COLUMN_PREVIOUS_POSITION =
            "previous_position"

        private const val COLUMN_CURRENT_POSITION =
            "current_position"

        private const val COLUMN_STATUS =
            "status"

        /*
         * Поля минутного снимка.
         */
        private const val COLUMN_TOTAL_COUNT =
            "total_count"

        private const val COLUMN_CAR_COUNT =
            "car_count"

        private const val COLUMN_TRUCK_COUNT =
            "truck_count"

        private const val COLUMN_BUS_COUNT =
            "bus_count"

        private const val COLUMN_MOTORCYCLE_COUNT =
            "motorcycle_count"

        /*
         * Поля полного события автомобиля.
         */
        private const val COLUMN_EVENT_TYPE =
            "event_type"

        private const val COLUMN_VEHICLE_TYPE =
            "vehicle_type"

        private const val COLUMN_TYPE_QUEUE =
            "type_queue"

        private const val COLUMN_PREVIOUS_STATUS =
            "previous_status"

        private const val COLUMN_CURRENT_STATUS =
            "current_status"

        private const val COLUMN_REGISTRATION_DATE =
            "registration_date"

        private const val COLUMN_CHANGED_DATE =
            "changed_date"
    }

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        createMovementTable(
            db
        )

        createQueueSampleTable(
            db
        )

        createVehicleEventTable(
            db
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        /*
         * Версия 1 -> версия 2.
         */
        if (
            oldVersion < 2
        ) {

            createQueueSampleTable(
                db
            )
        }

        /*
         * Версия 1/2 -> версия 3.
         *
         * Только добавляем новую таблицу.
         * Старые данные не удаляем.
         */
        if (
            oldVersion < 3
        ) {

            createVehicleEventTable(
                db
            )
        }
    }

    private fun createMovementTable(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_MOVEMENTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CHECKPOINT_ID TEXT NOT NULL,
                $COLUMN_REGISTRATION_NUMBER TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_PREVIOUS_POSITION INTEGER,
                $COLUMN_CURRENT_POSITION INTEGER,
                $COLUMN_STATUS INTEGER
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_movement_checkpoint_time
            ON $TABLE_MOVEMENTS (
                $COLUMN_CHECKPOINT_ID,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_movement_vehicle_time
            ON $TABLE_MOVEMENTS (
                $COLUMN_REGISTRATION_NUMBER,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )
    }

    /**
     * Один ряд =
     * один успешный опрос одного КПП.
     */
    private fun createQueueSampleTable(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_QUEUE_SAMPLES (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CHECKPOINT_ID TEXT NOT NULL,
                $COLUMN_CHECKPOINT_NAME TEXT NOT NULL,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_TOTAL_COUNT INTEGER NOT NULL,
                $COLUMN_CAR_COUNT INTEGER NOT NULL,
                $COLUMN_TRUCK_COUNT INTEGER NOT NULL,
                $COLUMN_BUS_COUNT INTEGER NOT NULL,
                $COLUMN_MOTORCYCLE_COUNT INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_queue_sample_checkpoint_time
            ON $TABLE_QUEUE_SAMPLES (
                $COLUMN_CHECKPOINT_ID,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )
    }

    /**
     * Полная история событий автомобилей.
     *
     * Здесь хранятся:
     *
     * ARRIVAL
     * MOVE
     * STATUS_CHANGE
     * CALLED
     * DISAPPEARED
     *
     * А также:
     * тип транспорта,
     * type_queue,
     * старая/новая позиция,
     * старый/новый статус.
     */
    private fun createVehicleEventTable(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS $TABLE_VEHICLE_EVENTS (
                $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                $COLUMN_CHECKPOINT_ID TEXT NOT NULL,
                $COLUMN_CHECKPOINT_NAME TEXT NOT NULL,
                $COLUMN_REGISTRATION_NUMBER TEXT NOT NULL,
                $COLUMN_VEHICLE_TYPE INTEGER,
                $COLUMN_TYPE_QUEUE INTEGER,
                $COLUMN_TIMESTAMP INTEGER NOT NULL,
                $COLUMN_EVENT_TYPE TEXT NOT NULL,
                $COLUMN_PREVIOUS_POSITION INTEGER,
                $COLUMN_CURRENT_POSITION INTEGER,
                $COLUMN_PREVIOUS_STATUS INTEGER,
                $COLUMN_CURRENT_STATUS INTEGER,
                $COLUMN_REGISTRATION_DATE TEXT,
                $COLUMN_CHANGED_DATE TEXT
            )
            """.trimIndent()
        )

        /*
         * Быстрый поиск событий
         * одного КПП по времени.
         */
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_vehicle_event_checkpoint_time
            ON $TABLE_VEHICLE_EVENTS (
                $COLUMN_CHECKPOINT_ID,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )

        /*
         * Быстрое восстановление
         * всей траектории одной машины.
         */
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_vehicle_event_vehicle_time
            ON $TABLE_VEHICLE_EVENTS (
                $COLUMN_CHECKPOINT_ID,
                $COLUMN_REGISTRATION_NUMBER,
                $COLUMN_VEHICLE_TYPE,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )

        /*
         * Для будущей статистики:
         * отдельно легковые/грузовые
         * и разные type_queue.
         */
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_vehicle_event_queue_type
            ON $TABLE_VEHICLE_EVENTS (
                $COLUMN_CHECKPOINT_ID,
                $COLUMN_VEHICLE_TYPE,
                $COLUMN_TYPE_QUEUE,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )

        /*
         * Для быстрого поиска
         * появлений и вызовов.
         */
        db.execSQL(
            """
            CREATE INDEX IF NOT EXISTS
            index_vehicle_event_type_time
            ON $TABLE_VEHICLE_EVENTS (
                $COLUMN_EVENT_TYPE,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )
    }

    /**
     * Сохранить минутный снимок размера очереди.
     */
    fun saveQueueSample(
        snapshot: QueueSnapshot
    ): Long {

        var carCount =
            0

        var truckCount =
            0

        var busCount =
            0

        var motorcycleCount =
            0

        for (
            vehicle in
            snapshot.vehicles
        ) {

            when (
                vehicle.vehicleType
            ) {

                QueueParser.VEHICLE_TYPE_CAR ->

                    carCount++

                QueueParser.VEHICLE_TYPE_TRUCK ->

                    truckCount++

                QueueParser.VEHICLE_TYPE_BUS ->

                    busCount++

                QueueParser.VEHICLE_TYPE_MOTORCYCLE ->

                    motorcycleCount++
            }
        }

        val values =
            ContentValues().apply {

                put(
                    COLUMN_CHECKPOINT_ID,
                    snapshot.checkpointId
                )

                put(
                    COLUMN_CHECKPOINT_NAME,
                    snapshot.checkpointName
                )

                put(
                    COLUMN_TIMESTAMP,
                    snapshot.timestamp
                )

                put(
                    COLUMN_TOTAL_COUNT,
                    snapshot.vehicleCount
                )

                put(
                    COLUMN_CAR_COUNT,
                    carCount
                )

                put(
                    COLUMN_TRUCK_COUNT,
                    truckCount
                )

                put(
                    COLUMN_BUS_COUNT,
                    busCount
                )

                /*
                 * Пока оставляем число
                 * мотоциклов в общем снимке
                 * как исходную информацию.
                 *
                 * В прогностической модели
                 * они использоваться не будут.
                 */
                put(
                    COLUMN_MOTORCYCLE_COUNT,
                    motorcycleCount
                )
            }

        return writableDatabase.insert(
            TABLE_QUEUE_SAMPLES,
            null,
            values
        )
    }

    /**
     * Сохранить одно старое movement.
     */
    fun saveMovement(
        movement: VehicleMovement
    ): Long {

        return writableDatabase.insert(
            TABLE_MOVEMENTS,
            null,
            createMovementValues(
                movement
            )
        )
    }

    fun saveMovements(
        movements: List<VehicleMovement>
    ) {

        if (
            movements.isEmpty()
        ) {
            return
        }

        val db =
            writableDatabase

        db.beginTransaction()

        try {

            for (
                movement in
                movements
            ) {

                db.insert(
                    TABLE_MOVEMENTS,
                    null,
                    createMovementValues(
                        movement
                    )
                )
            }

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()
        }
    }

    private fun createMovementValues(
        movement: VehicleMovement
    ): ContentValues {

        return ContentValues().apply {

            put(
                COLUMN_CHECKPOINT_ID,
                movement.checkpointId
            )

            put(
                COLUMN_REGISTRATION_NUMBER,
                movement.registrationNumber
            )

            put(
                COLUMN_TIMESTAMP,
                movement.timestamp
            )

            putNullableInt(
                COLUMN_PREVIOUS_POSITION,
                movement.previousPosition
            )

            putNullableInt(
                COLUMN_CURRENT_POSITION,
                movement.currentPosition
            )

            putNullableInt(
                COLUMN_STATUS,
                movement.status
            )
        }
    }

    /**
     * Сохранить одно полное событие.
     */
    fun saveVehicleEvent(
        event: VehicleEvent
    ): Long {

        /*
         * Мотоциклы в новую
         * прогностическую историю не записываем.
         */
        if (
            event.vehicleType ==
            QueueParser.VEHICLE_TYPE_MOTORCYCLE
        ) {

            return -1L
        }

        return writableDatabase.insert(
            TABLE_VEHICLE_EVENTS,
            null,
            createVehicleEventValues(
                event
            )
        )
    }

    /**
     * Сохранить список событий
     * одной транзакцией.
     */
    fun saveVehicleEvents(
        events: List<VehicleEvent>
    ) {

        val filteredEvents =
            events.filter {
                it.vehicleType !=
                    QueueParser.VEHICLE_TYPE_MOTORCYCLE
            }

        if (
            filteredEvents.isEmpty()
        ) {
            return
        }

        val db =
            writableDatabase

        db.beginTransaction()

        try {

            for (
                event in
                filteredEvents
            ) {

                db.insert(
                    TABLE_VEHICLE_EVENTS,
                    null,
                    createVehicleEventValues(
                        event
                    )
                )
            }

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()
        }
    }

    private fun createVehicleEventValues(
        event: VehicleEvent
    ): ContentValues {

        return ContentValues().apply {

            put(
                COLUMN_CHECKPOINT_ID,
                event.checkpointId
            )

            put(
                COLUMN_CHECKPOINT_NAME,
                event.checkpointName
            )

            put(
                COLUMN_REGISTRATION_NUMBER,
                event.registrationNumber
            )

            putNullableInt(
                COLUMN_VEHICLE_TYPE,
                event.vehicleType
            )

            /*
             * type_queue сохраняем
             * без интерпретации.
             *
             * Позже по реальным данным
             * определим обычную
             * и приоритетную очередь.
             */
            putNullableInt(
                COLUMN_TYPE_QUEUE,
                event.typeQueue
            )

            put(
                COLUMN_TIMESTAMP,
                event.timestamp
            )

            put(
                COLUMN_EVENT_TYPE,
                event.eventType.name
            )

            putNullableInt(
                COLUMN_PREVIOUS_POSITION,
                event.previousPosition
            )

            putNullableInt(
                COLUMN_CURRENT_POSITION,
                event.currentPosition
            )

            putNullableInt(
                COLUMN_PREVIOUS_STATUS,
                event.previousStatus
            )

            putNullableInt(
                COLUMN_CURRENT_STATUS,
                event.currentStatus
            )

            putNullableString(
                COLUMN_REGISTRATION_DATE,
                event.registrationDate
            )

            putNullableString(
                COLUMN_CHANGED_DATE,
                event.changedDate
            )
        }
    }

    /**
     * Количество старых записей движения.
     */
    fun getMovementCount(): Long {

        return getTableRowCount(
            TABLE_MOVEMENTS
        )
    }

    /**
     * Количество минутных снимков.
     */
    fun getQueueSampleCount(): Long {

        return getTableRowCount(
            TABLE_QUEUE_SAMPLES
        )
    }

    /**
     * Количество полных событий.
     */
    fun getVehicleEventCount(): Long {

        return getTableRowCount(
            TABLE_VEHICLE_EVENTS
        )
    }

    private fun getTableRowCount(
        tableName: String
    ): Long {

        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $tableName
            """.trimIndent(),
            null
        ).use {
            cursor ->

            return if (
                cursor.moveToFirst()
            ) {

                cursor.getLong(
                    0
                )

            } else {

                0L
            }
        }
    }

    /**
     * Последние старые движения.
     */
    fun getRecentMovements(
        limit: Int = 100
    ): List<VehicleMovement> {

        val safeLimit =
            limit.coerceIn(
                1,
                10_000
            )

        val result =
            mutableListOf<VehicleMovement>()

        readableDatabase.query(

            TABLE_MOVEMENTS,

            arrayOf(
                COLUMN_CHECKPOINT_ID,
                COLUMN_REGISTRATION_NUMBER,
                COLUMN_TIMESTAMP,
                COLUMN_PREVIOUS_POSITION,
                COLUMN_CURRENT_POSITION,
                COLUMN_STATUS
            ),

            null,
            null,
            null,
            null,

            "$COLUMN_TIMESTAMP DESC",

            safeLimit.toString()

        ).use {
            cursor ->

            val checkpointIndex =
                cursor.getColumnIndexOrThrow(
                    COLUMN_CHECKPOINT_ID
                )

            val registrationIndex =
                cursor.getColumnIndexOrThrow(
                    COLUMN_REGISTRATION_NUMBER
                )

            val timestampIndex =
                cursor.getColumnIndexOrThrow(
                    COLUMN_TIMESTAMP
                )

            val previousPositionIndex =
                cursor.getColumnIndexOrThrow(
                    COLUMN_PREVIOUS_POSITION
                )

            val currentPositionIndex =
                cursor.getColumnIndexOrThrow(
                    COLUMN_CURRENT_POSITION
                )

            val statusIndex =
                cursor.getColumnIndexOrThrow(
                    COLUMN_STATUS
                )

            while (
                cursor.moveToNext()
            ) {

                result +=
                    VehicleMovement(
                        checkpointId =
                            cursor.getString(
                                checkpointIndex
                            ),

                        registrationNumber =
                            cursor.getString(
                                registrationIndex
                            ),

                        timestamp =
                            cursor.getLong(
                                timestampIndex
                            ),

                        previousPosition =
                            cursor.getNullableInt(
                                previousPositionIndex
                            ),

                        currentPosition =
                            cursor.getNullableInt(
                                currentPositionIndex
                            ),

                        status =
                            cursor.getNullableInt(
                                statusIndex
                            )
                    )
            }
        }

        return result
    }

    /**
     * Вспомогательные функции для NULL.
     */
    private fun ContentValues.putNullableInt(
        key: String,
        value: Int?
    ) {

        if (
            value == null
        ) {

            putNull(
                key
            )

        } else {

            put(
                key,
                value
            )
        }
    }

    private fun ContentValues.putNullableString(
        key: String,
        value: String?
    ) {

        if (
            value == null
        ) {

            putNull(
                key
            )

        } else {

            put(
                key,
                value
            )
        }
    }

    private fun android.database.Cursor.getNullableInt(
        columnIndex: Int
    ): Int? {

        return if (
            isNull(
                columnIndex
            )
        ) {

            null

        } else {

            getInt(
                columnIndex
            )
        }
    }
}
