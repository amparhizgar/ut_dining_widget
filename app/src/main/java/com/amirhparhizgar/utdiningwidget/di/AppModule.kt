package com.amirhparhizgar.utdiningwidget.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.preferencesDataStoreFile
import com.amirhparhizgar.utdiningwidget.data.ReserveDao
import com.amirhparhizgar.utdiningwidget.data.ReserveDatabase
import com.amirhparhizgar.utdiningwidget.data.getDBInstance
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Created by AmirHossein Parhizgar on 12/8/2022.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Singleton
    @Provides
    fun provideDb(@ApplicationContext context: Context): ReserveDao = getDBInstance(context).dao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create {
            context.preferencesDataStoreFile("settings")
        }

}