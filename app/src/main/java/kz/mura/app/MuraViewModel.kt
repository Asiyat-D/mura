package kz.mura.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kz.mura.domain.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kz.mura.data.NetworkRepository

/** UI state lives only in memory; configuration changes retain the ViewModel. */
data class ChatDraft(val text: String = "", val incoming: Boolean = false)
data class ConverterState(val input: String = "", val output: String = "", val password: String = "", val mode: ConversionMode = ConversionMode.ENCODE, val busy: Boolean = false, val failure: Failure? = null)
class MuraViewModel(val repository: MessageRepository, private val settings: PreferencesRepository, val network: NetworkRepository) : ViewModel() {
    val preferences = MutableStateFlow(Preferences())
    val preferencesReady = MutableStateFlow(false)
    val storageFailure = MutableStateFlow(false)
    val entries = repository.entries.catch { storageFailure.value = true }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val conversations = repository.conversations.catch { storageFailure.value = true }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val messages = repository.messages.catch { storageFailure.value = true }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val converter = MutableStateFlow(ConverterState())
    val chatDrafts = MutableStateFlow<Map<Long, ChatDraft>>(emptyMap())
    val networkError = MutableStateFlow<String?>(null)
    private val networkActive = MutableStateFlow(false)
    fun setNetworkActive(value: Boolean) { networkActive.value = value }
    fun networkAction(block: suspend () -> Unit) { viewModelScope.launch {
        try { block(); networkError.value = null }
        catch(e: CancellationException) { throw e }
        catch(e: Exception) { networkError.value = e.message ?: "invalid_contact" }
    } }
    suspend fun sendMessage(id: Long, text: String) {
        if(conversations.value.firstOrNull { it.id == id }?.peerCode != null) network.send(id,text) else repository.saveMessage(id,text,false)
    }
    fun updateChatDraft(id: Long, text: String, incoming: Boolean) { chatDrafts.update { it + (id to ChatDraft(text.take(1_048_576), incoming)) } }
    private var conversionJob: Job? = null
    suspend fun eraseAll() {
        conversionJob?.cancelAndJoin()
        network.eraseAll { repository.eraseAll() }
        converter.value = ConverterState()
        chatDrafts.value = emptyMap()
    }
    private val codec = DecimalCodec()
    private val cipher = PasswordCipher()
    init {
        viewModelScope.launch { safely { network.initialize() } }
        viewModelScope.launch { networkActive.collectLatest { active ->
            if(active) while(true) { safely { network.sync() }; delay(4_000) }
        } }
        viewModelScope.launch { settings.preferences.catch { storageFailure.value = true }.collect { preferences.value = it; preferencesReady.value = true } }
        viewModelScope.launch { settings.preferences.catch { storageFailure.value = true }.collectLatest { p ->
            while (true) { if (p.retentionDays > 0) safely { repository.prune(System.currentTimeMillis() - p.retentionDays * 86_400_000L) }; delay(60_000) }
        } }
    }
    fun setInput(value: String) { if (!converter.value.busy) converter.update { it.copy(input = value.take(1_048_576), output = "", failure = null) } }
    fun setPassword(value: String) { converter.update { it.copy(password = value, failure = null) } }
    fun setMode(mode: ConversionMode) { if (!converter.value.busy) converter.update { it.copy(mode = mode, output = "", password = "", failure = null) } }
    fun clear() { if (!converter.value.busy) converter.value = ConverterState(mode = converter.value.mode) }
    fun importCode(code: String) { converter.value = ConverterState(input = code.take(1_048_576), mode = if (code.startsWith(PasswordCipher.PREFIX)) ConversionMode.DECRYPT else ConversionMode.DECODE) }
    fun restore(entry: SavedEntry) { converter.value = ConverterState(input = entry.source, output = entry.result, mode = entry.mode) }
    fun convert() {
        val state = converter.value
        if (state.busy) return
        converter.update { it.copy(busy = true, output = "", failure = null) }
        conversionJob = viewModelScope.launch {
            val pass = state.password.toCharArray()
            try {
                val output = withContext(Dispatchers.Default) { when (state.mode) {
                    ConversionMode.ENCODE -> codec.encode(state.input)
                    ConversionMode.DECODE -> codec.decode(state.input)
                    ConversionMode.ENCRYPT -> cipher.encrypt(state.input, pass)
                    ConversionMode.DECRYPT -> cipher.decrypt(state.input, pass)
                } }
                repository.saveEntry(state.mode, state.input, output)
                converter.update { it.copy(output = output, password = "", busy = false) }
            } catch (e: MuraException) { converter.update { it.copy(busy = false, failure = e.reason) } }
            catch (e: CancellationException) { throw e }
            catch (_: Exception) { converter.update { it.copy(busy = false, failure = Failure.STORAGE) } }
            finally { pass.fill('\u0000') }
        }
    }
    fun updatePreferences(value: Preferences) { launch { settings.update(value) } }
    fun launch(block: suspend () -> Unit) { viewModelScope.launch { safely(block) } }
    suspend fun safely(block: suspend () -> Unit) { try { block() } catch (e: CancellationException) { throw e } catch (_: Exception) { storageFailure.value = true } }
}
