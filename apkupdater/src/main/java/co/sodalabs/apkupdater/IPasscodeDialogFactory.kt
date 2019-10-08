package co.sodalabs.apkupdater

import io.reactivex.Single

interface IPasscodeDialogFactory {
    fun showPasscodeDialog(): Single<Boolean>
}