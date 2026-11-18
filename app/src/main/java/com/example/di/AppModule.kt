package com.example.di

import android.content.Context
import com.example.data.local.db.AppDatabase
import com.example.data.local.db.BatteryDao
import com.example.data.local.db.ChargingSessionDao
import com.example.data.local.db.NetraDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

  @Provides
  @Singleton
  fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
    AppDatabase.getInstance(context)

  @Provides
  @Singleton
  fun provideBatteryDao(database: AppDatabase): BatteryDao =
    database.batteryDao()

  @Provides
  @Singleton
  fun provideNetraDao(database: AppDatabase): NetraDao = database.netraDao()

  @Provides
  @Singleton
  fun provideChargingSessionDao(database: AppDatabase): ChargingSessionDao =
    database.chargingSessionDao()
}
