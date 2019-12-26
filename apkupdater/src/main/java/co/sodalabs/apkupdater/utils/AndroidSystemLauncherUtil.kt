package co.sodalabs.apkupdater.utils

import Packages.SPARKPOINT_PACKAGE_NAME
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import co.sodalabs.apkupdater.ISystemLauncherUtil
import co.sodalabs.updaterengine.extension.ensureBackgroundThread
import timber.log.Timber
import javax.inject.Inject

class AndroidSystemLauncherUtil @Inject constructor(
    private val context: Context
) : ISystemLauncherUtil {

    private val packageManager: PackageManager by lazy { context.packageManager }
    private val getHomeActivitiesMethod by lazy {
        packageManager::class.java.getDeclaredMethod(
            "getHomeActivities",
            List::class.java
        )
    }
    private val replacePreferredActivityMethod by lazy {
        packageManager::class.java.getDeclaredMethod(
            "replacePreferredActivity",
            IntentFilter::class.java,
            Int::class.java,
            Array<ComponentName>::class.java,
            ComponentName::class.java
        )
    }

    private val baseHomeIntent = Intent(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
        // Note: Don't add flags cause we want this HOME intent to be very PURE.
    }
    private val baseHomeIntentFilter = IntentFilter(Intent.ACTION_MAIN).apply {
        addCategory(Intent.CATEGORY_HOME)
        addCategory(Intent.CATEGORY_DEFAULT)
    }

    override fun startSystemLauncherWithSelector() {
        ensureBackgroundThread()

        try {
            Timber.v("[LauncherUtil] Start the system launcher via HOME Intent.")

            // Unset the default HOME preference.
            val homeActivities = buildHomeActivityList()
            homeActivities.forEach {
                packageManager.clearPackagePreferredActivities(it.activityInfo.packageName)
            }

            // Some install fails and therefore cannot restart the app.
            // See https://app.clubhouse.io/soda/story/869/updater-crashes-when-the-sparkpoint-player-updates
            val homeIntent = Intent(baseHomeIntent).apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
            }
            context.startActivity(homeIntent)
        } catch (error: Throwable) {
            Timber.w(error)
        }
    }

    override fun startSodaLabsLauncherIfInstalled() {
        ensureBackgroundThread()

        val launchIntentOpt: Intent? = packageManager.getLaunchIntentForPackage(SPARKPOINT_PACKAGE_NAME)
        launchIntentOpt?.let { launchIntent ->
            Timber.v("[LauncherUtil] Start the '$SPARKPOINT_PACKAGE_NAME' via package name.")

            context.startActivity(launchIntent.apply {
                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } ?: kotlin.run {
            Timber.w("[LauncherUtil] Couldn't find SodaLabs launcher, so do nothing")
        }
    }

    override fun setSodaLabsLauncherAsDefaultIfInstalled() {
        ensureBackgroundThread()

        try {
            Timber.v("[LauncherUtil] Set '$SPARKPOINT_PACKAGE_NAME' as the preferred HOME.")

            val launchIntentOpt: Intent? = packageManager.getLaunchIntentForPackage(SPARKPOINT_PACKAGE_NAME)
            launchIntentOpt?.let { launchIntent ->
                val componentOpt: ComponentName? = launchIntent.component
                componentOpt?.let { component ->
                    setSodaLabsLauncherAsDefault(component)
                }
            }
        } catch (error: Throwable) {
            Timber.w(error)
        }
    }

    private fun setSodaLabsLauncherAsDefault(
        activityComponent: ComponentName
    ) {
        replacePreferredActivityMethod(
            packageManager,
            baseHomeIntentFilter,
            IntentFilter.MATCH_CATEGORY_EMPTY,
            buildHomeComponentSet(),
            activityComponent)
    }

    /**
     * Collect the HOME activities but map the information to [ComponentName].
     */
    private fun buildHomeComponentSet(): Array<ComponentName> {
        val homeActivities = buildHomeActivityList()
        return homeActivities
            .map { info ->
                ComponentName(info.activityInfo.packageName, info.activityInfo.name)
            }
            .toTypedArray()
    }

    /**
     * Collect the HOME activities.
     */
    private fun buildHomeActivityList(): List<ResolveInfo> {
        val resolveInfoList = mutableListOf<ResolveInfo>()
        getHomeActivitiesMethod.invoke(packageManager, resolveInfoList)
        return resolveInfoList
    }

    private fun getCurrentDefaultSystemLauncherPackageName(): String {
        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_HOME)
        }
        val resolveInfo = packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        return resolveInfo.activityInfo.packageName
    }
}