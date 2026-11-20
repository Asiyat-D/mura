package kz.mura.app

import android.app.Application
import kz.mura.data.AppContainer

class MuraApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        java.io.File(cacheDir, "shares").listFiles()?.filter { it.lastModified() < System.currentTimeMillis() - 3_600_000 }?.forEach { it.delete() }
    }
    val container by lazy { AppContainer(this) }
}
