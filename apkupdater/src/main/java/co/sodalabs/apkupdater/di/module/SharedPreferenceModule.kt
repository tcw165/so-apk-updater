@file:Suppress("unused")

package co.sodalabs.apkupdater.di.module

import android.content.Context
import co.sodalabs.apkupdater.di.ApplicationScope
import dagger.Module
import dagger.Provides

@Module
class SharedPreferenceModule(
    private val context: Context
) {

    @Provides
    @ApplicationScope
    fun getApplicationContext(): Context = context
}