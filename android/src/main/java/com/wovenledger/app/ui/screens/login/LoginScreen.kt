package com.wovenledger.app.ui.screens.login

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.wovenledger.app.data.api.AuthApiService
import com.wovenledger.app.data.api.LoginRequest
import com.wovenledger.app.data.auth.SessionManager
import com.wovenledger.app.data.outbox.OutboxManager
import com.wovenledger.app.data.sync.SyncManager
import com.wovenledger.app.ui.components.FormScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject

/** The sign-in form. Same shape as every other form in the app, with two fields. */
data class LoginForm(
    val username: String = "",
    val password: String = "",
    val submitting: Boolean = false,
    val errors: Map<String, String> = emptyMap(),
    val message: String? = null,
    val signedIn: Boolean = false,
)

private const val UNAUTHORIZED = 401

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val api: AuthApiService,
    private val session: SessionManager,
    private val outbox: OutboxManager,
    private val sync: SyncManager,
) : ViewModel() {

    private val _form = MutableStateFlow(LoginForm())
    val form: StateFlow<LoginForm> = _form.asStateFlow()

    fun onUsernameChange(value: String) =
        _form.update { it.copy(username = value, errors = it.errors - "username", message = null) }

    fun onPasswordChange(value: String) =
        _form.update { it.copy(password = value, errors = it.errors - "password", message = null) }

    /**
     * Exchanges the password for a token, then catches the app up.
     *
     * The cold-start drain and sync were skipped while there was no token — every call
     * would have 401'd, and a 401 storm would have walked the outbox. This is where
     * they finally happen, and it is also what restarts a drain that stopped on
     * [com.wovenledger.app.data.outbox.DrainResult.Unauthorized]: that result
     * deliberately schedules no retry, because only a sign-in can change its answer.
     */
    fun submit() {
        val current = _form.value
        val local = validate(current)

        if (local.isNotEmpty()) {
            _form.update { it.copy(errors = local, message = null) }

            return
        }

        viewModelScope.launch {
            _form.update { it.copy(submitting = true, errors = emptyMap(), message = null) }

            runCatching {
                api.login(LoginRequest(current.username.trim(), current.password))
            }.onSuccess { answer ->
                session.signIn(answer.token)
                _form.update { it.copy(submitting = false, signedIn = true, password = "") }

                outbox.start()
                sync.sync()
            }.onFailure { cause ->
                _form.update {
                    it.copy(submitting = false, message = signInFailureMessage(cause))
                }
            }
        }
    }

    private fun validate(form: LoginForm): Map<String, String> = buildMap {
        if (form.username.isBlank()) put("username", "Username is required")
        if (form.password.isBlank()) put("password", "Password is required")
    }
}

/**
 * Why the sign-in did not work, in the user's terms.
 *
 * [com.wovenledger.app.ui.components.failureMessage] cannot serve here: it has no 401
 * case, so a mistyped password would read "The server refused the save (401). Nothing
 * was saved." — which describes neither what was attempted nor what to do about it.
 */
internal fun signInFailureMessage(cause: Throwable): String = when {
    cause is HttpException && cause.code() == UNAUTHORIZED -> "Wrong username or password."
    cause is HttpException ->
        "The server could not sign you in (${cause.code()}). Please try again."

    cause is IOException ->
        "Could not reach the server. Signing in needs a connection, unlike the rest of the app."

    else -> "Signing in could not be completed."
}

/**
 * The screen shown in place of the whole app while there is no token.
 *
 * It sits above the Scaffold rather than inside the NavHost on purpose: as a
 * destination it would have inherited the bottom navigation bar, a back arrow, a title
 * and the pending-uploads badge, every one of which offers something a signed-out user
 * cannot have.
 */
@Composable
fun LoginScreen() {
    val viewModel: LoginViewModel = hiltViewModel()
    val form by viewModel.form.collectAsStateWithLifecycle()

    // No LaunchedEffect popping a back stack here, unlike the other forms: there is no
    // back stack. Signing in flips the session flow that App() gates on, and the whole
    // Scaffold replaces this screen. The flag keeps the button disabled through that
    // hand-over, so a second tap in the gap cannot post the password twice.
    // Every other screen sits inside the Scaffold, which insets itself. This one does
    // not, so it has to do it here or the title draws under the status bar clock.
    // safeDrawing rather than systemBars: it also counts the keyboard, which otherwise
    // covers the very button the two fields above it lead to.
    FormScaffold(
        submitting = form.submitting || form.signedIn,
        saveLabel = "Sign in",
        message = form.message,
        onSave = viewModel::submit,
        modifier = Modifier.safeDrawingPadding(),
    ) {
        Text(
            text = "Woven Ledger",
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(bottom = 4.dp),
        )

        Text(
            text = "Sign in to reach the ledger. The session lasts thirty days.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 12.dp),
        )

        OutlinedTextField(
            value = form.username,
            onValueChange = viewModel::onUsernameChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Username") },
            singleLine = true,
            isError = form.errors.containsKey("username"),
            supportingText = { form.errors["username"]?.let { Text(it) } },
        )

        OutlinedTextField(
            value = form.password,
            onValueChange = viewModel::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Password") },
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            isError = form.errors.containsKey("password"),
            supportingText = { form.errors["password"]?.let { Text(it) } },
        )
    }
}
