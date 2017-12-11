package pm.gnosis.heimdall

import android.content.Context
import android.support.multidex.MultiDexApplication
import com.crashlytics.android.Crashlytics
import io.fabric.sdk.android.Fabric
import io.reactivex.plugins.RxJavaPlugins
import org.spongycastle.jce.provider.BouncyCastleProvider
import pm.gnosis.crypto.LinuxSecureRandom
import pm.gnosis.heimdall.common.di.components.ApplicationComponent
import pm.gnosis.heimdall.common.di.components.DaggerApplicationComponent
import pm.gnosis.heimdall.common.di.modules.CoreModule
import timber.log.Timber
import timber.log.Timber.DebugTree
import java.security.Security

class HeimdallApplication : MultiDexApplication() {
    val component: ApplicationComponent = DaggerApplicationComponent.builder()
            .coreModule(CoreModule(this))
            .build()

    override fun onCreate() {
        super.onCreate()

        // Init crash tracker to track unhandled exceptions
        component.crashTracker().init()
        Fabric.with(this, Crashlytics())
        RxJavaPlugins.setErrorHandler(Timber::e)

        try {
            LinuxSecureRandom()
        } catch (e: Exception) {
            Timber.e("Could not register LinuxSecureRandom. Using default SecureRandom.")
        }
        Security.insertProviderAt(BouncyCastleProvider(), 1)
    }

    companion object {
        operator fun get(context: Context): HeimdallApplication {
            return context.applicationContext as HeimdallApplication
        }
    }
}
