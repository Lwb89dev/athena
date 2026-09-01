package com.athena.reader.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.nostr.relay.RelayCatalog
import com.athena.reader.ui.text
import com.athena.reader.ui.theme.GreekKey
import com.athena.reader.ui.theme.TempleSymbol
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.action_add_relay
import athena.shared.generated.resources.action_back
import athena.shared.generated.resources.action_continue
import athena.shared.generated.resources.action_dismiss
import athena.shared.generated.resources.app_name
import athena.shared.generated.resources.app_tagline
import athena.shared.generated.resources.bunker_approval_title
import athena.shared.generated.resources.login_amber
import athena.shared.generated.resources.login_bunker
import athena.shared.generated.resources.login_bunker_uri
import athena.shared.generated.resources.onboard_continue_relays
import athena.shared.generated.resources.onboard_enter
import athena.shared.generated.resources.onboard_login_body
import athena.shared.generated.resources.onboard_login_title
import athena.shared.generated.resources.onboard_no_amber
import athena.shared.generated.resources.onboard_relay_custom
import athena.shared.generated.resources.onboard_relays_body
import athena.shared.generated.resources.onboard_relays_title
import athena.shared.generated.resources.onboard_signed_in_as
import athena.shared.generated.resources.onboard_skip
import athena.shared.generated.resources.onboard_step
import athena.shared.generated.resources.onboard_welcome_1
import athena.shared.generated.resources.onboard_welcome_2
import athena.shared.generated.resources.onboard_welcome_3
import athena.shared.generated.resources.onboard_your_relay
import athena.shared.generated.resources.relay_placeholder
import athena.shared.generated.resources.waiting_signer
import org.jetbrains.compose.resources.stringResource

@Composable
fun OnboardingScreen(
    viewModel: OnboardingViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        StepMark(state.step)
        Spacer(Modifier.height(12.dp))
        GreekKey(Modifier.fillMaxWidth().height(8.dp))
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
        ) {
            when (state.step) {
                OnboardingStep.Welcome -> WelcomePage()
                OnboardingStep.Login -> LoginPage(
                    state = state,
                    onAmber = viewModel::loginWithAmber,
                    onBunker = viewModel::loginWithBunker,
                    onDismissAuth = viewModel::dismissAuthUrl,
                    onDismissError = viewModel::dismissError,
                )
                OnboardingStep.Relays -> RelaysPage(
                    state = state,
                    onToggle = viewModel::toggleRelay,
                    onAddCustom = viewModel::addCustomRelay,
                    onDismissError = viewModel::dismissError,
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        StepActions(state = state, viewModel = viewModel)
    }
}

@Composable
private fun StepMark(step: OnboardingStep) {
    val label = stringResource(
        Res.string.onboard_step,
        when (step) {
            OnboardingStep.Welcome -> 1
            OnboardingStep.Login -> 2
            OnboardingStep.Relays -> 3
        },
        3,
    )
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun WelcomePage() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Icon(
            imageVector = TempleSymbol,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(64.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.app_name).uppercase(),
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.app_tagline),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            text = stringResource(Res.string.onboard_welcome_1),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboard_welcome_2),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboard_welcome_3),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun LoginPage(
    state: OnboardingUiState,
    onAmber: () -> Unit,
    onBunker: (String) -> Unit,
    onDismissAuth: () -> Unit,
    onDismissError: () -> Unit,
) {
    var bunkerUri by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.onboard_login_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(Res.string.onboard_login_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(20.dp))

        if (state.session.isLoggedIn) {
            Text(
                text = stringResource(Res.string.onboard_signed_in_as),
                style = MaterialTheme.typography.labelMedium,
            )
            Text(
                text = state.npub.orEmpty(),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.onboard_continue_relays),
                style = MaterialTheme.typography.bodyMedium,
            )
            return@Column
        }

        if (state.amberAvailable) {
            Button(
                onClick = onAmber,
                enabled = !state.busy,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.login_amber)) }
            Spacer(Modifier.height(12.dp))
        } else {
            Text(
                text = stringResource(Res.string.onboard_no_amber),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = bunkerUri,
            onValueChange = { bunkerUri = it },
            label = { Text(stringResource(Res.string.login_bunker_uri)) },
            singleLine = true,
            enabled = !state.busy,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = { onBunker(bunkerUri) },
            enabled = bunkerUri.isNotBlank() && !state.busy,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.login_bunker)) }

        if (state.busy) {
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp))
                Spacer(Modifier.size(12.dp))
                Text(stringResource(Res.string.waiting_signer), style = MaterialTheme.typography.bodySmall)
            }
        }

        state.authUrl?.let { url ->
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.bunker_approval_title), style = MaterialTheme.typography.titleSmall)
            Text(url, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            TextButton(onClick = onDismissAuth) { Text(stringResource(Res.string.action_dismiss)) }
        }

        state.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message.text(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError) { Text(stringResource(Res.string.action_dismiss)) }
        }
    }
}

@Composable
private fun RelaysPage(
    state: OnboardingUiState,
    onToggle: (String, Boolean) -> Unit,
    onAddCustom: (String) -> Boolean,
    onDismissError: () -> Unit,
) {
    var custom by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = stringResource(Res.string.onboard_relays_title),
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = stringResource(Res.string.onboard_relays_body),
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(16.dp))

        RelayCatalog.onboarding.forEach { relay ->
            RelayRow(
                title = relay.name,
                detail = relay.blurb,
                url = relay.url,
                checked = relay.url in state.selectedRelays,
                onChecked = { onToggle(relay.url, it) },
            )
        }
        state.extraRelays.forEach { url ->
            RelayRow(
                title = url,
                detail = stringResource(Res.string.onboard_relay_custom),
                url = url,
                checked = url in state.selectedRelays,
                onChecked = { onToggle(url, it) },
            )
        }

        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            placeholder = { Text(stringResource(Res.string.relay_placeholder)) },
            label = { Text(stringResource(Res.string.onboard_your_relay)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = {
                if (onAddCustom(custom)) custom = ""
            },
            enabled = custom.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(Res.string.action_add_relay)) }

        state.error?.let { message ->
            Spacer(Modifier.height(12.dp))
            Text(message.text(), color = MaterialTheme.colorScheme.error)
            TextButton(onClick = onDismissError) { Text(stringResource(Res.string.action_dismiss)) }
        }
    }
}

@Composable
private fun RelayRow(
    title: String,
    detail: String,
    url: String,
    checked: Boolean,
    onChecked: (Boolean) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Checkbox(checked = checked, onCheckedChange = onChecked)
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = url,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun StepActions(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Row(
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (state.step == OnboardingStep.Welcome) {
            Spacer(Modifier.weight(1f))
        } else {
            TextButton(onClick = viewModel::back) { Text(stringResource(Res.string.action_back)) }
        }

        when (state.step) {
            OnboardingStep.Welcome ->
                Button(onClick = viewModel::next) { Text(stringResource(Res.string.action_continue)) }
            OnboardingStep.Login -> {
                if (state.session.isLoggedIn) {
                    Button(onClick = viewModel::next) { Text(stringResource(Res.string.action_continue)) }
                } else {
                    OutlinedButton(onClick = viewModel::skipLogin) {
                        Text(stringResource(Res.string.onboard_skip))
                    }
                }
            }
            OnboardingStep.Relays ->
                Button(
                    onClick = viewModel::finish,
                    enabled = state.canFinish,
                ) { Text(stringResource(Res.string.onboard_enter)) }
        }
    }
}
