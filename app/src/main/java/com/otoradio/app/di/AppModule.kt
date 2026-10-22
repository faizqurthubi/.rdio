package com.otoradio.app.di

import android.content.Context
import androidx.room.Room
import com.otoradio.app.data.local.RadioDao
import com.otoradio.app.data.local.RadioDatabase
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
    fun provideDatabase(@ApplicationContext context: Context): RadioDatabase =
        Room.databaseBuilder(context, RadioDatabase::class.java, "oto_radio.db")
            .addMigrations(RadioDatabase.MIGRATION_1_2)
            .build()

    @Provides
    fun provideDao(db: RadioDatabase): RadioDao = db.dao()
}
