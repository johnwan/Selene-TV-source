package org.moontechlab.selenetv.remote

import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button

@Composable fun TrustedPhonesPanel() {
    var open by remember { mutableStateOf(false) }
    val phones by RemoteReceiver.phones.collectAsState()
    Button(onClick = { open = true }) { Text("已信任的手机 (${phones.size})") }
    if (open) {
        DisposableEffect(Unit) {
            RemoteReceiver.trustUiOpen = true
            onDispose { RemoteReceiver.trustUiOpen = false }
        }
        AlertDialog(onDismissRequest = { open = false }, title = { Text("已信任的手机") }, text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("手动连接：电视 IP:${RemoteReceiver.controlPort}")
                if (phones.isEmpty()) Text("尚未连接手机")
                phones.forEach { phone -> Button(onClick = { RemoteReceiver.revoke(phone.id) }) { Text("移除 ${phone.name}") } }
                if (phones.isNotEmpty()) Button(onClick = { phones.toList().forEach { RemoteReceiver.revoke(it.id) } }) { Text("移除所有手机") }
            }
        }, confirmButton = { Button(onClick = { open = false }) { Text("关闭") } })
    }
}

@Composable fun RemotePairingHost() {
    val pending by RemoteReceiver.pending.collectAsState()
    LaunchedEffect(Unit) { while (true) { RemoteReceiver.refreshPair(); kotlinx.coroutines.delay(1000) } }
    LaunchedEffect(Unit) { RemoteReceiver.messages.collect { org.moontechlab.selenetv.ui.MoonToast.show(it) } }
    pending?.let { p ->
        AlertDialog(onDismissRequest = { RemoteReceiver.decline(p.pairId) }, title = { Text("手机连接请求") },
            text = { Text("${p.name} 想连接此电视。允许后，该手机可以选择视频和控制播放。以后连接无需再次确认。") },
            confirmButton = { Button(onClick = { RemoteReceiver.approve(p.pairId) }) { Text("允许") } },
            dismissButton = { Button(onClick = { RemoteReceiver.decline(p.pairId) }) { Text("拒绝") } })
    }
}
