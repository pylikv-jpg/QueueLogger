package com.pylikv.queuelogger

import android.database.Cursor
import org.json.JSONObject

/**
 * Читает накопленные данные QueueLogger
 * небольшими пакетами для отправки в GitHub.
 *
 * Структуру существующей базы этот класс
 * не изменяет.
 */
class QueueSyncDataSource(
    private val database: QueueDatabase
) {

    companion object {

        private const val TABLE_QUEUE_SAMPLES =
            "queue_samples"

        private const val TABLE_VEHICLE_EVENTS =
            "vehicle_events"

        private const val TABLE_MOVEMENTS =
            "vehicle_movements"

        /**
         * Пакеты намеренно небольшие,
         * чтобы не создавать огромные
         * GitHub-файлы и не расходовать
         * лишнюю память телефона.
         */
        const val DEFAULT_BATCH_SIZE =
            1000
    }

    /**
     * Получить новые минутные снимки очереди.
     */
    fun getQueueSamples(
        afterId: Long,
        limit: Int = DEFAULT_BATCH_SIZE
    ): SyncBatch {

        val sql =
            """
            SELECT
                id,
                checkpoint_id,
                checkpoint_name,
                timestamp,
                total_count,
                car_count,
                truck_count,
                bus_count,
                motorcycle_count
            FROM $TABLE_QUEUE_SAMPLES
            WHERE id > ?
            ORDER BY id ASC
            LIMIT ?
            """.trimIndent()

        return readBatch(
            sql = sql,
            args = arrayOf(
                afterId.toString(),
                limit.toString()
            )
        ) { cursor ->

            JSONObject().apply {

                put(
                    "id",
                    cursor.getLongByName("id")
                )

                put(
                    "checkpoint_id",
                    cursor.getStringByName(
                        "checkpoint_id"
                    )
                )

                put(
                    "checkpoint_name",
                    cursor.getStringByName(
                        "checkpoint_name"
                    )
                )

                put(
                    "timestamp",
                    cursor.getLongByName(
                        "timestamp"
                    )
                )

                put(
                    "total_count",
                    cursor.getIntByName(
                        "total_count"
                    )
                )

                put(
                    "car_count",
                    cursor.getIntByName(
                        "car_count"
                    )
                )

                put(
                    "truck_count",
                    cursor.getIntByName(
                        "truck_count"
                    )
                )

                put(
                    "bus_count",
                    cursor.getIntByName(
                        "bus_count"
                    )
                )

                put(
                    "motorcycle_count",
                    cursor.getIntByName(
                        "motorcycle_count"
                    )
                )
            }
        }
    }

    /**
     * Получить новые полные события автомобилей.
     */
    fun getVehicleEvents(
        afterId: Long,
        limit: Int = DEFAULT_BATCH_SIZE
    ): SyncBatch {

        val sql =
            """
            SELECT
                id,
                checkpoint_id,
                checkpoint_name,
                registration_number,
                vehicle_type,
                type_queue,
                timestamp,
                event_type,
                previous_position,
                current_position,
                previous_status,
                current_status,
                registration_date,
                changed_date
            FROM $TABLE_VEHICLE_EVENTS
            WHERE id > ?
            ORDER BY id ASC
            LIMIT ?
            """.trimIndent()

        return readBatch(
            sql = sql,
            args = arrayOf(
                afterId.toString(),
                limit.toString()
            )
        ) { cursor ->

            JSONObject().apply {

                put(
                    "id",
                    cursor.getLongByName("id")
                )

                put(
                    "checkpoint_id",
                    cursor.getStringByName(
                        "checkpoint_id"
                    )
                )

                put(
                    "checkpoint_name",
                    cursor.getStringByName(
                        "checkpoint_name"
                    )
                )

                put(
                    "registration_number",
                    cursor.getStringByName(
                        "registration_number"
                    )
                )

                putNullable(
                    "vehicle_type",
                    cursor.getNullableIntByName(
                        "vehicle_type"
                    )
                )

                putNullable(
                    "type_queue",
                    cursor.getNullableIntByName(
                        "type_queue"
                    )
                )

                put(
                    "timestamp",
                    cursor.getLongByName(
                        "timestamp"
                    )
                )

                put(
                    "event_type",
                    cursor.getStringByName(
                        "event_type"
                    )
                )

                putNullable(
                    "previous_position",
                    cursor.getNullableIntByName(
                        "previous_position"
                    )
                )

                putNullable(
                    "current_position",
                    cursor.getNullableIntByName(
                        "current_position"
                    )
                )

                putNullable(
                    "previous_status",
                    cursor.getNullableIntByName(
                        "previous_status"
                    )
                )

                putNullable(
                    "current_status",
                    cursor.getNullableIntByName(
                        "current_status"
                    )
                )

                putNullable(
                    "registration_date",
                    cursor.getNullableStringByName(
                        "registration_date"
                    )
                )

                putNullable(
                    "changed_date",
                    cursor.getNullableStringByName(
                        "changed_date"
                    )
                )
            }
        }
    }

    /**
     * Старые movement-записи тоже сохраним.
     *
     * В первую очередь они нужны как
     * дополнительная историческая статистика.
     */
    fun getMovements(
        afterId: Long,
        limit: Int = DEFAULT_BATCH_SIZE
    ): SyncBatch {

        val sql =
            """
            SELECT
                id,
                checkpoint_id,
                registration_number,
                timestamp,
                previous_position,
                current_position,
                status
            FROM $TABLE_MOVEMENTS
            WHERE id > ?
            ORDER BY id ASC
            LIMIT ?
            """.trimIndent()

        return readBatch(
            sql = sql,
            args = arrayOf(
                afterId.toString(),
                limit.toString()
            )
        ) { cursor ->

            JSONObject().apply {

                put(
                    "id",
                    cursor.getLongByName("id")
                )

                put(
                    "checkpoint_id",
                    cursor.getStringByName(
                        "checkpoint_id"
                    )
                )

                put(
                    "registration_number",
                    cursor.getStringByName(
                        "registration_number"
                    )
                )

                put(
                    "timestamp",
                    cursor.getLongByName(
                        "timestamp"
                    )
                )

                putNullable(
                    "previous_position",
                    cursor.getNullableIntByName(
                        "previous_position"
                    )
                )

                putNullable(
                    "current_position",
                    cursor.getNullableIntByName(
                        "current_position"
                    )
                )

                putNullable(
                    "status",
                    cursor.getNullableIntByName(
                        "status"
                    )
                )
            }
        }
    }

    /**
     * Универсальное чтение результата запроса.
     *
     * Каждая строка превращается в отдельный
     * JSON-объект, то есть итоговый файл
     * имеет формат JSONL:
     *
     * { ... }
     * { ... }
     * { ... }
     */
    private fun readBatch(
        sql: String,
        args: Array<String>,
        mapper: (Cursor) -> JSONObject
    ): SyncBatch {

        val cursor =
            database
                .readableDatabase
                .rawQuery(
                    sql,
                    args
                )

        cursor.use {

            if (!it.moveToFirst()) {

                return SyncBatch(
                    content = "",
                    firstId = 0L,
                    lastId = 0L,
                    rowCount = 0
                )
            }

            val builder =
                StringBuilder()

            var firstId =
                0L

            var lastId =
                0L

            var count =
                0

            do {

                val json =
                    mapper(it)

                val id =
                    json.optLong(
                        "id",
                        0L
                    )

                if (count == 0) {
                    firstId = id
                }

                lastId = id

                builder
                    .append(
                        json.toString()
                    )
                    .append('\n')

                count++

            } while (
                it.moveToNext()
            )

            return SyncBatch(
                content =
                    builder.toString(),

                firstId =
                    firstId,

                lastId =
                    lastId,

                rowCount =
                    count
            )
        }
    }
}

/**
 * Один пакет данных для синхронизации.
 */
data class SyncBatch(
    val content: String,
    val firstId: Long,
    val lastId: Long,
    val rowCount: Int
) {

    val isEmpty: Boolean
        get() =
            rowCount == 0 ||
                content.isBlank()
}

/**
 * Вспомогательные функции Cursor.
 */
private fun Cursor.getLongByName(
    name: String
): Long {

    return getLong(
        getColumnIndexOrThrow(name)
    )
}

private fun Cursor.getIntByName(
    name: String
): Int {

    return getInt(
        getColumnIndexOrThrow(name)
    )
}

private fun Cursor.getStringByName(
    name: String
): String {

    return getString(
        getColumnIndexOrThrow(name)
    )
}

private fun Cursor.getNullableIntByName(
    name: String
): Int? {

    val index =
        getColumnIndexOrThrow(name)

    return if (
        isNull(index)
    ) {
        null
    } else {
        getInt(index)
    }
}

private fun Cursor.getNullableStringByName(
    name: String
): String? {

    val index =
        getColumnIndexOrThrow(name)

    return if (
        isNull(index)
    ) {
        null
    } else {
        getString(index)
    }
}

private fun JSONObject.putNullable(
    key: String,
    value: Any?
) {

    if (value == null) {

        put(
            key,
            JSONObject.NULL
        )

    } else {

        put(
            key,
            value
        )
    }
}
