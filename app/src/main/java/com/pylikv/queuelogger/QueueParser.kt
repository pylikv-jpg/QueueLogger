package com.pylikv.queuelogger

import org.json.JSONArray
import org.json.JSONObject

/**
 * Разбирает JSON, полученный от Belarusborder,
 * и превращает его в данные QueueLogger.
 */
class QueueParser {

    companion object {

        const val VEHICLE_TYPE_CAR =
            1

        const val VEHICLE_TYPE_TRUCK =
            2

        const val VEHICLE_TYPE_BUS =
            3

        const val VEHICLE_TYPE_MOTORCYCLE =
            4
    }

    /**
     * Создать полный снимок очереди.
     */
    fun parseSnapshot(
        json: String,
        checkpointId: String,
        checkpointName: String,
        timestamp: Long =
            System.currentTimeMillis()
    ): QueueSnapshot {

        return QueueSnapshot(

            checkpointId =
                checkpointId,

            checkpointName =
                checkpointName,

            timestamp =
                timestamp,

            vehicles =
                parseVehicles(
                    json
                )
        )
    }

    /**
     * Получить все транспортные средства
     * из ответа сервера.
     */
    fun parseVehicles(
        json: String
    ): List<QueueVehicle> {

        val root =
            JSONObject(
                json
            )

        val result =
            mutableListOf<QueueVehicle>()

        parseQueueArray(
            root = root,
            arrayName =
                "carLiveQueue",
            vehicleType =
                VEHICLE_TYPE_CAR,
            result =
                result
        )

        parseQueueArray(
            root = root,
            arrayName =
                "truckLiveQueue",
            vehicleType =
                VEHICLE_TYPE_TRUCK,
            result =
                result
        )

        parseQueueArray(
            root = root,
            arrayName =
                "busLiveQueue",
            vehicleType =
                VEHICLE_TYPE_BUS,
            result =
                result
        )

        parseQueueArray(
            root = root,
            arrayName =
                "motorcycleLiveQueue",
            vehicleType =
                VEHICLE_TYPE_MOTORCYCLE,
            result =
                result
        )

        return result
    }

    /**
     * Разбор одного массива очереди.
     */
    private fun parseQueueArray(
        root: JSONObject,
        arrayName: String,
        vehicleType: Int,
        result:
            MutableList<QueueVehicle>
    ) {

        val queue =
            root.optJSONArray(
                arrayName
            )
                ?: JSONArray()

        for (
            index in
            0 until queue.length()
        ) {

            val item =
                queue.optJSONObject(
                    index
                )
                    ?: continue

            val registrationNumber =
                readNullableString(
                    item,
                    "regnum"
                )
                    ?: continue

            val orderId =
                readNullableInt(
                    item,
                    "order_id"
                )

            result +=
                QueueVehicle(

                    registrationNumber =
                        registrationNumber,

                    position =
                        orderId,

                    status =
                        readNullableInt(
                            item,
                            "status"
                        ),

                    orderId =
                        orderId
                            ?.toString(),

                    vehicleType =
                        vehicleType,

                    typeQueue =
                        readNullableInt(
                            item,
                            "type_queue"
                        ),

                    registrationDate =
                        readNullableString(
                            item,
                            "registration_date"
                        ),

                    changedDate =
                        readNullableString(
                            item,
                            "changed_date"
                        )
                )
        }
    }

    /**
     * Сервер может вернуть число
     * как Number или как String.
     */
    private fun readNullableInt(
        item: JSONObject,
        key: String
    ): Int? {

        if (
            !item.has(key) ||
            item.isNull(key)
        ) {

            return null
        }

        return when (
            val value =
                item.opt(
                    key
                )
        ) {

            is Number ->

                value.toInt()

            is String ->

                value
                    .trim()
                    .toIntOrNull()

            else ->

                null
        }
    }

    /**
     * Безопасное чтение строкового поля.
     *
     * Пустые строки превращаем в null,
     * чтобы они не засоряли историю.
     */
    private fun readNullableString(
        item: JSONObject,
        key: String
    ): String? {

        if (
            !item.has(key) ||
            item.isNull(key)
        ) {

            return null
        }

        return item
            .optString(
                key,
                ""
            )
            .trim()
            .takeIf {
                it.isNotEmpty()
            }
    }
}
