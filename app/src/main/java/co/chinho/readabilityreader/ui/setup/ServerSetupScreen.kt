package co.chinho.readabilityreader.ui.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.chinho.readabilityreader.ui.theme.LocalEInkMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSetupScreen(
    onSetupComplete: () -> Unit,
    viewModel: ServerSetupViewModel = hiltViewModel()
) {
    var serverUrl by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isEInk = LocalEInkMode.current

    fun handleSave() {
        viewModel.saveProfile(serverUrl, username, password)
    }

    LaunchedEffect(Unit) {
        viewModel.navigationEvent.collect {
            onSetupComplete()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("Add Server Profile", style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onBackground)
            Spacer(modifier = Modifier.height(32.dp))
            
            OutlinedTextField(
                value = serverUrl,
                onValueChange = { serverUrl = it.replace("\n", "") },
                label = { Text("Server URL") },
                placeholder = { Text("https://your-server.example/fever/") },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ServerSetupUiState.Loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = username,
                onValueChange = { username = it.replace("\n", "") },
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ServerSetupUiState.Loading,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = password,
                onValueChange = { password = it.replace("\n", "") },
                label = { Text("Password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth(),
                enabled = uiState !is ServerSetupUiState.Loading,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(onDone = { handleSave() }),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            if (uiState is ServerSetupUiState.Error) {
                Text(
                    text = (uiState as ServerSetupUiState.Error).message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
            }

            Button(
                onClick = { handleSave() },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
                enabled = uiState !is ServerSetupUiState.Loading
            ) {
                if (uiState is ServerSetupUiState.Loading) {
                    if (isEInk) {
                        Text("Saving...")
                    } else {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.onPrimary,
                            strokeWidth = 2.dp
                        )
                    }
                } else {
                    Text("Save Profile")
                }
            }
        }
    }
}
