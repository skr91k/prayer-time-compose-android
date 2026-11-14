package shakir.swalah.ui.reminders

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.core.content.IntentCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import shakir.swalah.net.NetSync
import shakir.swalah.reminder.Sounds
import java.io.File

/**
 * Per-reminder sound: the app's built-in adhan / tones, a system alarm sound, or an audio file
 * from the phone (copied into the app so it still plays after the file is moved, or before unlock).
 * Tapping a built-in sound previews it.
 */
@Composable
fun SoundPickerDialog(current: String?, onDismiss: () -> Unit, onPick: (String?) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selected by remember { mutableStateOf(current) }
    val preview = remember { SoundPreview(context) }
    DisposableEffect(Unit) { onDispose { preview.stop() } }
    // Opening the picker re-checks the server for new / changed adhan files.
    LaunchedEffect(Unit) { NetSync.onAppOpen(context, force = true) }

    val systemPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.let { IntentCompat.getParcelableExtra(it, RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) }
            onPick(uri?.toString())
        }
    }
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) { importAudio(context, uri) }
            if (imported != null) onPick(imported)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sound") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                Sounds.builtIns.forEach { sound ->
                    val uri = Sounds.rawUri(context, sound.rawName)
                    SoundRow(sound.title, selected == uri) {
                        selected = uri
                        preview.play(uri)
                    }
                }
                // Adhans downloaded from the app's server (only missing ones are fetched).
                val netVersion by NetSync.version.collectAsState()
                val netStatus by NetSync.status.collectAsState()
                val downloaded = remember(netVersion) { NetSync.downloadedSounds(context) }
                downloaded.forEach { file ->
                    val uri = Uri.fromFile(file).toString()
                    SoundRow(file.nameWithoutExtension.trim(), selected == uri) {
                        selected = uri
                        preview.play(uri)
                    }
                }
                netStatus?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                val custom = selected?.takeIf { s ->
                    Sounds.builtIns.none { Sounds.rawUri(context, it.rawName) == s } && downloaded.none { Uri.fromFile(it).toString() == s }
                }
                if (custom != null) SoundRow(Sounds.title(context, custom), true) { preview.play(custom) }
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                // One entry per system category: the system picker shows a single type at a time.
                listOf(
                    RingtoneManager.TYPE_ALARM to "System alarm sounds…",
                    RingtoneManager.TYPE_NOTIFICATION to "System notification sounds…",
                    RingtoneManager.TYPE_RINGTONE to "System ringtones…",
                ).forEach { (type, title) ->
                    TextButton(onClick = {
                        preview.stop()
                        systemPicker.launch(
                            Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, type)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false)
                                .putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Reminder sound")
                                .putExtra(
                                    RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                                    selected?.let(Uri::parse) ?: RingtoneManager.getDefaultUri(type),
                                ),
                        )
                    }) { Text(title) }
                }
                TextButton(onClick = { preview.stop(); filePicker.launch(arrayOf("audio/*")) }) { Text("Audio file from phone…") }
            }
        },
        confirmButton = { TextButton(onClick = { onPick(selected) }) { Text("OK") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun SoundRow(title: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(12.dp))
        Text(title, style = MaterialTheme.typography.bodyLarge)
    }
}

private fun importAudio(context: Context, uri: Uri): String? = runCatching {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
        if (c.moveToFirst()) c.getString(0) else null
    } ?: "sound_${System.currentTimeMillis()}"
    val safe = name.replace(Regex("[^\\w.\\- ]"), "_")
    val dest = File(Sounds.importDir(context), safe)
    context.contentResolver.openInputStream(uri)!!.use { input -> dest.outputStream().use { input.copyTo(it) } }
    Uri.fromFile(dest).toString()
}.getOrNull()

/** Short preview while choosing; stops when the dialog closes. */
private class SoundPreview(private val context: Context) {
    private var player: MediaPlayer? = null

    fun play(uri: String) {
        stop()
        player = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
                setDataSource(context, Uri.parse(uri))
                prepare()
                start()
            }
        }.getOrNull()
    }

    fun stop() {
        player?.let { runCatching { it.stop() }; it.release() }
        player = null
    }
}
