package com.example

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.service.NetraWorkScheduler
import com.example.service.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class NetraApplication : Application(), Configuration.Provider {

  @Inject lateinit var workerFactory: HiltWorkerFactory
  @Inject lateinit var notificationHelper: NotificationHelper

  override val workManagerConfiguration: Configuration
    get() =
      Configuration.Builder()
        .setWorkerFactory(workerFactory)
        .build()

  override fun onCreate() {
    super.onCreate()
    notificationHelper.createChannels()
    NetraWorkScheduler.schedulePeriodicMaintenance(this)
  }
}
