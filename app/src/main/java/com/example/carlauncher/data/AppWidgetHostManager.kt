package com.example.carlauncher.data

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.View

/**
 * Управление системным AppWidgetHost для отображения настоящих
 * виджетов Android (Яндекс Музыка, сторонние плееры, навигация, часы).
 */
object AppWidgetHostManager {
    private const val TAG = "AppWidgetHostMgr"
    const val HOST_ID = 2048

    const val REQUEST_BIND_APPWIDGET = 1201
    const val REQUEST_PICK_APPWIDGET = 1202
    const val REQUEST_CONFIGURE_APPWIDGET = 1203

    private var appWidgetHost: AppWidgetHost? = null
    private var appWidgetManager: AppWidgetManager? = null
    private var isListening = false

    fun init(context: Context) {
        val appCtx = context.applicationContext
        if (appWidgetManager == null) {
            appWidgetManager = AppWidgetManager.getInstance(appCtx)
        }
        if (appWidgetHost == null) {
            appWidgetHost = object : AppWidgetHost(appCtx, HOST_ID) {
                override fun onCreateView(
                    context: Context,
                    appWidgetId: Int,
                    appWidget: AppWidgetProviderInfo?
                ): AppWidgetHostView {
                    return object : AppWidgetHostView(context) {
                        init {
                            clipToOutline = true
                        }
                    }
                }
            }
        }
    }

    fun startListening() {
        if (!isListening) {
            runCatching {
                appWidgetHost?.startListening()
                isListening = true
                Log.d(TAG, "AppWidgetHost started listening")
            }.onFailure { Log.w(TAG, "startListening error: ${it.message}") }
        }
    }

    fun stopListening() {
        if (isListening) {
            runCatching {
                appWidgetHost?.stopListening()
                isListening = false
                Log.d(TAG, "AppWidgetHost stopped listening")
            }.onFailure { Log.w(TAG, "stopListening error: ${it.message}") }
        }
    }

    fun allocateAppWidgetId(): Int {
        return runCatching {
            appWidgetHost?.allocateAppWidgetId() ?: -1
        }.getOrDefault(-1)
    }

    fun deleteAppWidgetId(appWidgetId: Int) {
        if (appWidgetId <= 0) return
        runCatching {
            appWidgetHost?.deleteAppWidgetId(appWidgetId)
            Log.d(TAG, "Deleted widget id $appWidgetId")
        }.onFailure { Log.w(TAG, "deleteAppWidgetId error: ${it.message}") }
    }

    fun getAppWidgetInfo(appWidgetId: Int): AppWidgetProviderInfo? {
        if (appWidgetId <= 0) return null
        return runCatching {
            appWidgetManager?.getAppWidgetInfo(appWidgetId)
        }.getOrNull()
    }

    fun getInstalledProviders(context: Context): List<AppWidgetProviderInfo> {
        val mgr = appWidgetManager ?: AppWidgetManager.getInstance(context).also { appWidgetManager = it }
        return runCatching {
            mgr.installedProviders ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun bindAppWidgetIdIfAllowed(appWidgetId: Int, provider: ComponentName): Boolean {
        val mgr = appWidgetManager ?: return false
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                mgr.bindAppWidgetIdIfAllowed(appWidgetId, provider)
            } else {
                false
            }
        }.getOrDefault(false)
    }

    fun createView(context: Context, appWidgetId: Int, widthPx: Int = 0, heightPx: Int = 0): View? {
        val host = appWidgetHost ?: return null
        val mgr = appWidgetManager ?: return null
        val info = mgr.getAppWidgetInfo(appWidgetId) ?: return null

        return runCatching {
            val view = host.createView(context, appWidgetId, info)
            if (widthPx > 0 && heightPx > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
                val opts = Bundle().apply {
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthPx)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthPx)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightPx)
                    putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightPx)
                }
                view.updateAppWidgetSize(opts, widthPx, heightPx, widthPx, heightPx)
            }
            view
        }.getOrNull()
    }
}
