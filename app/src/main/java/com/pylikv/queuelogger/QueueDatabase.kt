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
         * Версия 2:
         *
         * добавлена таблица queue_samples,
         * которая хранит размер очереди
         * при каждом опросе.
         */
        private const val DATABASE_VERSION =
            2

        private const val TABLE_MOVEMENTS =
            "vehicle_movements"

        private const val TABLE_QUEUE_SAMPLES =
            "queue_samples"

        /*
         * Общие поля.
         */
        private const val COLUMN_ID =
            "id"

        private const val COLUMN_CHECKPOINT_ID =
            "checkpoint_id"

        private const val COLUMN_TIMESTAMP =
            "timestamp"

        /*
         * Поля movement.
         */
        private const val COLUMN_REGISTRATION_NUMBER =
            "registration_number"

        private const val COLUMN_PREVIOUS_POSITION =
            "previous_position"

        private const val COLUMN_CURRENT_POSITION =
            "current_position"

        private const val COLUMN_STATUS =
            "status"

        /*
         * Поля queue sample.
         */
        private const val COLUMN_CHECKPOINT_NAME =
            "checkpoint_name"

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
    }

    /**
     * Если приложение уже успело создать
     * базу версии 1, просто добавляем
     * новую таблицу статистики.
     */
    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        if (
            oldVersion < 2
        ) {

            createQueueSampleTable(
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
     * Один ряд этой таблицы =
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
     * Сохранить статистический снимок очереди.
     *
     * Здесь не сохраняем повторно каждый автомобиль.
     * Храним компактную информацию о размере очереди.
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
     * Сохранить одно движение.
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

    /**
     * Сохранить список изменений
     * одной транзакцией.
     */
    fun saveMovements(
        movements:
            List<VehicleMovement>
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

            if (
                movement.previousPosition != null
            ) {

                put(
                    COLUMN_PREVIOUS_POSITION,
                    movement.previousPosition
                )

            } else {

                putNull(
                    COLUMN_PREVIOUS_POSITION
                )
            }

            if (
                movement.currentPosition != null
            ) {

                put(
                    COLUMN_CURRENT_POSITION,
                    movement.currentPosition
                )

            } else {

                putNull(
                    COLUMN_CURRENT_POSITION
                )
            }

            if (
                movement.status != null
            ) {

                put(
                    COLUMN_STATUS,
                    movement.status
                )

            } else {

                putNull(
                    COLUMN_STATUS
                )
            }
        }
    }

    /**
     * Общее количество сохранённых движений.
     */
    fun getMovementCount(): Long {

        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_MOVEMENTS
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
     * Количество минутных снимков очередей.
     */
    fun getQueueSampleCount(): Long {

        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_QUEUE_SAMPLES
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
     * Последние движения автомобилей.
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
                            if (
                                cursor.isNull(
                                    previousPositionIndex
                                )
                            ) {

                                null

                            } else {

                                cursor.getInt(
                                    previousPositionIndex
                                )
                            },

                        currentPosition =
                            if (
                                cursor.isNull(
                                    currentPositionIndex
                                )
                            ) {

                                null

                            } else {

                                cursor.getInt(
                                    currentPositionIndex
                                )
                            },

                        status =
                            if (
                                cursor.isNull(
                                    statusIndex
                                )
                            ) {

                                null

                            } else {

                                cursor.getInt(
                                    statusIndex
                                )
                            }
                    )
            }
        }

        return result
    }
}
