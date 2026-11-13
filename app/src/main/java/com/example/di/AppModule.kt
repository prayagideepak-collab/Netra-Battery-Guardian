package com.example.di

import android.content.Context
import com.example.data.local.db.BatteryDatabase
import com.example.data.local.db.BatterySnapshotDao
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
  fun provideBatteryDatabase(@ApplicationContext context: Context): BatteryDatabase =
    BatteryDatabase.getInstance(context)

  @Provides
  @Singleton
  fun provideBatterySnapshotDao(database: BatteryDatabase): BatterySnapshotDao =
    database.batterySnapshotDao()

  @Provides
  @Singleton
  fun provideNetraDao(database: BatteryDatabase): NetraDao = database.netraDao()
}
