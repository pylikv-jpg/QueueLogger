package com.pylikv.queuelogger

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?
    ) {

        super.onCreate(
            savedInstanceState
        )

        setContent {
            QueueLoggerApp()
        }
    }
}

/**
 * Все пункты пропуска,
 * которые QueueLogger будет контролировать.
 *
 * UUID взяты из рабочей версии QueueWatch.
 */
private val ALL_CHECKPOINTS =
    listOf(

        Checkpoint(
            id =
                "53d94097-2b34-11ec-8467-ac1f6bf889c0",
            name =
                "Бенякони"
        ),

        Checkpoint(
            id =
                "7e46a2d1-ab2f-11ec-bafb-ac1f6bf889c1",
            name =
                "Берестовица"
        ),

        Checkpoint(
            id =
                "a9173a85-3fc0-424c-84f0-defa632481e4",
            name =
                "Брест"
        ),

        Checkpoint(
            id =
                "3b797d4d-706a-440f-a1a4-826c191e1e36",
            name =
                "Брузги"
        ),

        Checkpoint(
            id =
                "ffe81c11-00d6-11e8-a967-b0dd44bde851",
            name =
                "Григоровщина"
        ),

        Checkpoint(
            id =
                "b60677d4-8a00-4f93-a781-e129e1692a03",
            name =
                "Каменный Лог"
        ),

        Checkpoint(
            id =
                "98b5be92-d3a5-4ba2-9106-76eb4eb3df49",
            name =
                "Козловичи"
        )
    )

@Composable
fun QueueLoggerApp() {

    MaterialTheme {

        Surface(
            modifier =
                Modifier.fillMaxSize()
        ) {

            MainScreen()
        }
    }
}

@Composable
fun MainScreen() {

    val context =
        LocalContext.current

    var collectionRunning by remember {

        mutableStateOf(
            isCollectionConfigured(
                context
            )
        )
    }

    var statusText by remember {

        mutableStateOf(
            if (collectionRunning) {

                "Состояние: сбор данных активен"

            } else {

                "Состояние: сбор данных не запущен"
            }
        )
    }

    /**
     * После ответа пользователя
     * на запрос разрешения уведомлений
     * всё равно запускаем сервис.
     *
     * Разрешение влияет на отображение
     * обычных уведомлений, но пользователь
     * сам решает, выдавать его или нет.
     */
    val notificationPermissionLauncher =
        rememberLauncherForActivityResult(
            contract =
                ActivityResultContracts
                    .RequestPermission()
        ) {

            startQueueLoggerService(
                context
            )

            collectionRunning =
                true

            statusText =
                "Состояние: сбор данных активен"
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(
                    24.dp
                ),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text =
                "QueueLogger",

            fontSize =
                32.sp,

            fontWeight =
                FontWeight.Bold
        )

        Spacer(
            modifier =
                Modifier.height(
                    12.dp
                )
        )

        Text(
            text =
                "Сбор статистики электронной очереди",

            fontSize =
                18.sp,

            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(
                    20.dp
                )
        )

        Text(
            text =
                "Контролируется 7 пунктов пропуска",

            fontSize =
                15.sp
        )

        Spacer(
            modifier =
                Modifier.height(
                    6.dp
                )
        )

        Text(
            text =
                "Бенякони · Берестовица · Брест · Брузги · Григоровщина · Каменный Лог · Козловичи",

            fontSize =
                13.sp,

            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(
                    32.dp
                )
        )

        Text(
            text =
                statusText,

            fontSize =
                16.sp,

            fontWeight =
                FontWeight.SemiBold,

            textAlign =
                TextAlign.Center
        )

        Spacer(
            modifier =
                Modifier.height(
                    24.dp
                )
        )

        if (!collectionRunning) {

            Button(
                onClick = {

                    if (
                        Build.VERSION.SDK_INT >=
                        Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission
                                .POST_NOTIFICATIONS
                        ) !=
                        PackageManager
                            .PERMISSION_GRANTED
                    ) {

                        notificationPermissionLauncher
                            .launch(
                                Manifest.permission
                                    .POST_NOTIFICATIONS
                            )

                    } else {

                        startQueueLoggerService(
                            context
                        )

                        collectionRunning =
                            true

                        statusText =
                            "Состояние: сбор данных активен"
                    }
                },

                modifier =
                    Modifier.fillMaxWidth()
            ) {

                Text(
                    text =
                        "Начать сбор данных"
                )
            }

        } else {

            Button(
                onClick = {

                    stopQueueLoggerService(
                        context
                    )

                    collectionRunning =
                        false

                    statusText =
                        "Состояние: сбор данных остановлен"
                },

                modifier =
                    Modifier.fillMaxWidth(),

                colors =
                    ButtonDefaults.buttonColors(
                        containerColor =
                            Color(
                                0xFFB3261E
                            )
                    )
            ) {

                Text(
                    text =
                        "Остановить сбор данных"
                )
            }
        }

        Spacer(
            modifier =
                Modifier.height(
                    18.dp
                )
        )

        Text(
            text =
                if (collectionRunning) {

                    "Приложение можно закрыть. Сбор продолжится в фоновом режиме."

                } else {

                    "После запуска QueueLogger будет автоматически опрашивать все пункты пропуска."
                },

            fontSize =
                14.sp,

            textAlign =
                TextAlign.Center
        )
    }
}

/**
 * Запустить foreground service
 * и передать ему все семь КПП.
 */
private fun startQueueLoggerService(
    context: Context
) {

    val ids =
        ArrayList(
            ALL_CHECKPOINTS.map {
                it.id
            }
        )

    val names =
        ArrayList(
            ALL_CHECKPOINTS.map {
                it.name
            }
        )

    val intent =
        Intent(
            context,
            QueueLoggerService::class.java
        ).apply {

            action =
                QueueLoggerService.ACTION_START

            putStringArrayListExtra(
                QueueLoggerService
                    .EXTRA_CHECKPOINT_IDS,
                ids
            )

            putStringArrayListExtra(
                QueueLoggerService
                    .EXTRA_CHECKPOINT_NAMES,
                names
            )
        }

    ContextCompat.startForegroundService(
        context,
        intent
    )
}

/**
 * Ручная остановка сервиса.
 */
private fun stopQueueLoggerService(
    context: Context
) {

    val intent =
        Intent(
            context,
            QueueLoggerService::class.java
        ).apply {

            action =
                QueueLoggerService.ACTION_STOP
        }

    context.startService(
        intent
    )
}

/**
 * Проверяем, сохранён ли список КПП
 * работающего/восстанавливаемого сервиса.
 */
private fun isCollectionConfigured(
    context: Context
): Boolean {

    val prefs =
        context.getSharedPreferences(
            "queue_logger_service",
            Context.MODE_PRIVATE
        )

    return !prefs
        .getString(
            "checkpoint_ids",
            null
        )
        .isNullOrBlank()
}
