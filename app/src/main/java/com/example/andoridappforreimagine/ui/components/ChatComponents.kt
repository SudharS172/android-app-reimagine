package com.example.andoridappforreimagine.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.andoridappforreimagine.R
import com.example.andoridappforreimagine.data.ChatMessage
import com.example.andoridappforreimagine.data.MessageStatus
import com.example.andoridappforreimagine.viewmodel.ChatViewModel

@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Messages list
        val messages by viewModel.messages.collectAsStateWithLifecycle()
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages) { message ->
                MessageBubble(message)
            }
        }

        // Input area
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var text by remember { mutableStateOf("") }
            val inputText by viewModel.inputText.collectAsStateWithLifecycle()
            val isProcessing by viewModel.isProcessing.collectAsStateWithLifecycle()

            LaunchedEffect(inputText) {
                text = inputText
            }

            OutlinedTextField(
                value = text,
                onValueChange = { 
                    text = it
                    viewModel.onInputTextChanged(it)
                },
                modifier = Modifier.weight(1f),
                placeholder = { Text(stringResource(R.string.input_hint)) },
                enabled = !isProcessing,
                singleLine = true,
                maxLines = 1
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { viewModel.sendMessage() },
                enabled = !isProcessing && text.isNotBlank()
            ) {
                Text(stringResource(R.string.send_button))
            }
        }
    }
}

@Composable
fun MessageBubble(message: ChatMessage) {
    val backgroundColor = if (message.isUser) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }

    val alignment = if (message.isUser) {
        Alignment.End
    } else {
        Alignment.Start
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalAlignment = alignment
    ) {
        Surface(
            color = backgroundColor,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.padding(4.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text(text = message.content)
                
                when (message.status) {
                    MessageStatus.PROCESSING -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                    }
                    MessageStatus.ERROR -> {
                        Text(
                            text = stringResource(R.string.error_generic),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.ACTION_IN_PROGRESS -> {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_in_progress),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.ACTION_COMPLETED -> {
                        Text(
                            text = stringResource(R.string.action_completed),
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.ACTION_FAILED -> {
                        Text(
                            text = stringResource(R.string.action_failed),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    else -> { /* No status indicator needed */ }
                }
            }
        }
    }
}
