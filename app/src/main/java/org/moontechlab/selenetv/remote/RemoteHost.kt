package org.moontechlab.selenetv.remote

import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import org.moontechlab.selenetv.model.*
import org.moontechlab.selenetv.screen.detail.sourceKey
import org.moontechlab.selenetv.screen.player.*
import kotlinx.coroutines.delay

data class RemoteLaunch(val request: PlayRequest, val status: RemoteStatus)

@Composable fun RemotePlaybackHost() {
    var launch by remember { mutableStateOf<RemoteLaunch?>(null) }
    DisposableEffect(Unit) {
        val callback: (PlayRequest, RemoteStatus) -> Unit = { request, status -> RemoteReceiver.playbackActive.value = true; launch = RemoteLaunch(request, status) }
        RemoteReceiver.onPlay = callback
        onDispose { if (RemoteReceiver.onPlay === callback) RemoteReceiver.onPlay = null }
    }
    launch?.let { current ->
        key(current.status.sessionId) {
            fun exit() { RemoteReceiver.session.release(current.status.sessionId); launch = null; RemoteReceiver.playbackActive.value = false }
            Dialog(onDismissRequest = { exit() }, properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnBackPress = false)) {
                // Compose disables the default width but still measures Dialog
                // height as content on physical TVs. Fill both dimensions for
                // movie and live playback, as the standalone movie host does.
                val playerView = LocalView.current
                DisposableEffect(playerView) {
                    (playerView.parent as? DialogWindowProvider)?.window?.setLayout(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    onDispose {}
                }
                val m = current.request.media
                if (m.kind == "live") {
                    val channel = LiveChannel(id = m.liveChannelId.ifEmpty { current.request.mediaId }, name = m.title, url = m.url)
                    val source = LiveSource(key = m.sourceId, name = m.title, url = "", ua = m.headers.entries.firstOrNull { it.key.equals("User-Agent", true) }?.value ?: "")
                    LivePlayerScreen(LiveLaunch(channel, source, listOf(LiveChannelGroup("手机选择", listOf(channel)))), onExit = { exit() }, remote = current)
                } else {
                    val episodes = m.episodes.ifEmpty { listOf(RemoteEpisode(m.title, m.url)) }.toMutableList()
                    if (m.episodeIndex in episodes.indices) episodes[m.episodeIndex] = episodes[m.episodeIndex].copy(url = m.url)
                    val source = SearchResult(id = m.videoId.ifEmpty { current.request.mediaId }, title = m.title, poster = m.safeCoverUrl(), source = m.sourceId.ifEmpty { "remote" }, sourceName = "手机选择", episodes = episodes.map { it.url }, episodesTitles = episodes.map { it.title })
                    PlayerScreen(PlayerLaunch(m.title, null, listOf(source), sourceKey(source), m.episodeIndex, m.startPositionMs), onExit = { exit() }, remote = current)
                }
            }
        }
    }
}

@Composable fun BindRemotePlayer(remote: RemoteLaunch?, player: VideoPlayer, onStop: () -> Unit, episodeIndex: Int = 0, title: String? = null) {
    if (remote == null) return
    val stop by rememberUpdatedState(onStop)
    val activeEpisode by rememberUpdatedState(episodeIndex)
    val activeTitle by rememberUpdatedState(title)
    val binding = remember(remote.status.sessionId, player) {
        RemotePlaybackBinding(RemoteReceiver.session, remote.status.sessionId, object : RemotePlayerPort {
            override val playing get() = player.isPlaying
            override val buffering get() = player.isBuffering
            override val started get() = player.hasStarted
            override val position get() = player.positionMs
            override val duration get() = player.durationMs
            override val error get() = player.error
            override var rate: Double get() = player.speed.toDouble(); set(value) { player.speed = value.toFloat() }
            override fun pause() = player.pause()
            override fun play() { if (!player.isPlaying) player.togglePlay() }
            override fun seek(ms: Long) = player.seekTo(ms)
        }) { stop() }
    }
    DisposableEffect(binding) {
        val callback: (CommandRequest) -> Unit = { binding.command(it) }
        RemoteReceiver.onCommand = callback
        onDispose {
            if (RemoteReceiver.onCommand === callback) RemoteReceiver.onCommand = null
            binding.close()
        }
    }
    LaunchedEffect(binding) { while (true) { player.syncProgress(); binding.refresh(activeEpisode, activeTitle); delay(500) } }
}
