@file:Suppress("unused")

package co.sodalabs.updaterengine.di.modules

import android.content.Context
import androidx.room.Room
import co.sodalabs.updaterengine.data.InstallHistoryDatabase
import dagger.Module
import dagger.Provides

@Deprecated("This is an example for Room")
@Module
class InstallHistoryDatabaseModule {

    @Provides
    fun provideHistoryDatabase(
        applicationContext: Context
    ): InstallHistoryDatabase {
        val safeApplicationContext = applicationContext.applicationContext
        return Room
            .databaseBuilder(safeApplicationContext, InstallHistoryDatabase::class.java, "updater.db")
            .fallbackToDestructiveMigration()
            // .addMigrations(migration1To2)
            .build()
    }

    // private val migration1To2 = object : Migration(1, 2) {
    //     override fun migrate(
    //         database: SupportSQLiteDatabase
    //     ) {
    //         database.delete()
    //     }
    // }
}