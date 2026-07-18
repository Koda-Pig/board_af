package za.co.boardaf.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import za.co.boardaf.data.sync.CloudSyncAvailability
import za.co.boardaf.data.sync.CloudSyncState
import za.co.boardaf.ui.theme.BoardDark
import za.co.boardaf.ui.theme.BoardLine
import za.co.boardaf.ui.theme.BoardMuted
import za.co.boardaf.ui.theme.Coral
import java.text.DateFormat
import java.util.Date

@Composable
fun CloudSyncCard(
    cloud: CloudSyncState,
    actions: BoardActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, BoardLine, RoundedCornerShape(14.dp))
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("Cloud sync", fontWeight = FontWeight.Bold)

        when {
            cloud.availability == CloudSyncAvailability.UNCONFIGURED -> {
                Text(
                    "This build has no Firebase configuration, so problems stay on this device. " +
                        "See docs/firebase-setup.md to enable sync.",
                    style = MaterialTheme.typography.bodySmall,
                    color = BoardMuted,
                )
            }

            !cloud.isSignedIn -> SignInForm(cloud, actions)

            else -> SignedInStatus(cloud, actions)
        }

        cloud.lastError?.let { error ->
            Text(error, style = MaterialTheme.typography.bodySmall, color = Coral)
        }
    }
}

@Composable
private fun SignInForm(cloud: CloudSyncState, actions: BoardActions) {
    var email by rememberSaveable { mutableStateOf("") }
    var password by rememberSaveable { mutableStateOf("") }
    val canSubmit = email.isNotBlank() && password.length >= 6 && !cloud.isAuthBusy

    Text(
        "Sign in to back up problems and keep every device on this board in sync.",
        style = MaterialTheme.typography.bodySmall,
        color = BoardMuted,
    )
    OutlinedTextField(
        value = email,
        onValueChange = { email = it },
        label = { Text("Email") },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = password,
        onValueChange = { password = it },
        label = { Text("Password (min 6 characters)") },
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(
            onClick = { actions.onCloudSignIn(email, password) },
            enabled = canSubmit,
            colors = ButtonDefaults.buttonColors(containerColor = BoardDark, contentColor = Color.White),
        ) {
            Text("Sign in")
        }
        OutlinedButton(
            onClick = { actions.onCloudCreateAccount(email, password) },
            enabled = canSubmit,
        ) {
            Text("Create account")
        }
        if (cloud.isAuthBusy) {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = Coral)
        }
    }
}

@Composable
private fun SignedInStatus(cloud: CloudSyncState, actions: BoardActions) {
    Text(
        "Signed in as ${cloud.userEmail}",
        style = MaterialTheme.typography.bodySmall,
    )
    Text(
        when {
            cloud.isSyncing -> "Syncing…"
            cloud.lastSyncAt != null ->
                "Last synced ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(cloud.lastSyncAt))}"
            else -> "Waiting for the first sync."
        },
        style = MaterialTheme.typography.bodySmall,
        color = BoardMuted,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(
            onClick = actions.onCloudSyncNow,
            enabled = !cloud.isSyncing,
            colors = ButtonDefaults.buttonColors(containerColor = BoardDark, contentColor = Color.White),
        ) {
            Text("Sync now")
        }
        OutlinedButton(onClick = actions.onCloudSignOut) {
            Text("Sign out")
        }
    }
}
