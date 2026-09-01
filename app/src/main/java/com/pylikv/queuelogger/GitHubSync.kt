package com.pylikv.queuelogger

import android.content.Context
import android.util.Base64
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

/**
 * Отправка данных QueueLogger в отдельный GitHub-репозиторий.
 *
 * ВАЖНО:
 * GitHub token НЕ хранится в исходном коде.
 * Пользователь вводит его в приложении один раз,
 * после чего он хранится только локально на устройстве.
 */
object GitHubSync {

    private const val PREFS_NAME =
        "queue_logger_github_sync"

    private const val KEY_TOKEN =
        "github_token"

    private const val KEY_LAST_SUCCESS =
        "last_success"

    private const val KEY_LAST_ERROR =
        "last_error"

    private const val OWNER =
        "pylikv-jpg"

    private const val REPOSITORY =
        "QueueLoggerData"

    private const val BRANCH =
        "main"

    /**
     * Сохранить токен локально.
     */
    fun saveToken(
        context: Context,
        token: String
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_TOKEN,
                token.trim()
            )
            .apply()
    }

    /**
     * Проверить, сохранён ли токен.
     */
    fun hasToken(
        context: Context
    ): Boolean {

        return getToken(context)
            .isNotBlank()
    }

    /**
     * Удалить сохранённый токен.
     */
    fun clearToken(
        context: Context
    ) {
        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .remove(KEY_TOKEN)
            .apply()
    }

    /**
     * Время последней успешной отправки.
     */
    fun getLastSuccess(
        context: Context
    ): Long {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getLong(
                KEY_LAST_SUCCESS,
                0L
            )
    }

    /**
     * Последняя ошибка синхронизации.
     */
    fun getLastError(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_LAST_ERROR,
                ""
            )
            .orEmpty()
    }

    /**
     * Отправить текстовый пакет данных.
     *
     * Каждый пакет получает уникальное имя,
     * поэтому старые данные в репозитории
     * не перезаписываются.
     */
    fun uploadBatch(
        context: Context,
        content: String,
        dataType: String
    ): SyncResult {

        if (content.isBlank()) {
            return SyncResult(
                success = false,
                message = "Нет данных для отправки"
            )
        }

        val token =
            getToken(context)

        if (token.isBlank()) {

            saveError(
                context,
                "GitHub token не настроен"
            )

            return SyncResult(
                success = false,
                message = "GitHub token не настроен"
            )
        }

        return try {

            val path =
                buildRemotePath(
                    dataType = dataType
                )

            val apiUrl =
                "https://api.github.com/repos/" +
                    "$OWNER/$REPOSITORY/contents/$path"

            val connection =
                URL(apiUrl)
                    .openConnection() as
                    HttpURLConnection

            try {

                connection.requestMethod =
                    "PUT"

                connection.connectTimeout =
                    20_000

                connection.readTimeout =
                    30_000

                connection.doOutput =
                    true

                connection.setRequestProperty(
                    "Authorization",
                    "Bearer $token"
                )

                connection.setRequestProperty(
                    "Accept",
                    "application/vnd.github+json"
                )

                connection.setRequestProperty(
                    "X-GitHub-Api-Version",
                    "2022-11-28"
                )

                connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=utf-8"
                )

                val encodedContent =
                    Base64.encodeToString(
                        content.toByteArray(
                            Charsets.UTF_8
                        ),
                        Base64.NO_WRAP
                    )

                val body =
                    JSONObject().apply {

                        put(
                            "message",
                            "QueueLogger automatic data upload"
                        )

                        put(
                            "content",
                            encodedContent
                        )

                        put(
                            "branch",
                            BRANCH
                        )
                    }
                        .toString()
                        .toByteArray(
                            Charsets.UTF_8
                        )

                connection
                    .outputStream
                    .use { output ->

                        output.write(body)
                        output.flush()
                    }

                val responseCode =
                    connection.responseCode

                val responseText =
                    readResponse(
                        connection,
                        responseCode
                    )

                if (
                    responseCode == 200 ||
                    responseCode == 201
                ) {

                    saveSuccess(context)

                    SyncResult(
                        success = true,
                        message =
                            "Данные отправлены",
                        remotePath =
                            path
                    )

                } else {

                    val message =
                        "GitHub HTTP $responseCode: " +
                            responseText.take(500)

                    saveError(
                        context,
                        message
                    )

                    SyncResult(
                        success = false,
                        message = message
                    )
                }

            } finally {

                connection.disconnect()
            }

        } catch (
            e: Exception
        ) {

            val message =
                e.message
                    ?: e.javaClass.simpleName

            saveError(
                context,
                message
            )

            SyncResult(
                success = false,
                message = message
            )
        }
    }

    /**
     * Формируем уникальный путь:
     *
     * data/2026-09-01/events/
     * 2026-09-01_18-45-00_uuid.jsonl
     */
    private fun buildRemotePath(
        dataType: String
    ): String {

        val now =
            Date()

        val dayFormat =
            SimpleDateFormat(
                "yyyy-MM-dd",
                Locale.US
            )

        val timeFormat =
            SimpleDateFormat(
                "yyyy-MM-dd_HH-mm-ss",
                Locale.US
            )

        val zone =
            TimeZone.getDefault()

        dayFormat.timeZone =
            zone

        timeFormat.timeZone =
            zone

        val day =
            dayFormat.format(now)

        val timestamp =
            timeFormat.format(now)

        val safeType =
            dataType
                .lowercase(Locale.US)
                .replace(
                    Regex("[^a-z0-9_-]"),
                    "_"
                )
                .ifBlank {
                    "data"
                }

        val unique =
            UUID.randomUUID()
                .toString()
                .take(8)

        return "data/" +
            "$day/" +
            "$safeType/" +
            "${timestamp}_$unique.jsonl"
    }

    /**
     * Читаем ответ GitHub как текст.
     */
    private fun readResponse(
        connection: HttpURLConnection,
        responseCode: Int
    ): String {

        val stream =
            if (
                responseCode in 200..299
            ) {
                connection.inputStream
            } else {
                connection.errorStream
            }

        if (stream == null) {
            return ""
        }

        return stream
            .bufferedReader(
                Charsets.UTF_8
            )
            .use {
                it.readText()
            }
    }

    private fun getToken(
        context: Context
    ): String {

        return context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .getString(
                KEY_TOKEN,
                ""
            )
            .orEmpty()
            .trim()
    }

    private fun saveSuccess(
        context: Context
    ) {

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putLong(
                KEY_LAST_SUCCESS,
                System.currentTimeMillis()
            )
            .putString(
                KEY_LAST_ERROR,
                ""
            )
            .apply()
    }

    private fun saveError(
        context: Context,
        error: String
    ) {

        context
            .getSharedPreferences(
                PREFS_NAME,
                Context.MODE_PRIVATE
            )
            .edit()
            .putString(
                KEY_LAST_ERROR,
                error
            )
            .apply()
    }
}

/**
 * Результат одной попытки отправки.
 */
data class SyncResult(
    val success: Boolean,
    val message: String,
    val remotePath: String? = null
)
