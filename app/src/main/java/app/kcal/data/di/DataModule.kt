package app.kcal.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import app.kcal.core.common.AppLocaleProvider
import app.kcal.core.common.DispatcherProvider
import app.kcal.core.common.interfaceLocale
import app.kcal.data.db.DailyTargetSnapshotDao
import app.kcal.data.db.KcalDatabase
import app.kcal.data.db.MealEntryDao
import app.kcal.data.db.WeightEntryDao
import app.kcal.data.repository.DailyTargetRepositoryImpl
import app.kcal.data.repository.MealRepositoryImpl
import app.kcal.data.repository.ProfileRepositoryImpl
import app.kcal.domain.repository.DailyTargetRepository
import app.kcal.domain.repository.MealRepository
import app.kcal.domain.repository.ProfileRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import java.time.ZoneId
import java.util.Locale
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KcalDatabase =
        Room.databaseBuilder(context, KcalDatabase::class.java, KcalDatabase.NAME).build()

    @Provides
    fun provideMealEntryDao(database: KcalDatabase): MealEntryDao = database.mealEntryDao()

    @Provides
    fun provideWeightEntryDao(database: KcalDatabase): WeightEntryDao = database.weightEntryDao()

    @Provides
    fun provideDailyTargetSnapshotDao(database: KcalDatabase): DailyTargetSnapshotDao =
        database.dailyTargetSnapshotDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(
            produceFile = { context.preferencesDataStoreFile(PREFERENCES_NAME) },
        )

    private const val PREFERENCES_NAME = "kcal_preferences"

    @Provides
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    fun provideZoneId(): ZoneId = ZoneId.systemDefault()

    @Provides
    fun provideAppLocaleProvider(): AppLocaleProvider = AppLocaleProvider { interfaceLocale(Locale.getDefault()) }

    @Provides
    fun provideDispatcherProvider(): DispatcherProvider = DispatcherProvider()
}

@Module
@InstallIn(SingletonComponent::class)
interface RepositoryModule {

    @Binds
    fun bindProfileRepository(impl: ProfileRepositoryImpl): ProfileRepository

    @Binds
    fun bindDailyTargetRepository(impl: DailyTargetRepositoryImpl): DailyTargetRepository

    @Binds
    fun bindMealRepository(impl: MealRepositoryImpl): MealRepository
}
