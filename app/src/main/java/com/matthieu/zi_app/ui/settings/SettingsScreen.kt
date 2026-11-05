package com.matthieu.zi_app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.matthieu.zi_app.ui.podcast.components.PodcastAttributionDialog
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var showPodcastAttributions by remember { mutableStateOf(false) }

    val exportStudyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportStudyDatabaseToUri(it, context) }
    }

    val exportProgressLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        uri?.let { viewModel.exportProgressDatabaseToUri(it, context) }
    }

    val importStudyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.onStudyDatabaseSelectedForImport(it, context) }
    }

    val importProgressLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let { viewModel.importProgressDatabaseFromUri(it, context) }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        viewModel.setNotificationEnabled(isGranted)
        if (!isGranted) {
            viewModel.showFeedbackMessage("Notification permission is required to receive study reminders.")
        }
    }

    val onToggleNotifications: (Boolean) -> Unit = { isChecked ->
        if (isChecked) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val hasPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED

                if (!hasPermission) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else {
                    viewModel.setNotificationEnabled(true)
                }
            } else {
                viewModel.setNotificationEnabled(true)
            }
        } else {
            viewModel.setNotificationEnabled(false)
        }
    }

    LaunchedEffect(uiState.actionFeedbackMessage) {
        uiState.actionFeedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            // 1. Appearance Section
            SettingsSectionHeader(title = "Appearance & Theme")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Theme Mode",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(10.dp))

                    ThemeOptionRow(
                        title = "System Default",
                        subtitle = "Follow system dark/light mode",
                        icon = Icons.Default.BrightnessAuto,
                        selected = uiState.themeMode == AppThemeMode.SYSTEM,
                        onClick = { viewModel.setThemeMode(AppThemeMode.SYSTEM) }
                    )

                    ThemeOptionRow(
                        title = "Light Mode",
                        subtitle = "Classic paper-white normal theme",
                        icon = Icons.Default.LightMode,
                        selected = uiState.themeMode == AppThemeMode.LIGHT,
                        onClick = { viewModel.setThemeMode(AppThemeMode.LIGHT) }
                    )

                    ThemeOptionRow(
                        title = "Dark Mode",
                        subtitle = "OLED-friendly dark obsidian theme",
                        icon = Icons.Default.DarkMode,
                        selected = uiState.themeMode == AppThemeMode.DARK,
                        onClick = { viewModel.setThemeMode(AppThemeMode.DARK) }
                    )

                    Spacer(modifier = Modifier.height(12.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Writing Practice Quiz",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Include stroke-by-stroke character writing tests in review sessions",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = uiState.writingQuizEnabled,
                            onCheckedChange = { viewModel.setWritingQuizEnabled(it) }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 2. Notifications Section
            SettingsSectionHeader(title = "Reminders")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onToggleNotifications(!uiState.notificationsEnabled) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Study Notifications",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Receive reminders when flashcards expire and are due for review",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = onToggleNotifications
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Audio Stories & Podcasts Section
            SettingsSectionHeader(title = "Audio Stories & Podcasts")

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { viewModel.setAudioStoriesEnabled(!uiState.audioStoriesEnabled) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Default.Headphones,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column {
                            Text(
                                text = "Enable Audio Stories & Listening",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Cultural audio stories with full transcripts and study vocabulary",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = uiState.audioStoriesEnabled,
                        onCheckedChange = { viewModel.setAudioStoriesEnabled(it) }
                    )
                }

                if (uiState.audioStoriesEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = { showPodcastAttributions = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Podcast Sources & Attributions")
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 3. Backup & Sharing Section
            SettingsSectionHeader(title = "Database Backup & Sharing")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsActionRow(
                        title = "Export Study Database",
                        subtitle = "Backup or share vocabulary and SRS memory scores (.azdb)",
                        icon = Icons.Default.FileUpload,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.showExportStudyDialog(true) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    SettingsActionRow(
                        title = "Import & Merge Study Database",
                        subtitle = "Safely merge vocabulary and memory scores from an .azdb backup",
                        icon = Icons.Default.FileDownload,
                        iconTint = MaterialTheme.colorScheme.primary,
                        onClick = { importStudyLauncher.launch(arrayOf("*/*")) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    SettingsActionRow(
                        title = "Export Progress History",
                        subtitle = "Backup or share historical analytics chart data (.azdb)",
                        icon = Icons.Default.Timeline,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { viewModel.showExportProgressDialog(true) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    SettingsActionRow(
                        title = "Import Progress History",
                        subtitle = "Import historical analytics snapshots from an .azdb backup",
                        icon = Icons.Default.History,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        onClick = { importProgressLauncher.launch(arrayOf("*/*")) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 4. Data Management & Reset
            SettingsSectionHeader(title = "Data Reset & Maintenance")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    SettingsActionRow(
                        title = "Reset Study Scores",
                        subtitle = "Reset all cards back to Box 0 while keeping your vocabulary",
                        icon = Icons.Default.RestartAlt,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.showResetConfirmation(true) }
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(horizontal = 16.dp),
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                    )

                    SettingsActionRow(
                        title = "Clear Historical Progress",
                        subtitle = "Delete all historical analytics data points",
                        icon = Icons.Default.DeleteForever,
                        iconTint = MaterialTheme.colorScheme.error,
                        onClick = { viewModel.showTrimProgressConfirmation(true) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // 5. About & Attribution
            SettingsSectionHeader(title = "About AndZi")

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                shape = RoundedCornerShape(16.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "AndZi Chinese Learning App",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Version ${uiState.appVersion}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "• Includes CC-CEDICT Chinese-English Dictionary under Creative Commons Attribution-Share Alike 4.0 license.\n• Includes audio stories from Slow Chinese & Reading China (Creative Commons CC BY-NC-SA), MaoMi Chinese (maomichinese.com), and TeaTime Chinese (teatimechinese.com).\n• Includes Hanzi Writer stroke animations & matching engine (David Chanin, MIT License).\n• Includes Make Me a Hanzi & Arphic PL font glyph data (Arphic Technology Co., Ltd. & Shaunak Kishore, Arphic Public License).\n• Includes SUBTLEX-CH frequency corpus (Ghent University / Cai & Brysbaert, PLoS ONE, CC BY 4.0).\n• Includes HSK 3.0 curriculum vocabulary under MIT License.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
        }

        // Export Study Database Dialog
        if (uiState.showExportStudyDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showExportStudyDialog(false) },
                title = { Text("Export Study Database") },
                text = {
                    Column {
                        Text(
                            text = "Choose whether to include your current memory scores (SRS intervals, box ranks, review statistics).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { viewModel.setExportIncludeScores(!uiState.exportIncludeScores) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Include Memory Scores",
                                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
                                )
                                Text(
                                    text = if (uiState.exportIncludeScores) {
                                        "Full study progress included (for personal backup)"
                                    } else {
                                        "Scores reset to Box 0 (for sharing study lists)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = uiState.exportIncludeScores,
                                onCheckedChange = { viewModel.setExportIncludeScores(it) }
                            )
                        }
                    }
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                exportStudyLauncher.launch("AndZiDB_$dateStr.azdb")
                            }
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save File")
                        }
                        Button(
                            onClick = { viewModel.shareStudyDatabase(context) }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showExportStudyDialog(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Export Progress History Dialog
        if (uiState.showExportProgressDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.showExportProgressDialog(false) },
                title = { Text("Export Progress History") },
                text = {
                    Text(
                        text = "Export your historical analytics snapshot records to an .azdb file for backup or sharing.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                confirmButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(
                            onClick = {
                                val dateStr = SimpleDateFormat("yyyyMMdd", Locale.US).format(Date())
                                exportProgressLauncher.launch("AndZiProgress_$dateStr.azdb")
                            }
                        ) {
                            Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Save File")
                        }
                        Button(
                            onClick = { viewModel.shareProgressDatabase(context) }
                        ) {
                            Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Share")
                        }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showExportProgressDialog(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Import Study Database Inspection & Conflict Resolution Dialog
        uiState.pendingImportInspection?.let { inspection ->
            var overwriteScoresChoice by remember { mutableStateOf(false) }

            AlertDialog(
                onDismissRequest = { viewModel.cancelImportInspection() },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Import & Merge Database")
                    }
                },
                text = {
                    Column {
                        Text(
                            text = "Backup Contents:",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• ${inspection.wordCount} words\n• ${inspection.charCount} characters\n• ${inspection.translationCount} translations\n• ${inspection.scoreCount} memory scores",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        if (inspection.scoreCount > 0 && inspection.conflictingScoreCount > 0) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "Score Conflicts Detected:",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${inspection.conflictingScoreCount} items in this backup already have study scores in your app. How would you like to handle conflicting scores?",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { overwriteScoresChoice = false }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = !overwriteScoresChoice,
                                    onClick = { overwriteScoresChoice = false }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Keep current scores (Recommended)",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "Preserve your current learning progress; only new cards adopt backup scores.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { overwriteScoresChoice = true }
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = overwriteScoresChoice,
                                    onClick = { overwriteScoresChoice = true }
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = "Overwrite with backup scores",
                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium)
                                    )
                                    Text(
                                        text = "Replace your existing scores with the incoming backup scores.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = "Vocabulary will be safely merged into your existing library without deleting current entries.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmMergeStudyDatabase(overwriteScoresChoice) }
                    ) {
                        Text("Merge")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelImportInspection() }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Loading dialog for database backup operations
        if (uiState.isProcessingBackup) {
            Dialog(onDismissRequest = { /* Prevent dismiss while processing */ }) {
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(24.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(36.dp))
                        Text(
                            text = "Processing database...",
                            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        // Reset Scores Confirmation Dialog
        if (uiState.showResetConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.showResetConfirmation(false) },
                title = { Text("Reset Study Scores?") },
                text = {
                    Text("Are you sure you want to reset all study cards back to Box 0? Your saved vocabulary and dictionary entries will not be deleted.")
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.resetScores() }
                    ) {
                        Text("Reset", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showResetConfirmation(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clear Historical Progress Confirmation Dialog
        if (uiState.showTrimProgressConfirmation) {
            AlertDialog(
                onDismissRequest = { viewModel.showTrimProgressConfirmation(false) },
                title = { Text("Clear Progress History?") },
                text = {
                    Text("Are you sure you want to delete all historical progress chart snapshots?")
                },
                confirmButton = {
                    TextButton(
                        onClick = { viewModel.clearHistoricalProgress() }
                    ) {
                        Text("Clear", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.showTrimProgressConfirmation(false) }) {
                        Text("Cancel")
                    }
                }
            )
        }

        if (showPodcastAttributions) {
            PodcastAttributionDialog(onDismissRequest = { showPodcastAttributions = false })
        }
    }
}

@Composable
private fun SettingsSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 4.dp, bottom = 8.dp)
    )
}

@Composable
private fun ThemeOptionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        RadioButton(
            selected = selected,
            onClick = onClick
        )
    }
}

@Composable
private fun SettingsActionRow(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
