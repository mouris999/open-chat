package com.openchat.app.presentation.screens.settings

import android.content.ContentValues.TAG
import android.content.Context
import android.media.RingtoneManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.RingVolume
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import android.media.Ringtone
import android.widget.Toast

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CallSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    var callVibrate by remember { mutableStateOf(getCallVibratePref(context)) }
    var callRingtoneUri by remember { mutableStateOf(getCallRingtonePref(context)) }
    var callRingtoneName by remember { mutableStateOf(getRingtoneName(context, callRingtoneUri)) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Call Settings") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
        ) {
            ListItem(
                headlineContent = { Text("Ringtone") },
                supportingContent = { Text(callRingtoneName) },
                leadingContent = { Icon(Icons.Default.RingVolume, contentDescription = null) },
                modifier = Modifier.clickable {
                    pickRingtone(context) { uri, name ->
                        if (uri != null) {
                            saveCallRingtonePref(context, uri.toString())
                            callRingtoneUri = uri
                            callRingtoneName = name ?: getRingtoneName(context, uri)
                        }
                    }
                }
            )
            Divider()
            ListItem(
                headlineContent = { Text("Vibrate") },
                supportingContent = { Text(if (callVibrate) "Vibrate on incoming call" else "No vibration") },
                leadingContent = { Icon(Icons.Default.Vibration, contentDescription = null) },
                trailingContent = {
                    Switch(checked = callVibrate, onCheckedChange = {
                        callVibrate = it
                        saveCallVibratePref(context, it)
                    })
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Note about FCM
            Divider()
            ListItem(
                headlineContent = { Text("Note") },
                supportingContent = {
                    Text(
                        "For incoming call notifications to work, the caller's device must have " +
                        "a Firebase service account JSON file at app/service-account.json. " +
                        "Get this from Firebase Console -> Project Settings -> Service Accounts."
                    )
                },
                leadingContent = { Icon(Icons.Default.Phone, contentDescription = null) }
            )
        }
    }
}

private fun getRingtoneName(context: Context, uri: Uri): String {
    return try {
        val ringtone = RingtoneManager.getRingtone(context, uri)
        ringtone?.getTitle(context) ?: "Default ringtone"
    } catch (e: Exception) {
        "Default ringtone"
    }
}

private fun pickRingtone(context: Context, onResult: (Uri?, String?) -> Unit) {
    try {
        val intent = android.content.Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
            putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_RINGTONE)
            putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE, "Select Call Ringtone")
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
            putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
            putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, getCallRingtonePref(context))
        }
        if (context is androidx.activity.ComponentActivity) {
            context.activityResultRegistry.register("ringtone_picker", androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()) { result ->
                if (result.resultCode == android.app.Activity.RESULT_OK) {
                    val uri = result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
                    if (uri != null) {
                        onResult(uri, null)
                    }
                }
            }.let { launcher ->
                launcher.launch(intent)
            }
        } else {
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        Log.e(TAG, "Error picking ringtone", e)
        Toast.makeText(context, "Could not open ringtone picker", Toast.LENGTH_SHORT).show()
    }
}

private fun getCallRingtonePref(context: Context): Uri {
    val prefs = context.getSharedPreferences("call_settings", Context.MODE_PRIVATE)
    val uriString = prefs.getString("call_ringtone_uri", null)
    return if (uriString != null) Uri.parse(uriString) else Settings.System.DEFAULT_RINGTONE_URI
}

private fun saveCallRingtonePref(context: Context, uri: String) {
    context.getSharedPreferences("call_settings", Context.MODE_PRIVATE)
        .edit()
        .putString("call_ringtone_uri", uri)
        .apply()
}

private fun getCallVibratePref(context: Context): Boolean {
    return context.getSharedPreferences("call_settings", Context.MODE_PRIVATE)
        .getBoolean("call_vibrate", true)
}

private fun saveCallVibratePref(context: Context, enabled: Boolean) {
    context.getSharedPreferences("call_settings", Context.MODE_PRIVATE)
        .edit()
        .putBoolean("call_vibrate", enabled)
        .apply()
}
