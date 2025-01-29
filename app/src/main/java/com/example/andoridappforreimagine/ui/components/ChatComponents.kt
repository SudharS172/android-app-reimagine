package com.example.andoridappforreimagine.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
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
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(viewModel.messages.value) { message ->
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
            OutlinedTextField(
                value = viewModel.inputText.value,
                onValueChange = { viewModel.onInputTextChanged(it) },
                modifier = Modifier.weight(1f),
                placeholder = { Text("Type a command...") },
                enabled = !viewModel.isProcessing.value
            )

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = { viewModel.sendMessage() },
                enabled = !viewModel.isProcessing.value && viewModel.inputText.value.isNotBlank()
            ) {
                Text("Send")
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
                            text = "Error",
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
                            text = "Performing action...",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.ACTION_COMPLETED -> {
                        Text(
                            text = "✓ Completed",
                            color = MaterialTheme.colorScheme.primary,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    MessageStatus.ACTION_FAILED -> {
                        Text(
                            text = "✗ Failed",
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
