package com.pylikv.queuelogger

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * Постоянный foreground service QueueLogger.
 *
 * Он поддерживает сбор данных независимо
 * от того, открыт экран приложения или нет.
 */
class QueueLoggerService : Service() {

    companion object {

        const val ACTION_START =
            "com.pylikv.queuelogger.action.START"

        const val ACTION_STOP =
            "com.pylikv.queuelogger.action.STOP"

        const val EXTRA_CHECKPOINT_IDS =
            "checkpoint_ids"

        const val EXTRA_CHECKPOINT_NAMES =
            "checkpoint_names"

        private const val NOTIFICATION_CHANNEL_ID =
            "queue_logger_collection"

        private const val NOTIFICATION_CHANNEL_NAME =
            "Сбор статистики очереди"

        private const val NOTIFICATION_ID =
            1001

        private const val PREFS_NAME =
            "queue_logger_service"

        private const val PREF_CHECKPOINT_IDS =
            "checkpoint_ids"

        private const val PREF_CHECKPOINT_NAMES =
            "checkpoint_names"

        private const val SEPARATOR =
            "\u001F"
    }

    private lateinit var pollingManager:
        QueuePollingManager

    override fun onCreate() {

        super.onCreate()

        createNotificationChannel()

        pollingManager =
            QueuePollingManager(
                applicationContext
            )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        when (intent?.action) {

            ACTION_STOP -> {

                stopCollection()

                return START_NOT_STICKY
            }

            ACTION_START -> {

                val checkpoints =
                    readCheckpointsFromIntent(
                        intent
                    )

                if (checkpoints.isNotEmpty()) {

                    saveCheckpoints(
                        checkpoints
                    )

                    startForegroundMode()

                    startCollection(
                        checkpoints
                    )
                } else {

                    val savedCheckpoints =
                        loadSavedCheckpoints()

                    if (savedCheckpoints.isNotEmpty()) {

                        startForegroundMode()

                        startCollection(
                            savedCheckpoints
                        )

                    } else {

                        stopSelf()
                    }
                }
            }

            else -> {

                /*
                 * Android может восстановить сервис
                 * после уничтожения процесса
                 * и передать null Intent.
                 *
                 * В таком случае восстанавливаем
                 * последний сохранённый список КПП.
                 */
                val savedCheckpoints =
                    loadSavedCheckpoints()

                if (savedCheckpoints.isNotEmpty()) {

                    startForegroundMode()

                    startCollection(
                        savedCheckpoints
                    )

                } else {

                    stopSelf()
                }
            }
        }

        return START_STICKY
    }

    /**
     * Переводим сервис в foreground
     * сразу после запуска.
     */
    private fun startForegroundMode() {

        val notification =
            buildNotification(
                text =
                    "Сбор статистики активен"
            )

        if (Build.VERSION.SDK_INT >= 34) {

            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo
                    .FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )

        } else {

            startForeground(
                NOTIFICATION_ID,
                notification
            )
        }
    }

    /**
     * Запустить минутный цикл опроса.
     */
    private fun startCollection(
        checkpoints: List<Checkpoint>
    ) {

        if (pollingManager.isRunning) {
            return
        }

        pollingManager.start(

            checkpoints =
                checkpoints,

            onResult = {
                result ->

                updateNotification(
                    buildString {

                        append(
                            result.checkpointName
                        )

                        append(": ")

                        append(
                            result.vehicleCount
                        )

                        append(
                            " авто"
                        )

                        if (
                            result.movementCount > 0
                        ) {

                            append(
                                ", изменений "
                            )

                            append(
                                result.movementCount
                            )
                        }

                        append(
                            " · записей "
                        )

                        append(
                            result.totalStoredMovements
                        )
                    }
                )
            },

            onError = {
                checkpoint,
                throwable ->

                val message =
                    throwable.message
                        ?.takeIf {
                            it.isNotBlank()
                        }
                        ?: "ошибка соединения"

                updateNotification(
                    "${checkpoint.name}: $message"
                )
            }
        )
    }

    /**
     * Полностью остановить сбор.
     */
    private fun stopCollection() {

        if (
            ::pollingManager.isInitialized
        ) {

            pollingManager.stop()
        }

        clearSavedCheckpoints()

        stopForeground(
            STOP_FOREGROUND_REMOVE
        )

        stopSelf()
    }

    /**
     * Уведомление foreground service.
     */
    private fun buildNotification(
        text: String
    ): Notification {

        val openAppIntent =
            Intent(
                this,
                MainActivity::class.java
            )

        val openAppPendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        val stopIntent =
            Intent(
                this,
                QueueLoggerService::class.java
            ).apply {

                action =
                    ACTION_STOP
            }

        val stopPendingIntent =
            PendingIntent.getService(
                this,
                1,
                stopIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or
                    PendingIntent.FLAG_IMMUTABLE
            )

        return NotificationCompat
            .Builder(
                this,
                NOTIFICATION_CHANNEL_ID
            )
            .setSmallIcon(
                android.R.drawable
                    .stat_notify_sync
            )
            .setContentTitle(
                "QueueLogger"
            )
            .setContentText(
                text
            )
            .setStyle(
                NotificationCompat
                    .BigTextStyle()
                    .bigText(
                        text
                    )
            )
            .setContentIntent(
                openAppPendingIntent
            )
            .setOngoing(
                true
            )
            .setOnlyAlertOnce(
                true
            )
            .setCategory(
                NotificationCompat
                    .CATEGORY_SERVICE
            )
            .setPriority(
                NotificationCompat
                    .PRIORITY_LOW
            )
            .addAction(
                android.R.drawable
                    .ic_media_pause,
                "Остановить сбор",
                stopPendingIntent
            )
            .build()
    }

    /**
     * Обновить текст постоянного уведомления.
     */
    private fun updateNotification(
        text: String
    ) {

        try {

            NotificationManagerCompat
                .from(this)
                .notify(
                    NOTIFICATION_ID,
                    buildNotification(
                        text
                    )
                )

        } catch (
            ignored: SecurityException
        ) {

            /*
             * На Android 13+ пользователь
             * может запретить обычные уведомления.
             *
             * Сам foreground service при этом
             * не должен аварийно завершаться.
             */
        }
    }

    /**
     * Создание канала уведомлений.
     */
    private fun createNotificationChannel() {

        if (
            Build.VERSION.SDK_INT <
            Build.VERSION_CODES.O
        ) {
            return
        }

        val channel =
            NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                NOTIFICATION_CHANNEL_NAME,
                NotificationManager
                    .IMPORTANCE_LOW
            ).apply {

                description =
                    "Постоянное уведомление во время сбора статистики электронной очереди"

                setSound(
                    null,
                    null
                )

                enableVibration(
                    false
                )
            }

        val manager =
            getSystemService(
                NotificationManager::class.java
            )

        manager.createNotificationChannel(
            channel
        )
    }

    /**
     * Получить список КПП из Intent.
     */
    private fun readCheckpointsFromIntent(
        intent: Intent
    ): List<Checkpoint> {

        val ids =
            intent.getStringArrayListExtra(
                EXTRA_CHECKPOINT_IDS
            ).orEmpty()

        val names =
            intent.getStringArrayListExtra(
                EXTRA_CHECKPOINT_NAMES
            ).orEmpty()

        if (
            ids.isEmpty() ||
            ids.size != names.size
        ) {

            return emptyList()
        }

        return ids.indices.mapNotNull {
            index ->

            val id =
                ids[index].trim()

            val name =
                names[index].trim()

            if (
                id.isBlank() ||
                name.isBlank()
            ) {

                null

            } else {

                Checkpoint(
                    id = id,
                    name = name
                )
            }
        }
    }

    /**
     * Сохраняем выбранные КПП,
     * чтобы восстановить сервис
     * после уничтожения процесса.
     */
    private fun saveCheckpoints(
        checkpoints: List<Checkpoint>
    ) {

        val ids =
            checkpoints.joinToString(
                separator =
                    SEPARATOR
            ) {
                it.id
            }

        val names =
            checkpoints.joinToString(
                separator =
                    SEPARATOR
            ) {
                it.name
            }

        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .putString(
                PREF_CHECKPOINT_IDS,
                ids
            )
            .putString(
                PREF_CHECKPOINT_NAMES,
                names
            )
            .apply()
    }

    /**
     * Восстановить последний список КПП.
     */
    private fun loadSavedCheckpoints():
        List<Checkpoint> {

        val prefs =
            getSharedPreferences(
                PREFS_NAME,
                MODE_PRIVATE
            )

        val idsText =
            prefs.getString(
                PREF_CHECKPOINT_IDS,
                null
            )
                ?: return emptyList()

        val namesText =
            prefs.getString(
                PREF_CHECKPOINT_NAMES,
                null
            )
                ?: return emptyList()

        val ids =
            idsText.split(
                SEPARATOR
            )

        val names =
            namesText.split(
                SEPARATOR
            )

        if (
            ids.size != names.size
        ) {

            return emptyList()
        }

        return ids.indices.mapNotNull {
            index ->

            val id =
                ids[index].trim()

            val name =
                names[index].trim()

            if (
                id.isBlank() ||
                name.isBlank()
            ) {

                null

            } else {

                Checkpoint(
                    id = id,
                    name = name
                )
            }
        }
    }

    private fun clearSavedCheckpoints() {

        getSharedPreferences(
            PREFS_NAME,
            MODE_PRIVATE
        )
            .edit()
            .clear()
           
