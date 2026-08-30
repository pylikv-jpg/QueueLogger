package com.pylikv.queuelogger

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * Локальная база данных QueueLogger.
 *
 * Здесь постоянно хранится история движения автомобилей.
 * Данные сохраняются даже после закрытия приложения
 * и перезагрузки телефона.
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

        private const val DATABASE_VERSION =
            1

        private const val TABLE_MOVEMENTS =
            "vehicle_movements"

        private const val COLUMN_ID =
            "id"

        private const val COLUMN_CHECKPOINT_ID =
            "checkpoint_id"

        private const val COLUMN_REGISTRATION_NUMBER =
            "registration_number"

        private const val COLUMN_TIMESTAMP =
            "timestamp"

        private const val COLUMN_PREVIOUS_POSITION =
            "previous_position"

        private const val COLUMN_CURRENT_POSITION =
            "current_position"

        private const val COLUMN_STATUS =
            "status"
    }

    override fun onCreate(
        db: SQLiteDatabase
    ) {

        db.execSQL(
            """
            CREATE TABLE $TABLE_MOVEMENTS (
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
            CREATE INDEX index_movement_checkpoint_time
            ON $TABLE_MOVEMENTS (
                $COLUMN_CHECKPOINT_ID,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE INDEX index_movement_vehicle_time
            ON $TABLE_MOVEMENTS (
                $COLUMN_REGISTRATION_NUMBER,
                $COLUMN_TIMESTAMP
            )
            """.trimIndent()
        )
    }

    override fun onUpgrade(
        db: SQLiteDatabase,
        oldVersion: Int,
        newVersion: Int
    ) {

        /*
         * Сейчас версия базы первая.
         *
         * Когда структура базы изменится,
         * миграцию добавим сюда.
         */
    }

    /**
     * Сохранить одно изменение автомобиля.
     */
    fun saveMovement(
        movement: VehicleMovement
    ): Long {

        val values =
            ContentValues().apply {

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

                if (movement.previousPosition != null) {
                    put(
                        COLUMN_PREVIOUS_POSITION,
                        movement.previousPosition
                    )
                } else {
                    putNull(
                        COLUMN_PREVIOUS_POSITION
                    )
                }

                if (movement.currentPosition != null) {
                    put(
                        COLUMN_CURRENT_POSITION,
                        movement.currentPosition
                    )
                } else {
                    putNull(
                        COLUMN_CURRENT_POSITION
                    )
                }

                if (movement.status != null) {
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

        return writableDatabase.insert(
            TABLE_MOVEMENTS,
            null,
            values
        )
    }

    /**
     * Сохранить сразу список изменений.
     *
     * Используем одну транзакцию,
     * чтобы большое количество записей
     * сохранялось быстрее.
     */
    fun saveMovements(
        movements: List<VehicleMovement>
    ) {

        if (movements.isEmpty()) {
            return
        }

        val db =
            writableDatabase

        db.beginTransaction()

        try {

            for (movement in movements) {

                val values =
                    ContentValues().apply {

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

                        if (movement.previousPosition != null) {
                            put(
                                COLUMN_PREVIOUS_POSITION,
                                movement.previousPosition
                            )
                        } else {
                            putNull(
                                COLUMN_PREVIOUS_POSITION
                            )
                        }

                        if (movement.currentPosition != null) {
                            put(
                                COLUMN_CURRENT_POSITION,
                                movement.currentPosition
                            )
                        } else {
                            putNull(
                                COLUMN_CURRENT_POSITION
                            )
                        }

                        if (movement.status != null) {
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

                db.insert(
                    TABLE_MOVEMENTS,
                    null,
                    values
                )
            }

            db.setTransactionSuccessful()

        } finally {

            db.endTransaction()
        }
    }

    /**
     * Количество накопленных изменений.
     */
    fun getMovementCount(): Long {

        readableDatabase.rawQuery(
            """
            SELECT COUNT(*)
            FROM $TABLE_MOVEMENTS
            """.trimIndent(),
            null
        ).use { cursor ->

            return if (cursor.moveToFirst()) {
                cursor.getLong(0)
            } else {
                0L
            }
        }
    }

    /**
     * Получить последние изменения.
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
        ).use { cursor ->

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

            while (cursor.moveToNext()) {

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
