package com.codexmeter.app

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class RefreshWorker(appContext: Context, params: WorkerParameters): CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val storage=Storage(applicationContext)
        if(storage.bridgeUrl.isBlank()) return Result.success()
        return BridgeClient(applicationContext).fetch().fold(
            onSuccess={ UsageWidgetProvider.updateAll(applicationContext); Result.success() },
            onFailure={ Result.retry() }
        )
    }
    companion object {
        fun schedule(context: Context) {
            val req=PeriodicWorkRequestBuilder<RefreshWorker>(15, TimeUnit.MINUTES).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("codexmeter_refresh", ExistingPeriodicWorkPolicy.UPDATE, req)
        }
    }
}
class UsageWidgetProvider: AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) { ids.forEach { update(context, manager, it) } }
    companion object {
        fun updateAll(context: Context) {
            val m=AppWidgetManager.getInstance(context)
            val c=ComponentName(context, UsageWidgetProvider::class.java)
            m.getAppWidgetIds(c).forEach { update(context,m,it) }
        }
        private fun update(context: Context, manager: AppWidgetManager, id: Int) {
            val views=android.widget.RemoteViews(context.packageName, R.layout.widget_usage)
            val s=Storage(context).latest()
            val launch=PendingIntent.getActivity(context,0,Intent(context,MainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            views.setOnClickPendingIntent(R.id.widget_title,launch)
            if(s==null) {
                views.setTextViewText(R.id.widget_limits,"5H  --%   ·   7D  --%")
                views.setTextViewText(R.id.widget_tokens,"Open CodexMeter to connect Bridge")
            } else {
                views.setTextViewText(R.id.widget_limits,"5H  ${s.fiveHourRemaining.toInt()}%   ·   7D  ${s.weeklyRemaining.toInt()}%")
                views.setTextViewText(R.id.widget_tokens,"Today ${formatTokens(s.tokensToday)}  ·  reset ${resetText(s.fiveHourResetAt)}")
            }
            manager.updateAppWidget(id,views)
        }
    }
}
class BootReceiver: android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) { RefreshWorker.schedule(context) }
}
