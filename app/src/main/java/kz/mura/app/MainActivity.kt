package kz.mura.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.graphics.luminance
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kz.mura.app.ui.*

class MainActivity : FragmentActivity() {
    private lateinit var model: MuraViewModel
    private var locked by mutableStateOf(true)
    private var authError by mutableStateOf(false)
    private var foreground by mutableStateOf(false)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        enableEdgeToEdge()
        val container = (application as MuraApplication).container
        model = ViewModelProvider(this, object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <V : ViewModel> create(modelClass: Class<V>): V = MuraViewModel(container.messages, container.settings, container.network) as V
        })[MuraViewModel::class.java]
        setContent {
            val prefs by model.preferences.collectAsStateWithLifecycle()
            val ready by model.preferencesReady.collectAsStateWithLifecycle()
            val storageFailure by model.storageFailure.collectAsStateWithLifecycle()
            MuraTheme(prefs.theme) {
                val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
                SideEffect {
                    model.setNetworkActive(ready && foreground && (!prefs.biometric || !locked))
                    val bars = if(dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT) else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT)
                    enableEdgeToEdge(statusBarStyle = bars, navigationBarStyle = bars)
                }
                CompositionLocalProvider(LocalStrings provides Strings(prefs.language)) {
                    when {
                        !ready -> if(storageFailure) StorageFailureScreen() else LoadingScreen()
                        prefs.biometric && locked -> LockScreen(authError, onUnlock = { authenticate { locked = false } })
                        else -> MuraApp(model, onEnableBiometric = { authenticate { locked = false; model.updatePreferences(model.preferences.value.copy(biometric = true)) } })
                    }
                }
            }
        }
    }
    override fun onResume() { super.onResume(); foreground = true }
    override fun onPause() { foreground = false; if(::model.isInitialized) model.setNetworkActive(false); super.onPause() }
    private fun authenticate(onSuccess: () -> Unit) {
        val allowed = BiometricManager.Authenticators.BIOMETRIC_STRONG
        if (BiometricManager.from(this).canAuthenticate(allowed) != BiometricManager.BIOMETRIC_SUCCESS) { authError = true; android.widget.Toast.makeText(this, Strings(model.preferences.value.language)[T.BiometricUnavailable], android.widget.Toast.LENGTH_LONG).show(); return }
        authError = false
        val strings = Strings(model.preferences.value.language)
        val prompt = BiometricPrompt(this, ContextCompat.getMainExecutor(this), object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) { onSuccess() }
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) { authError = true }
        })
        prompt.authenticate(BiometricPrompt.PromptInfo.Builder().setTitle(strings[T.Authenticate]).setAllowedAuthenticators(allowed).setNegativeButtonText(strings[T.Cancel]).build())
    }
    override fun onStop() {
        if (!isChangingConfigurations) { locked = true; if (::model.isInitialized) model.setPassword("") }
        super.onStop()
    }
}
