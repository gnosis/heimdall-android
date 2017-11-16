package pm.gnosis.heimdall.ui.base

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.Toolbar
import android.view.WindowManager
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.disposables.CompositeDisposable
import io.reactivex.rxkotlin.plusAssign
import pm.gnosis.heimdall.HeimdallApplication
import pm.gnosis.heimdall.security.EncryptionManager
import pm.gnosis.heimdall.ui.security.SecurityActivity
import timber.log.Timber
import javax.inject.Inject

abstract class BaseActivity : AppCompatActivity() {

    @Inject
    lateinit var encryptionManager: EncryptionManager

    protected val disposables = CompositeDisposable()

    private var performSecurityCheck = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        HeimdallApplication[this].component.inject(this)
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
    }

    override fun onStart() {
        super.onStart()
        if (performSecurityCheck) {
            disposables += encryptionManager.unlocked()
                    // We block the ui thread here to avoid exposing the ui before the app is unlocked
                    .subscribeOn(AndroidSchedulers.mainThread())
                    .observeOn(AndroidSchedulers.mainThread())
                    .subscribe(this::checkSecurity, this::handleCheckError)
        }
    }

    override fun onStop() {
        super.onStop()
        disposables.clear()
    }

    protected fun registerToolbar(toolbar: Toolbar) {
        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayShowTitleEnabled(false)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeButtonEnabled(true)
    }

    protected fun skipSecurityCheck() {
        performSecurityCheck = false
    }

    private fun checkSecurity(unlocked: Boolean) {
        if (!unlocked) {
            startActivity(SecurityActivity.createIntent(this))
        }
    }

    private fun handleCheckError(throwable: Throwable) {
        Timber.d(throwable)
        // Show blocker screen. No auth -> no app usage
    }
}
