package com.betesepmu.vendor.ui.vendor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.betesepmu.vendor.model.ChatMessage
import com.betesepmu.vendor.model.ChatRecipient
import com.betesepmu.vendor.model.ChatThread
import com.betesepmu.vendor.ui.components.DropdownSetting
import com.betesepmu.vendor.ui.components.SectionCard
import com.betesepmu.vendor.vendor.VendorViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Locale

private sealed interface ChatMode {
    data object List : ChatMode
    data object New : ChatMode
    data class Thread(val id: String, val title: String) : ChatMode
}

@Composable
fun ChatScreen(vm: VendorViewModel, onMessage: (String) -> Unit) {
    var mode by remember { mutableStateOf<ChatMode>(ChatMode.List) }

    when (val m = mode) {
        is ChatMode.List -> ThreadList(
            vm = vm,
            onOpen = { mode = ChatMode.Thread(it.id, it.name ?: "Conversation") },
            onNew = { mode = ChatMode.New },
        )
        is ChatMode.New -> NewMessage(
            vm = vm,
            onMessage = onMessage,
            onBack = { mode = ChatMode.List },
            onSent = { threadId, title -> mode = ChatMode.Thread(threadId, title) },
        )
        is ChatMode.Thread -> ThreadView(
            vm = vm,
            threadId = m.id,
            title = m.title,
            onMessage = onMessage,
            onBack = { mode = ChatMode.List },
        )
    }
}

@Composable
private fun ThreadList(vm: VendorViewModel, onOpen: (ChatThread) -> Unit, onNew: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val threads by vm.chatThreads.collectAsStateWithLifecycle()

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onNew, modifier = Modifier.fillMaxWidth()) { Text("New message to support") }
        SectionCard("Conversations") {
            if (threads.isEmpty()) {
                Text("No conversations yet. Start one above.", color = cs.onSurfaceVariant)
            } else {
                threads.forEach { thread ->
                    Card(
                        onClick = { onOpen(thread) },
                        colors = CardDefaults.cardColors(containerColor = cs.surfaceVariant),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(Modifier.padding(12.dp)) {
                            Text(thread.name ?: "Conversation", fontWeight = FontWeight.SemiBold)
                            thread.lastMessageTimestamp?.let {
                                Text(
                                    SimpleDateFormat("dd MMM HH:mm", Locale.US).format(it),
                                    fontSize = 11.sp,
                                    color = cs.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NewMessage(
    vm: VendorViewModel,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
    onSent: (threadId: String, title: String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var recipient by remember { mutableStateOf(ChatRecipient.BackOffice) }
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Conversations")
        }
        SectionCard("New Message") {
            DropdownSetting("Send to", ChatRecipient.entries, recipient, { it.label }) { recipient = it }
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text("Message") },
                minLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(
                onClick = {
                    if (text.isBlank()) return@OutlinedButton
                    busy = true
                    scope.launch {
                        vm.sendChat(null, text.trim(), recipient.participants)
                            .onSuccess { threadId -> onSent(threadId, recipient.label) }
                            .onFailure { onMessage(it.message ?: "Could not send") }
                        busy = false
                    }
                },
                enabled = !busy && text.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (busy) "Sending…" else "Send") }
        }
    }
}

@Composable
private fun ThreadView(
    vm: VendorViewModel,
    threadId: String,
    title: String,
    onMessage: (String) -> Unit,
    onBack: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val me by vm.currentUser.collectAsStateWithLifecycle()
    val messages by remember(threadId) { vm.chatMessages(threadId) }
        .collectAsStateWithLifecycle(emptyList())
    var text by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, null); Spacer(Modifier.width(6.dp)); Text("Conversations")
        }
        Text(title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = cs.primary)

        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (messages.isEmpty()) {
                Text("No messages yet — say hello.", color = cs.onSurfaceVariant)
            }
            messages.forEach { msg -> MessageBubble(msg, isMine = msg.senderId == me?.id) }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                placeholder = { Text("Message…") },
                modifier = Modifier.weight(1f),
            )
            IconButton(
                onClick = {
                    if (text.isBlank() || busy) return@IconButton
                    val toSend = text.trim()
                    text = ""
                    busy = true
                    scope.launch {
                        vm.sendChat(threadId, toSend, listOf("BACK_OFFICE"))
                            .onFailure { onMessage(it.message ?: "Could not send") }
                        busy = false
                    }
                },
                enabled = !busy && text.isNotBlank(),
            ) { Icon(Icons.AutoMirrored.Filled.Send, "Send", tint = cs.primary) }
        }
    }
}

@Composable
private fun MessageBubble(msg: ChatMessage, isMine: Boolean) {
    val cs = MaterialTheme.colorScheme
    val time = SimpleDateFormat("HH:mm", Locale.US).format(msg.timestamp)
    Box(Modifier.fillMaxWidth(), contentAlignment = if (isMine) Alignment.CenterEnd else Alignment.CenterStart) {
        Card(
            colors = CardDefaults.cardColors(containerColor = if (isMine) cs.primaryContainer else cs.surfaceVariant),
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth(0.85f),
        ) {
            Column(Modifier.padding(10.dp)) {
                if (!isMine) Text(msg.senderName, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = cs.primary)
                Text(msg.content, fontSize = 14.sp)
                Text(time, fontSize = 10.sp, color = cs.onSurfaceVariant, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
