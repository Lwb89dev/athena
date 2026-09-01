package com.athena.reader.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.athena.reader.data.session.SyncMode
import com.athena.reader.ui.text
import com.athena.reader.ui.theme.InscriptionHeader
import athena.shared.generated.resources.Res
import athena.shared.generated.resources.action_add
import athena.shared.generated.resources.action_dismiss
import athena.shared.generated.resources.bunker_approval_body
import athena.shared.generated.resources.bunker_approval_title
import athena.shared.generated.resources.cd_dismiss
import athena.shared.generated.resources.cd_remove_relay
import athena.shared.generated.resources.login_amber
import athena.shared.generated.resources.login_bunker
import athena.shared.generated.resources.login_bunker_uri
import athena.shared.generated.resources.onboard_signed_in_as
import athena.shared.generated.resources.relay_placeholder
import athena.shared.generated.resources.settings_account
import athena.shared.generated.resources.settings_no_amber
import athena.shared.generated.resources.settings_relays
import athena.shared.generated.resources.settings_sign_out
import athena.shared.generated.resources.settings_signin_blurb
import athena.shared.generated.resources.settings_sync
import athena.shared.generated.resources.sync_auto
import athena.shared.generated.resources.sync_auto_detail
import athena.shared.generated.resources.sync_off
import athena.shared.generated.resources.sync_off_detail
import athena.shared.generated.resources.sync_passphrase
import athena.shared.generated.resources.sync_passphrase_detail
import athena.shared.generated.resources.sync_passphrase_label
import athena.shared.generated.resources.sync_unlock
import athena.shared.generated.resources.sync_unlocked
import org.jetbrains.compose.resources.stringResource

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var bunkerUri by remember { mutableStateOf("") }
    var newRelay by remember { mutableStateOf("") }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item { InscriptionHeader(stringResource(Res.string.settings_account)) }

        item {
            if (state.session.isLoggedIn) {
                SignedInCard(npub = state.npub.orEmpty(), onLogout = viewModel::logout)
            } else {
                SignInCard(
                    externalSignerAvailable = state.externalSignerAvailable,
                    bunkerUri = bunkerUri,
                    onBunkerUriChange = { bunkerUri = it },
                    onAmber = viewModel::loginWithAmber,
                    onBunker = { viewModel.loginWithBunker(bunkerUri) },
                )
            }
        }

        state.authUrl?.let { url ->
            item { AuthUrlCard(url = url, onDismiss = viewModel::dismissAuthUrl) }
        }

        state.error?.let { message ->
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = message.text(),
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = viewModel::dismissError) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cd_dismiss))
                    }
                }
            }
        }

        if (state.session.isLoggedIn) {
            item { InscriptionHeader(stringResource(Res.string.settings_sync)) }
            item {
                SyncModeCard(
                    mode = state.syncMode,
                    unlocked = state.syncUnlocked,
                    busy = state.busy,
                    onMode = viewModel::setSyncMode,
                    onUnlock = viewModel::unlockSync,
                )
            }
        }

        item { InscriptionHeader(stringResource(Res.string.settings_relays)) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newRelay,
                    onValueChange = { newRelay = it },
                    placeholder = { Text(stringResource(Res.string.relay_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.padding(4.dp))
                TextButton(
                    onClick = {
                        viewModel.addRelay(newRelay)
                        newRelay = ""
                    },
                ) { Text(stringResource(Res.string.action_add)) }
            }
        }

        items(state.relays, key = { it }) { relay ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = relay,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = { viewModel.removeRelay(relay) }) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(Res.string.cd_remove_relay))
                }
            }
        }
    }
}

/**
 * The privacy decision the app must not make for the user.
 *
 * Reading positions and private highlights sync through addresses derived from
 * a secret. Where that secret comes from decides whether a relay can tell that
 * this npub uses Athena at all — which is not a hypothetical concern if
 * someone might be treated badly for the books they read.
 */
@Composable
private fun SyncModeCard(
    mode: SyncMode,
    unlocked: Boolean,
    busy: Boolean,
    onMode: (SyncMode) -> Unit,
    onUnlock: (String) -> Unit,
) {
    var passphrase by remember { mutableStateOf("") }

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            SyncOption(
                selected = mode == SyncMode.Off,
                title = stringResource(Res.string.sync_off),
                detail = stringResource(Res.string.sync_off_detail),
                onClick = { onMode(SyncMode.Off) },
            )
            SyncOption(
                selected = mode == SyncMode.Passphrase,
                title = stringResource(Res.string.sync_passphrase),
                detail = stringResource(Res.string.sync_passphrase_detail),
                onClick = { onMode(SyncMode.Passphrase) },
            )
            SyncOption(
                selected = mode == SyncMode.RelayBootstrap,
                title = stringResource(Res.string.sync_auto),
                detail = stringResource(Res.string.sync_auto_detail),
                onClick = { onMode(SyncMode.RelayBootstrap) },
            )

            if (mode != SyncMode.Passphrase) return@Column

            Spacer(Modifier.height(12.dp))
            if (unlocked) {
                Text(
                    text = stringResource(Res.string.sync_unlocked),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
                return@Column
            }

            OutlinedTextField(
                value = passphrase,
                onValueChange = { passphrase = it },
                label = { Text(stringResource(Res.string.sync_passphrase_label)) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                enabled = !busy,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = { onUnlock(passphrase) }, enabled = !busy) {
                    Text(stringResource(Res.string.sync_unlock))
                }
                if (busy) {
                    Spacer(Modifier.padding(8.dp))
                    // Deriving the key is meant to be slow; say so rather than
                    // letting the button look broken.
                    CircularProgressIndicator(Modifier.size(20.dp))
                }
            }
        }
    }
}

@Composable
private fun SyncOption(
    selected: Boolean,
    title: String,
    detail: String,
    onClick: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadioButton(selected = selected, onClick = onClick)
            Text(title, style = MaterialTheme.typography.bodyLarge)
        }
        Text(
            text = detail,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 48.dp),
        )
    }
}

/**
 * A bunker can answer `auth_url` instead of a result: it wants the user to
 * approve this app on a web page first. That is a normal step, not an error, so
 * it gets a card of its own rather than a red message.
 */
@Composable
private fun AuthUrlCard(url: String, onDismiss: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.bunker_approval_title), style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.bunker_approval_body),
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(12.dp))
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.action_dismiss)) }
        }
    }
}

@Composable
private fun SignedInCard(npub: String, onLogout: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(Res.string.onboard_signed_in_as), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                text = npub,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onLogout) { Text(stringResource(Res.string.settings_sign_out)) }
        }
    }
}

/**
 * The two login paths are offered by platform, not by preference: Amber when a
 * NIP-55 signer is installed, a bunker URI otherwise (which is the desktop case).
 * Reading needs neither — the library is public.
 */
@Composable
private fun SignInCard(
    externalSignerAvailable: Boolean,
    bunkerUri: String,
    onBunkerUriChange: (String) -> Unit,
    onAmber: () -> Unit,
    onBunker: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = stringResource(Res.string.settings_signin_blurb),
                style = MaterialTheme.typography.bodyMedium,
            )
            Spacer(Modifier.height(16.dp))

            if (externalSignerAvailable) {
                Button(onClick = onAmber, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(Res.string.login_amber))
                }
                Spacer(Modifier.height(12.dp))
            } else {
                Text(
                    text = stringResource(Res.string.settings_no_amber),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
            }

            OutlinedTextField(
                value = bunkerUri,
                onValueChange = onBunkerUriChange,
                label = { Text(stringResource(Res.string.login_bunker_uri)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(8.dp))
            OutlinedButton(
                onClick = onBunker,
                enabled = bunkerUri.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(Res.string.login_bunker)) }
        }
    }
}
