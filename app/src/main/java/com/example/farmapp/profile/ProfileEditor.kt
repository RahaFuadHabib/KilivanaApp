package com.example.farmapp.profile

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.testTag
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.BuildConfig
import com.example.farmapp.data.model.canonical.*
import com.example.farmapp.data.remote.ApiClient
import com.example.farmapp.network.*
import com.example.farmapp.network.canonical.*
import com.example.farmapp.supplier.SupplierProfileDto
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import retrofit2.Response
import retrofit2.http.*

data class AccountEditRequest(val name: String, val email: String, val phone: String, val region: String?) {
    override fun toString() = "AccountEditRequest(redacted)"
}
data class BusinessEditRequest(val businessName: String, val businessDetails: String?, val location: String,
    val verificationInfo: String?) {
    override fun toString() = "BusinessEditRequest(redacted)"
}
interface ProfileEditorApi {
    @GET("auth/me") suspend fun me(@Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalUserDto>>
    @PUT("users/me") suspend fun save(@Body request: AccountEditRequest, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<CanonicalUserDto>>
    @GET("profiles/suppliers/{id}") suspend fun business(@Path("id") id: Long, @Tag session: SessionSnapshot): Response<CanonicalApiResponse<SupplierProfileDto>>
    @PUT("profiles/suppliers/{id}") suspend fun saveBusiness(@Path("id") id: Long, @Body request: BusinessEditRequest,
        @Tag session: SessionSnapshot): Response<CanonicalApiResponse<SupplierProfileDto>>
}

internal fun validateAccountEdit(name: String, phone: String): String? = when {
    name.trim().isEmpty() -> "Enter your full name."
    !Regex("\\+?[0-9]{7,15}").matches(phone.trim()) -> "Enter a valid phone number, for example +254712345678."
    else -> null
}

internal data class ProfileEditState(val account: CanonicalAccount? = null, val business: SupplierProfileDto? = null,
    val busy: Boolean = false, val error: String? = null, val saved: Int = 0)

internal class ProfileEditorModel : ViewModel() {
    private val mutable = MutableStateFlow(ProfileEditState())
    val state = mutable.asStateFlow()
    private val expected = TokenManager.session.value
    init { load() }
    private fun current(): SessionSnapshot {
        val now = TokenManager.session.value
        check(now.generation == expected.generation && !now.loggingOut && now.auth?.userId == expected.auth?.userId &&
            now.auth?.role == expected.auth?.role && now.auth?.backendContract == "canonical") { "Sign in again to edit your profile." }
        return now
    }
    private fun <T> read(response: Response<CanonicalApiResponse<T>>): T {
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            error(when (response.code()) {
                409 -> "These details conflict with an existing account."
                400, 422 -> "Check your profile details."
                401, 403 -> "Your session cannot authorize this change. Sign in again."
                else -> "Profile request failed. Reload your profile before trying again."
            })
        }
        return response.body()?.requireData() ?: error("Incomplete profile response.")
    }
    private fun launch(action: suspend () -> Unit) {
        if (mutable.value.busy) return
        mutable.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try { action(); current() }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutable.update { it.copy(error = if (e is IllegalStateException) e.message else "Profile request failed. Reload before retrying; no automatic submission was made.") } }
            finally { mutable.update { it.copy(busy = false) } }
        }
    }
    fun load() = launch {
        val account = read(ApiClient.profileEditorApi.me(current())).toAccount()
        check(account.userId == current().auth?.userId && account.role == current().auth?.role)
        mutable.update { it.copy(account = account) }
        if (account.role == "SUPPLIER") {
            val response = ApiClient.profileEditorApi.business(account.userId.toLong(), current())
            val business = if (response.code() == 404) { response.errorBody()?.close(); null } else read(response)
            check(business == null || business.userId.toString() == current().auth?.userId)
            mutable.update { it.copy(business = business) }
        }
    }
    fun save(name: String, phone: String, region: String) = launch {
        validateAccountEdit(name, phone)?.let { error(it) }
        val account = mutable.value.account ?: error("Reload your profile first.")
        val county = if (region.isBlank() && account.region.isNullOrBlank()) null else
            KenyanCounties.normalize(region) ?: error("Choose one of the 47 Kenyan counties for your region.")
        val saved = read(ApiClient.profileEditorApi.save(AccountEditRequest(name.trim(), account.email, phone.trim(), county), current())).toAccount()
        val now = current()
        check(saved.userId == now.auth?.userId && saved.role == now.auth?.role)
        // Keep the newest token pair if a read refreshed the session while editing.
        check(TokenManager.rotate(now, now.auth!!.copy(fullName = saved.fullName, phoneNumber = saved.phoneNumber,
            email = saved.email, region = saved.region, username = saved.username)) != null)
        mutable.update { it.copy(account = saved, saved = it.saved + 1) }
    }
    fun saveBusiness(name: String, details: String, location: String) = launch {
        check(name.isNotBlank() && location.isNotBlank()) { "Enter a business name and physical location." }
        val original = mutable.value.business ?: error("No business profile is available to edit.")
        val id = current().auth!!.userId.toLong()
        val latest = read(ApiClient.profileEditorApi.business(id, current()))
        check(latest.id == original.id && latest.userId == id)
        val saved = read(ApiClient.profileEditorApi.saveBusiness(id,
            BusinessEditRequest(name.trim(), details.trim(), location.trim(), latest.verificationInfo), current()))
        check(saved.userId == id && saved.id == original.id)
        current()
        mutable.update { it.copy(business = saved, saved = it.saved + 1) }
    }
}

@Composable
internal fun ProfileEditor(onClose: () -> Unit) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    val model: ProfileEditorModel = viewModel(key = "profile-editor-${session.generation}")
    val state by model.state.collectAsStateWithLifecycle()
    LaunchedEffect(model) { model.load() }
    ProfileEditorContent(state, onClose, model::load, model::save, model::saveBusiness)
}

@Composable internal fun ProfileEditorContent(state: ProfileEditState, onClose: () -> Unit,
    reload: () -> Unit, saveAccount: (String, String, String) -> Unit, saveBusiness: (String, String, String) -> Unit) {
    var businessMode by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var region by remember { mutableStateOf("") }
    var businessName by remember { mutableStateOf("") }
    var details by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var edited by remember { mutableStateOf(false) }
    LaunchedEffect(state.account, state.business, state.saved) {
        if (!edited || state.saved > 0) {
            name = state.account?.fullName.orEmpty(); phone = state.account?.phoneNumber.orEmpty(); region = state.account?.region.orEmpty()
            businessName = state.business?.businessName.orEmpty(); details = state.business?.businessDetails.orEmpty(); location = state.business?.location.orEmpty()
        }
    }
    AlertDialog(onDismissRequest = { if (!state.busy) onClose() }, title = { Text("Edit profile") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (state.account?.role == "SUPPLIER") Row {
                TextButton(onClick = { businessMode = false }, enabled = !state.busy) { Text("Account") }
                TextButton(onClick = { businessMode = true }, enabled = !state.busy && state.business != null) { Text("Business") }
            }
            if (businessMode) {
                OutlinedTextField(businessName, { businessName = it; edited = true }, label = { Text("Business name") }, enabled = !state.busy)
                OutlinedTextField(details, { details = it; edited = true }, label = { Text("Business details") }, enabled = !state.busy)
                OutlinedTextField(location, { location = it; edited = true }, label = { Text("Physical location") }, enabled = !state.busy)
                SupplierProfilePhotos()
            } else {
                OutlinedTextField(name, { name = it; edited = true }, label = { Text("Full name / contact person") }, enabled = !state.busy,
                    isError = edited && name.isBlank(), supportingText = { if (edited && name.isBlank()) Text("Enter your full name.") })
                OutlinedTextField(phone, { phone = it; edited = true }, label = { Text("Phone number") }, enabled = !state.busy,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                    isError = edited && !Regex("\\+?[0-9]{7,15}").matches(phone.trim()),
                    supportingText = { Text("For example +254712345678") })
                OutlinedTextField(region, { region = it; edited = true }, label = { Text("Region / county") }, enabled = !state.busy,
                    isError = region.isNotBlank() && KenyanCounties.normalize(region) == null,
                    supportingText = { Text("Use one of Kenya's 47 county names.") })
                Text("Email: ${state.account?.email.orEmpty()}")
                Text("Username: ${state.account?.username ?: "Not set"}. Username changes are not supported by the API.", style = MaterialTheme.typography.bodySmall)
            }
            if (state.busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.error?.let { Text(it, color = MaterialTheme.colorScheme.error); TextButton(onClick = { edited = false; reload() }, enabled = !state.busy) { Text("Reload profile") } }
            if (state.saved > 0) Text("Profile saved.", color = MaterialTheme.colorScheme.primary)
        }
    }, confirmButton = { TextButton(onClick = {
        if (businessMode) saveBusiness(businessName, details, location) else saveAccount(name, phone, region)
    }, enabled = !state.busy && state.account != null &&
        (if (businessMode) businessName.isNotBlank() && location.isNotBlank() else validateAccountEdit(name, phone) == null)) { Text("Save") } },
        dismissButton = { TextButton(onClick = onClose, enabled = !state.busy) { Text(if (state.saved > 0) "Done" else "Cancel") } })
}

@Composable
internal fun AboutKilivana() {
    var open by remember { mutableStateOf(false) }
    Surface(onClick = { open = true }, modifier = Modifier.fillMaxWidth().testTag("profile-about"), shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.fillMaxWidth().padding(20.dp), horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Info, null, tint = MaterialTheme.colorScheme.primary)
            Text("About", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
            Icon(Icons.Default.ChevronRight, null)
        }
    }
    if (open) AlertDialog(onDismissRequest = { open = false }, title = { Text("About Kilivana") },
        text = { Text("Kilivana connects farmers, buyers, suppliers and inspectors.\nVersion ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})") },
        confirmButton = { TextButton(onClick = { open = false }) { Text("Close") } })
}

@Composable internal fun AccountProfilePage(onClose: () -> Unit) {
    val session by TokenManager.session.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    if (editing) { ProfileEditor { editing = false }; return }
    AlertDialog(onDismissRequest = onClose, title = { Text("Profile") }, text = {
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(session.auth?.fullName.orEmpty(), style = MaterialTheme.typography.titleLarge)
            Text(session.auth?.email.orEmpty())
            Text(session.auth?.phoneNumber ?: "Phone not provided")
            TextButton(onClick = { editing = true }) { Text("Edit profile") }
            AboutKilivana()
        }
    }, confirmButton = { TextButton(onClick = onClose) { Text("Close") } })
}
