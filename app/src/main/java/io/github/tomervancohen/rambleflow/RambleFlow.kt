package io.github.tomervancohen.rambleflow

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.Settings as AndroidSettings
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalDensity
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.google.android.gms.auth.api.identity.Identity
import java.text.DateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RambleFlow(app: RambleApp, device: RecorderDevice = RecorderDevice.current()) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val activity = androidx.activity.compose.LocalActivity.current as ComponentActivity
    val settings = app.settings
    val trialRecorder: TrialRecorder = androidx.lifecycle.viewmodel.compose.viewModel()
    val trialCapture by trialRecorder.state.collectAsState()
    var quickSettingsOpened by rememberSaveable { mutableStateOf(false) }
    var micRequested by remember { mutableStateOf(false) }
    val micPermission =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) trialRecorder.start() else trialRecorder.denied()
        }
    fun beginTrial() {
        if (
            androidx.core.content.ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.RECORD_AUDIO,
            ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        )
            trialRecorder.start()
        else if (
            micRequested &&
                !activity.shouldShowRequestPermissionRationale(
                    android.Manifest.permission.RECORD_AUDIO
                )
        ) {
            context.startActivity(
                Intent(
                    AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.parse("package:${context.packageName}"),
                )
            )
        } else {
            micRequested = true
            micPermission.launch(android.Manifest.permission.RECORD_AUDIO)
        }
    }
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, trialRecorder) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) trialRecorder.cancel()
            }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val scope = rememberCoroutineScope()
    var screen by remember {
        mutableStateOf(AppEntry.screen(
            settings.step, settings.hasKey(settings.provider),
            live = activity.intent.getBooleanExtra("ramble.live.recording", false),
            language = activity.intent.getBooleanExtra("ramble.language.settings", false),
        )).also { activity.intent.removeExtra("ramble.language.settings") }
    }
    var revision by remember { mutableIntStateOf(0) }
    DisposableEffect(lifecycle) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) { revision++;app.maintainPrivateAudio() }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    var candidate by remember { mutableStateOf(settings.provider) }
    var key by remember { mutableStateOf("") }
    var keyDraftSaving by remember { mutableStateOf(false) }
    var keyCheck by remember { mutableStateOf("idle") }
    var verifiedKey by remember { mutableStateOf<Pair<String, String>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var notice by remember { mutableStateOf("") }
    var driveServicesResolutionCode by remember { mutableStateOf<Int?>(null) }
    var selected by remember { mutableStateOf<Recording?>(null) }
    var deleteCandidate by remember { mutableStateOf<Recording?>(null) }
    var customFolderHasAudio by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var customFolderKind by rememberSaveable { mutableStateOf("voice") }
    var requestedKind by remember { mutableStateOf("voice") }
    var replacingFolderTree by remember { mutableStateOf<String?>(null) }
    var promptEnglish by remember { mutableStateOf(settings.promptEnglish) }
    var promptCopied by remember { mutableStateOf(false) }
    var driveChoices by remember { mutableStateOf(emptyList<DriveExport.Folder>()) }
    var driveFolderDraft by rememberSaveable { mutableStateOf("") }
    val jobs by app.database.queue().watch().collectAsState(initial = emptyList())
    val summaries by app.database.queue().summaries().collectAsState(initial = emptyList())
    val rawTrialJob =
        jobs.find { it.id == settings.trialRecording } ?:
            if (settings.trialRecording.isBlank()) jobs.firstOrNull { it.source == "app" } else null
    val trialJob =
        if (rawTrialJob?.state == "duplicate")
            jobs.find { it.hash == rawTrialJob.hash && it.state == "saved" } ?: rawTrialJob
        else rawTrialJob
    LaunchedEffect(screen) { if (screen !in setOf("7", "trial")) trialRecorder.cancel() }
    val sourceStatus by
        produceState(
            Triple(emptyList<Source>(), emptySet<String>(), false),
            revision,
            screen,
        ) {
            value =
                withContext(Dispatchers.IO) {
                    val reader = SourceReader(app, app.database, settings)
                    reader.restoreCoveredSources()
                    val saved = app.database.queue().sources()
                    val readable =
                        saved
                            .filter { it.enabled && it.baselineComplete && reader.canRead(it) }
                            .map { it.kind }
                            .toSet()
                    val parent = saved.find { it.kind == "voice" }?.tree ?: settings.tree
                    val callsAvailable =
                        saved.any { it.kind == "call" && it.baselineComplete && reader.canRead(it) } ||
                            (parent.isNotBlank() && reader.hasKnownCallFolder(Uri.parse(parent)))
                    Triple(saved, readable, callsAvailable)
                }
        }
    val connectedFolders by produceState(emptyList<ConnectedFolder>(), sourceStatus.first, screen, revision) {
        value = withContext(Dispatchers.IO) {
            val reader = SourceReader(app, app.database, settings)
            sourceStatus.first.filter { it.baselineComplete && it.tree !in settings.removedSourceTrees }
                .groupBy { it.tree }.map { (tree, members) ->
                    val root = members.first().copy(document = DocumentsContract.getTreeDocumentId(Uri.parse(tree)))
                    ConnectedFolder(tree, reader.folderLabel(root), reader.canRead(root), members)
                }.sortedBy { it.name.lowercase() }
        }
    }
    val sources = sourceStatus.first
    val readableSources = sourceStatus.second
    val callsAvailable = sourceStatus.third
    val setupVoiceAccess = "voice" in readableSources
    val setupCallAccess = "call" in readableSources
    val snackbar = remember { SnackbarHostState() }
    val isSetup = screen.toIntOrNull() != null || (screen == "quickcheck" && settings.step < 10)
    val repair = !isSetup
    fun go(step: Int) {
        settings.step = step
        screen = step.toString()
        notice = ""
    }


    fun open(uri: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(uri))) }
            .onFailure { notice = appText(R.string.cannot_open_link) }
    }
    fun task(work: suspend () -> Unit) {
        if (busy) return
        busy = true
        notice = ""
        scope.launch {
            try {
                withContext(Dispatchers.IO) { work() }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                notice =
                    when {
                        e is RemoteFailure && e.kind == "key" ->
                            appText(R.string.key_rejected_help)
                        e is RemoteFailure && e.kind.startsWith("drive") ->
                            appText(R.string.ui_couldn_t_access_drive_check_the_connection_an_0b28fd7)
                        e.message == "recording_busy" -> appText(R.string.ui_wait_for_active_processing_to_finish_then_try_63ea4c9)
                        e.message == "incomplete_audio" -> appText(R.string.ui_this_file_was_not_finalized_and_couldn_t_be_r_962928c)
                        e is SecurityException -> appText(R.string.folder_access_needed)
                        else ->
                            appText(R.string.action_incomplete)
                    }
            } finally {
                busy = false
                revision++
            }
        }
    }
    fun finish() {
        settings.step = 10
        screen = "home"
        Schedule.scan(app)
    }
    fun loadDriveFolders() = task {
        val api = DriveExport(app, settings)
        val choices = api.folders(api.token(refreshFolder = false))
        withContext(Dispatchers.Main) { driveChoices = choices }
    }
    fun useDriveFolder(id: String) = task {
        val api = DriveExport(app, settings)
        api.selectFolder(api.token(refreshFolder = false), id)
        withContext(Dispatchers.Main) { screen = "drive" }
        Schedule.pump(app)
    }
    LaunchedEffect(screen) { if (screen == "drive-folders") loadDriveFolders() }
    fun agentText() = Policy.prompt(promptEnglish, settings.driveFolder)
    val folderLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result
            ->
            val uri = result.data?.data
            val previousFolder = replacingFolderTree
            replacingFolderTree = null
            if (uri != null)
                task {
                    val flags =
                        result.data!!.flags and
                            (Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    require(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION,
                    )
                    val reader = SourceReader(app, app.database, settings)
                    val connectingSetup = screen == "3" && !requestedKind.startsWith("folder:")
                    val success = if (previousFolder != null) {
                        reader.replaceFolder(previousFolder, uri)
                    } else if (connectingSetup) {
                        val voice = reader.resolveFolder(uri, "voice", allowCustom = false)
                        val call = reader.resolveFolder(uri, "call", allowCustom = false)
                        if (voice != null) reader.activate(uri, "voice")
                        if (call != null) {
                            reader.activate(uri, "call")
                        }
                        if (voice != null || call != null) {
                            val emptySingleSource = (voice == null || call == null) &&
                                listOfNotNull(voice, call).all { doc ->
                                    reader.entries(uri.toString(), doc).none { reader.audio(it) }
                                }
                            withContext(Dispatchers.Main) {
                                customFolderHasAudio = if (emptySingleSource) false else null
                                customFolderKind = if (voice != null) "voice" else "call"
                            }
                            true
                        } else {
                            val doc = reader.resolveFolder(uri, requestedKind)
                            if (doc == null) false else {
                                val hasAudio = reader.entries(uri.toString(), doc).any { reader.audio(it) }
                                val activated = reader.activate(uri, requestedKind)
                                if (activated) withContext(Dispatchers.Main) {
                                    customFolderKind = requestedKind
                                    customFolderHasAudio = hasAudio
                                }
                                activated
                            }
                        }
                    } else reader.activate(uri, requestedKind)
                    withContext(Dispatchers.Main) {
                        replacingFolderTree = null
                        if (success) {
                            // A grant can be added after Application.onCreate (when no
                            // source existed). Arm MediaStore wake-ups and scan now.
                            Schedule.scan(app)
                            if (connectingSetup) {
                                revision++
                            } else if (screen == "3") {
                                customFolderHasAudio = null
                                revision++
                            } else if (isSetup) go(5)
                            else screen = "sources"
                        } else notice =
                            appText(R.string.folder_not_found)
                    }
                }
            else notice = appText(R.string.folder_access_cancelled)
        }
    val outputLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                    settings.outputTree = uri.toString()
                    revision++
                    screen = "settings"
                }
                    .onFailure {
                        notice = appText(R.string.folder_not_writable)
                    }
            }
        }
    fun chooseSource(kind: String, repairAccess: Boolean = false) {
        requestedKind = kind
        val knownTree =
            sources.find { it.kind == kind }?.tree
                ?: sources.find { it.kind == "voice" }?.tree
                ?: settings.tree
        if (!repairAccess && kind == "call" && callsAvailable && knownTree.isNotBlank())
            task {
                val success =
                    SourceReader(app, app.database, settings).activate(Uri.parse(knownTree), kind)
                withContext(Dispatchers.Main) {
                    if (success) {
                        if (isSetup) go(5) else screen = "sources"
                    } else {
                        notice = ""
                        revision++
                    }
                }
            }
        else {
            val initial =
                DocumentsContract.buildDocumentUri(
                    "com.android.externalstorage.documents",
                    "primary:${device.suggestedFolder}",
                )
            val intent =
                Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(
                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    )
                    .putExtra(DocumentsContract.EXTRA_INITIAL_URI, initial)
            folderLauncher.launch(intent)
        }
    }
    fun launchQuickSettings() {
        runCatching { QuickRecordingSettings.open(context, device) }
            .onSuccess { quickSettingsOpened = true }
            .onFailure { notice = appText(R.string.ui_couldn_t_open_settings_open_them_from_your_ph_5700da0) }
    }
    fun openQuickRecordingSettings() { launchQuickSettings() }
    fun connected(token: String) {
        task {
            DriveExport(app, settings).provision(token)
            withContext(Dispatchers.Main) { if (isSetup) go(6) else screen = "drive" }
        }
    }
    fun driveAuthFailed(stage: String, error: Throwable? = null, cancelled: Boolean = false) {
        val status = (error as? com.google.android.gms.common.api.ApiException)?.statusCode
        driveServicesResolutionCode = if (DriveAuthErrors.needsServicesUpdate(
            status, (error as? com.google.android.gms.common.api.ApiException)?.status?.statusMessage,
        )) com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED else null
        // Status codes identify configuration/network failures without logging accounts or tokens.
        android.util.Log.w("RambleDriveAuth", "stage=$stage status=${status ?: "none"} cancelled=$cancelled")
        busy = false
        notice = when {
            driveServicesResolutionCode != null -> appText(R.string.ui_google_play_services_on_this_phone_can_t_conn_06c93af)
            status == 10 -> appText(R.string.ui_this_version_of_ramble_isn_t_registered_corre_4c72858)
            cancelled || status in setOf(16, 12501) -> appText(R.string.ui_connection_cancelled_try_again_when_you_re_re_f25da53)
            status == 7 -> appText(R.string.ui_couldn_t_reach_google_check_your_internet_con_d9d52bf)
            else -> appText(R.string.ui_google_didn_t_complete_the_connection_try_aga_08bd154)
        }
    }
    val authLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r
            ->
            runCatching {
                Identity.getAuthorizationClient(activity)
                    .getAuthorizationResultFromIntent(r.data)
                    .accessToken ?: error("drive_auth")
            }
                .onSuccess { connected(it) }
                .onFailure { error ->
                    driveAuthFailed("consent_result", error,
                        cancelled = r.resultCode != android.app.Activity.RESULT_OK)
                }
        }
    fun connectDrive() {
        if (busy) return
        busy = true
        notice = ""
        driveServicesResolutionCode = null
        val availability = com.google.android.gms.common.GoogleApiAvailability.getInstance()
        val servicesInstalled = runCatching { activity.packageManager.getPackageInfo("com.google.android.gms", 0) }.isSuccess
        val servicesCode = if (servicesInstalled) availability.isGooglePlayServicesAvailable(activity)
            else com.google.android.gms.common.ConnectionResult.SERVICE_MISSING
        if (servicesCode != com.google.android.gms.common.ConnectionResult.SUCCESS) {
            busy = false
            driveServicesResolutionCode = servicesCode.takeIf { it != com.google.android.gms.common.ConnectionResult.SERVICE_MISSING && availability.isUserResolvableError(it) }
            android.util.Log.w("RambleDriveAuth", "stage=services_availability status=$servicesCode")
            notice = when (servicesCode) {
                com.google.android.gms.common.ConnectionResult.SERVICE_MISSING -> appText(R.string.drive_services_missing)
                com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED -> appText(R.string.drive_services_disabled)
                com.google.android.gms.common.ConnectionResult.SERVICE_VERSION_UPDATE_REQUIRED -> appText(R.string.ui_google_play_services_on_this_phone_can_t_conn_06c93af)
                else -> appText(R.string.drive_services_unavailable)
            }
            return
        }
        Identity.getAuthorizationClient(activity)
            .authorize(DriveExport(app, settings).authRequest())
            .addOnSuccessListener { r ->
                busy = false
                if (r.hasResolution())
                    authLauncher.launch(
                        IntentSenderRequest.Builder(r.pendingIntent!!.intentSender).build()
                    )
                else {
                    val token = r.accessToken
                    if (token.isNullOrBlank()) driveAuthFailed("missing_token") else connected(token)
                }
            }
            .addOnFailureListener { error ->
                driveAuthFailed("authorize", error)
            }
    }
    fun providerSite() {
        open(
            if (candidate == "groq") "https://console.groq.com/keys"
            else "https://platform.openai.com/api-keys"
        )
    }
    fun saveKeyAndContinue() {
        val entered = key.trim()
        task {
            val value = entered.ifBlank {
                settings.readKey(settings.profile(candidate))
            }
            if (verifiedKey != (candidate to value))
                ProviderApi().validate(candidate, value)
            if (entered.isNotBlank()) settings.saveKey(candidate, value)
            settings.provider = candidate
            if (app.database.queue().resumeKeyBlocked(candidate, settings.profile(candidate)) > 0)
                Schedule.pump(app)
            withContext(Dispatchers.Main) {
                key = ""
                if (
                    settings.step in 7..9 && selected?.id == settings.trialRecording
                )
                    go(7)
                else if (isSetup) go(3) else screen = "settings"
            }
        }
    }
    suspend fun checkEnteredKey(provider: String, value: String) {
        keyCheck = "checking"
        notice = ""
        try {
            withContext(Dispatchers.IO) { ProviderApi().validate(provider, value) }
            if (candidate == provider && key.trim() == value && screen in setOf("2", "key")) {
                verifiedKey = provider to value
                keyCheck = "ok"
                saveKeyAndContinue()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (candidate == provider && key.trim() == value) {
                verifiedKey = null
                keyCheck = if (e is RemoteFailure && e.kind == "key") "invalid" else "offline"
            }
        }
    }
    LaunchedEffect(screen, candidate) {
        if (screen in setOf("2", "key") && key.isBlank()) {
            val draft = withContext(Dispatchers.IO) { settings.readKeyDraft(candidate) }
            if (key.isBlank()) key = draft
        }
    }
    LaunchedEffect(screen, candidate, key) {
        if (screen in setOf("2", "key")) {
            keyCheck = "idle"
            if (key.trim().length >= 20) {
                delay(700)
                checkEnteredKey(candidate, key.trim())
            }
        }
    }
    fun back() {
        trialRecorder.cancel()
        notice = ""
        key = ""
        screen =
            when (screen) {
                "0" -> "0"
                "1" -> "0"
                "2" -> "1"
                "help" -> if (settings.step < 10) "2" else "key"
                "3" -> "2"
                "4" -> "3"
                "5" -> "3"
                "6" -> "5"
                "7" -> if (settings.driveEnabled) "6" else "5"
                "8" -> "7"
                "9" -> "7"
                "quickcheck" -> "8"
                "home" -> "home"
                "settings" -> "home"
                "drive-folders" -> "drive"
                "detail" -> if (settings.step in 7..9) "7" else "home"
                "trial" -> "settings"
                "recording-live" -> AppEntry.screen(settings.step, settings.hasKey(settings.provider))
                else -> "settings"
            }
    }
    androidx.activity.compose.BackHandler(screen != "home" && screen != "0") { back() }
    val screenTitle =
        when (screen) {
            "home" -> "Ramble"
            "settings" -> appText(R.string.ui_settings_7e3c1bd)
            "detail" -> appText(R.string.ui_your_transcript_9fed024)
            else -> "Ramble"
        }
    val contentScroll = remember(screen) { ScrollState(0) }
    val hasFooter =
        (screen == "detail" && selected != null) ||
            screen in
                setOf(
                    "1",
                    "provider",
                    "2",
                    "key",
                    "help",
                    "3",
                    "4",
                    "5",
                    "drive",
                    "drive-folders",
                    "6",
                    "agent",
                    "7",
                    "trial",
                    "8",
                    "guide",
                    "9",
                    "output",
                    "quickcheck",
                )
    fun openProviderKey() {
        key = ""
        screen = if (isSetup) "2" else "key"
        if (isSetup) settings.step = 2
    }
    val addRecordingFolder: () -> Unit = {
        replacingFolderTree = null
        chooseSource("folder:" + java.util.UUID.randomUUID(), repairAccess = true)
    }
    val changeRecordingFolder: (ConnectedFolder) -> Unit = { folder ->
        replacingFolderTree = folder.tree
        val kind = folder.members.find { it.kind == "voice" }?.kind ?: folder.members.first().kind
        chooseSource(kind, repairAccess = true)
    }
    val removeRecordingFolder: (ConnectedFolder) -> Unit = { folder ->
        customFolderHasAudio = null
        task { SourceReader(app, app.database, settings).removeFolder(folder.tree) }
    }
    val folderManagement: @Composable (Boolean) -> Unit = { _ ->
        FolderConnections(connectedFolders, busy, addRecordingFolder, changeRecordingFolder, removeRecordingFolder)
    }
    val pageActions: @Composable () -> Unit = {
        when (screen) {
            "1",
            "provider" -> {
                Action(appText(R.string.ui_continue_51df566)) {
                    openProviderKey()
                }
            }
            "2",
            "key" -> {
                if (key.isBlank() && !settings.hasKey(candidate)) {
                    Action(appText(R.string.get_provider_key,candidate)) {
                        providerSite()
                    }
                } else if (key.isNotBlank() && keyCheck != "ok") {
                    Action(
                        if (keyCheck == "checking") appText(R.string.ui_checking_your_key_4299568)
                        else appText(R.string.ui_try_again_96eaf84),
                        !busy && keyCheck != "checking" && key.trim().length >= 20,
                    ) {
                        scope.launch { checkEnteredKey(candidate, key.trim()) }
                    }
                } else
                    Action(
                        if (isSetup) appText(R.string.ui_continue_51df566) else appText(R.string.ui_save_f622d3a),
                        !busy,
                    ) {
                        saveKeyAndContinue()
                    }
            }
            "help" -> {
                Action(appText(R.string.ui_open_provider_website_c6cd642)) {
                    open(
                        if (candidate == "groq") "https://console.groq.com/keys"
                        else "https://platform.openai.com/api-keys"
                    )
                }
                Action(appText(R.string.ui_back_to_key_a19a070), secondary = true) {
                    screen = if (settings.step < 10) "2" else "key"
                }
            }
            "3" -> {
                if (device.family == RecorderFamily.PIXEL) {
                    Action(appText(R.string.ui_continue_51df566)) { go(5) }
                } else if (readableSources.isNotEmpty()) {
                    Action(appText(R.string.ui_continue_51df566), !busy) { go(5) }
                    if (customFolderHasAudio != null) {
                        Action(appText(R.string.ui_choose_another_folder_58c9c1c), !busy, secondary = true) {
                            chooseSource(customFolderKind, repairAccess = true)
                        }
                    }
                } else {
                    Action(appText(R.string.ui_allow_recording_access_1e4da6d), !busy) {
                        chooseSource("voice", repairAccess = true)
                    }
                }
            }
            "7",
            "trial" -> {
                when {
                    trialJob?.reason == "no_speech" ->
                        Action(appText(R.string.ui_record_again_05a3b5f)) {
                            trialRecorder.reset()
                            revision++
                        }
                    trialJob?.state == "saved" ->
                        Action(appText(R.string.ui_set_up_quick_recording_33d6a89)) {
                            if (isSetup) go(8) else screen = "guide"
                        }
                    trialJob != null -> {
                        if (trialJob.state in setOf("error", "uncertain", "key", "rate"))
                            Action(appText(R.string.ui_review_transcription_7e398bd)) {
                                selected = trialJob
                                screen = "detail"
                            }
                        else Action(if (settings.paused) appText(R.string.ui_transcription_paused_9d8f35b) else appText(R.string.ui_transcribing_f800f8e), enabled = false) {}
                    }
                    trialCapture.mode == "recording" ->
                        TrialRecordButton(recording = true, enabled = trialCapture.seconds >= 1) {
                            trialRecorder.stop()
                        }
                    trialCapture.mode == "saving" || trialCapture.mode == "submitted" ->
                        Action(
                            appText(R.string.ui_preparing_your_transcript_92f460a),
                            enabled = false,
                        ) {}
                    else -> TrialRecordButton(recording = false, enabled = !busy) {
                        beginTrial()
                    }
                }
                Action(
                    when {
                        trialCapture.mode == "recording" -> appText(R.string.ui_cancel_1fa68c8)
                        trialJob?.state == "saved" -> appText(R.string.ui_go_to_home_10d9f47)
                        trialJob != null -> appText(R.string.ui_continue_later_ce8721f)
                        else -> appText(R.string.ui_skip_23a36fc)
                    },
                    secondary = true,
                    enabled = trialCapture.mode != "saving",
                ) {
                    if (trialCapture.mode == "recording") trialRecorder.cancel("cancelled")
                    else if (trialJob != null) finish()
                    else if (isSetup) go(8) else screen = "guide"
                }
            }
            "8",
            "guide" -> {
                if (device.family in setOf(RecorderFamily.SAMSUNG, RecorderFamily.PIXEL)) {
                    Action(appText(R.string.ui_set_up_shortcut_5f551f6)) { openQuickRecordingSettings() }
                }
                Action(appText(R.string.ui_add_quick_settings_tile_320848c), secondary = device.family in setOf(RecorderFamily.SAMSUNG, RecorderFamily.PIXEL)) {
                    QuickRecordingAccess.addTile(context) { message -> notice = message }
                }
                Action(appText(R.string.ui_add_to_home_screen_f5eb116), secondary = true) {
                    if (!QuickRecordingAccess.pin(context)) notice = appText(R.string.ui_long_press_ramble_and_choose_record_or_use_th_a8a8004)
                }
                Action(if (quickSettingsOpened) appText(R.string.ui_done_ae30c54) else appText(R.string.ui_continue_51df566), secondary = true) {
                    if (isSetup) finish() else screen = "settings"
                }
            }
            "quickcheck" -> {
                Action(appText(R.string.ui_done_ae30c54)) { finish() }
                Action(appText(R.string.ui_open_settings_again_fe95363), secondary = true) {
                    openQuickRecordingSettings()
                }
            }
            "9" -> {
                Action(appText(R.string.ui_try_your_first_recording_0deff5f)) {
                    go(7)
                }
                Action(appText(R.string.ui_go_to_home_10d9f47), secondary = true) { finish() }
            }
            "output" -> {
                Action(appText(R.string.ui_choose_a_folder_6b65afe)) {
                    outputLauncher.launch(null)
                }
                Action(appText(R.string.ui_use_default_downloads_171fe5a), secondary = true) {
                    settings.outputTree = ""
                    revision++
                    screen = "settings"
                }
            }
            "5",
            "drive" -> {
                if (settings.driveEnabled) {
                    if (isSetup) Action(appText(R.string.ui_continue_51df566)) { go(6) }
                    else {
                        Action(appText(R.string.ui_open_google_drive_01e0330)) {
                            open("https://drive.google.com/drive/folders/${settings.driveFolder}")
                        }
                        Action(appText(R.string.ui_change_folder_555b8f5), secondary = true) { screen = "drive-folders" }
                    }
                    Action(appText(R.string.ui_disconnect_drive_e1596bc), secondary = true) {
                        settings.driveEnabled = false
                        revision++
                        screen = "settings"
                    }
                } else {
                    if (driveServicesResolutionCode != null) {
                        Action(appText(if (driveServicesResolutionCode == com.google.android.gms.common.ConnectionResult.SERVICE_DISABLED) R.string.drive_services_enable else R.string.ui_update_google_play_services_b89d591)) {
                            val intent = com.google.android.gms.common.GoogleApiAvailability.getInstance()
                                .getErrorResolutionIntent(activity,
                                    driveServicesResolutionCode!!,
                                    "ramble-drive")
                            if (intent != null) runCatching { activity.startActivity(intent) }
                                .onFailure { open("https://play.google.com/store/apps/details?id=com.google.android.gms") }
                            else open("https://play.google.com/store/apps/details?id=com.google.android.gms")
                        }
                    }
                    Action(appText(R.string.ui_connect_google_drive_f4d37b8), !busy,
                        secondary = driveServicesResolutionCode != null) {
                        connectDrive()
                    }
                    Action(appText(R.string.ui_save_only_on_this_phone_06718d7), secondary = true) {
                        if (isSetup) go(7) else screen = "settings"
                    }
                }
            }
            "drive-folders" -> {
                Action(appText(R.string.ui_create_and_use_folder_48d62db), !busy && driveFolderDraft.isNotBlank()) {
                    task {
                        val api = DriveExport(app, settings)
                        val token = api.token(refreshFolder = false)
                        val folder = api.createFolder(token, driveFolderDraft)
                        api.selectFolder(token, folder.id)
                        withContext(Dispatchers.Main) { screen = "drive" }
                        Schedule.pump(app)
                    }
                }
                Action(appText(R.string.ui_cancel_1fa68c8), secondary = true) { screen = "drive" }
            }
            "6",
            "agent" -> {
                if (!settings.driveEnabled || settings.driveFolder.isBlank()) {
                    Action(appText(R.string.ui_connect_google_drive_f4d37b8)) { screen = "drive" }
                } else {
                    Button(
                        onClick = { if (isSetup) go(7) else screen = "settings" },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background),
                    ) {
                        Text(if (isSetup) appText(R.string.ui_first_recording_e0228ab)
                            else appText(R.string.ui_done_ae30c54), fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
                    }
                    if (!isSetup) Action(appText(R.string.ui_open_folder_in_drive_141acb3), secondary = true) {
                        open("https://drive.google.com/drive/folders/${settings.driveFolder}")
                    }
                }
            }
            "detail" -> {
                val job = jobs.find { it.id == selected?.id } ?: selected
                if (job?.state == "saved") {
                    Action(appText(R.string.ui_copy_text_04d0d96)) {
                        (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                            .setPrimaryClip(
                                ClipData.newPlainText(
                                    "Transcript",
                                    LocalExport(app, app.database).file(job.id).readText(),
                                )
                            )
                        scope.launch { snackbar.showSnackbar(appText(R.string.copied_message)) }
                    }
                    Action(appText(R.string.ui_share_24de8ee), secondary = true) {
                        val uri =
                            FileProvider.getUriForFile(
                                context,
                                context.packageName + ".files",
                                LocalExport(app, app.database).file(job.id),
                            )
                        context.startActivity(
                            Intent.createChooser(
                                Intent(Intent.ACTION_SEND)
                                    .setType("text/plain")
                                    .putExtra(Intent.EXTRA_STREAM, uri)
                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION),
                                null,
                            )
                        )
                    }
                } else if (job?.reason in setOf("audio_budget","unsupported_duration","unsupported_size","unsupported_audio","unsupported_pcm","multiple_tracks","audio_timestamp_gap")) {
                    Action(appText(R.string.ui_go_to_home_10d9f47)) { screen="home" }
                } else if (job?.reason == "interrupted_audio") {
                    Action(appText(R.string.ui_recover_and_transcribe_ac94b26), !busy) {
                        task { AppRecordings(app).resumeInterrupted(job);Schedule.pump(app)
                            withContext(Dispatchers.Main) { screen="home" } }
                    }
                } else if (job?.state == "key") {
                    Action(appText(R.string.ui_fix_api_key_332dc00)) {
                        candidate = job.provider.substringBefore("@")
                        screen = "key"
                    }
                } else if (job?.state in setOf("uncertain", "error", "rate", "storage") && job != null) {
                    Action(appText(R.string.ui_retry_transcription_a577582), !busy) {
                        task {
                            if (job.state == "uncertain")
                                app.database
                                    .queue()
                                    .chunks(job.id)
                                    .filter { it.state == "inflight" }
                                    .forEach {
                                        app.database.queue().chunk(it.copy(state = "ready"))
                                    }
                            app.database
                                .queue()
                                .recording(
                                    job.copy(
                                        state = "queued",
                                        reason = "",
                                        nextAttempt = 0,
                                        localTarget =
                                            if (job.hash.isBlank())
                                                settings.outputTree.ifBlank { "downloads" }
                                            else job.localTarget,
                                    )
                                )
                            Schedule.pump(app)
                            withContext(Dispatchers.Main) { screen = "home" }
                        }
                    }
                    Action(appText(R.string.ui_skip_recording_723f296), secondary = true) {
                        task {
                            app.database.queue().recording(job.copy(state = "skipped", reason = ""))
                            withContext(Dispatchers.Main) { screen = "home" }
                        }
                    }
                }
                if (job!=null) Action(appText(R.string.ui_delete_recording_c62182e), !busy, secondary=true) { deleteCandidate=job }
            }
        }
    }
    deleteCandidate?.let { candidate ->
        AlertDialog(onDismissRequest={deleteCandidate=null},
            title={Text(appText(R.string.ui_delete_this_recording_49aa6f3))},
            text={Text(appText(R.string.ui_ramble_s_private_audio_and_transcript_will_be_19e729b))},
            confirmButton={TextButton(onClick={deleteCandidate=null;task {
                check(AudioRetention(app).remove(candidate)) { "recording_busy" }
                withContext(Dispatchers.Main) { selected=null;screen="home" }
            }}) { Text(appText(R.string.ui_delete_4d8629a)) }},
            dismissButton={TextButton(onClick={deleteCandidate=null}) { Text(appText(R.string.ui_cancel_1fa68c8)) }})
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(if (screen == "0") 14.dp else 10.dp),
                        ) {
                            Image(
                                painterResource(R.drawable.ramble_logo),
                                null,
                                Modifier.size(if (screen == "0") 52.dp else 28.dp),
                            )
                            Text(screenTitle, fontSize = if (screen == "0") 32.sp else 20.sp, fontWeight = FontWeight.Bold)
                        }
                    },
                    navigationIcon = {
                        if (screen != "home" && screen != "0")
                            IconButton(onClick = { back() }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, appText(R.string.ui_back_f855790))
                            }
                    },
                    actions = {
                        if (screen == "home")
                            IconButton(onClick = { screen = "settings" }) {
                                Icon(Icons.Outlined.Settings, appText(R.string.ui_settings_7e3c1bd))
                            }
                    },
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background
                        ),
                )
                if (isSetup && screen != "0" && screen != "9") {
                    Column(
                        Modifier.padding(start = 20.dp, end = 20.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        val stage =
                            when (screen) {
                                "1",
                                "2" -> 1
                                "3",
                                "4" -> 2
                                "5",
                                "6" -> 3
                                else -> 4
                            }
                        Text(
                            appText(R.string.progress_step,stage) +
                                " · " +
                                when (stage) {
                                    1 -> appText(R.string.ui_transcription_provider_fc1dfb9)
                                    2 -> appText(R.string.ui_recording_access_48f80c4)
                                    3 -> appText(R.string.ui_saving_75cb326)
                                    else ->
                                        if (screen == "7") appText(R.string.ui_first_recording_53935cd)
                                        else appText(R.string.ui_quick_recording_462793f)
                                },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 15.sp,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            repeat(4) {
                                Box(
                                    Modifier.weight(1f)
                                        .height(4.dp)
                                        .background(
                                            if (it < stage) MaterialTheme.colorScheme.secondary
                                            else
                                                MaterialTheme.colorScheme.outline.copy(alpha = .3f),
                                            RoundedCornerShape(2.dp),
                                        )
                                )
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (hasFooter) {
                Surface(color = MaterialTheme.colorScheme.background) {
                    Column(
                        Modifier.fillMaxWidth()
                            .navigationBarsPadding()
                            .imePadding()
                            .testTag("page-actions")
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        pageActions()
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { insets ->
        if (screen == "0") {
            WelcomePage(Modifier.padding(insets)) { go(1) }
            return@Scaffold
        }
        if (screen == "home") {
            RecordingsHome(
                jobs = jobs, summaries = summaries, paused = settings.paused,
                accessError = settings.error.isNotBlank(), modifier = Modifier.padding(insets),
                onOpen = { selected = it; screen = "detail" },
                onRecord = { context.startActivity(Intent(context, InternalRecordActivity::class.java).putExtra("fromApp", true)) },
                onRefresh = { Schedule.scan(app) },
                onRepair = { screen = "sources" },
                onTranscriptionSettings = { screen = "settings" },
            )
            return@Scaffold
        }
        Column(
            Modifier.fillMaxSize()
                .padding(insets)
                .imePadding()
                .padding(horizontal = 20.dp)
                .verticalScroll(contentScroll),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (busy) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (notice.isNotBlank()) Panel(appText(R.string.ui_let_s_try_again_0cd5374), notice)
            when (screen) {
                "1",
                "provider" -> {
                    ProviderSelectionPage(candidate, onProvider={candidate=it;openProviderKey()}, onInfo={open(it)})
                }
                "2",
                "key" -> {
                    Text(
                        appText(R.string.provider_key_title,if(candidate=="groq") "Groq" else "OpenAI"),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (selected?.state == "key")
                        Text(
                            appText(R.string.ui_the_new_key_will_be_used_for_the_remaining_un_84cb7a6)
                        )
                    if (settings.hasKey(candidate))
                        Panel(
                            appText(R.string.ui_a_key_is_already_saved_54f2514),
                            appText(R.string.ui_keep_it_or_enter_a_replacement_52dbe36),
                        )
                    Text(
                        appText(R.string.ui_your_key_is_encrypted_on_this_phone_audio_goe_b1aecf2)
                    )
                    OutlinedTextField(
                        key,
                        {},
                        Modifier.fillMaxWidth(),
                        readOnly = true,
                        singleLine = true,
                        label = { Text(appText(R.string.api_key_label)) },
                        placeholder = { Text(appText(R.string.ui_tap_paste_922f9f6)) },
                        textStyle =
                            MaterialTheme.typography.bodyLarge.copy(
                                textDirection = TextDirection.Ltr
                            ),
                        visualTransformation = PasswordVisualTransformation(),
                        shape = RoundedCornerShape(16.dp),
                        trailingIcon = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                            if (key.isNotEmpty()) IconButton(enabled = !keyDraftSaving && !busy, onClick = {
                                val provider = candidate
                                key = ""
                                keyCheck = "idle"
                                verifiedKey = null
                                notice = ""
                                keyDraftSaving = true
                                scope.launch {
                                    try { withContext(Dispatchers.IO) { settings.clearKeyDraft(provider) } }
                                    finally { keyDraftSaving = false }
                                }
                            }) { Icon(Icons.Outlined.Close, appText(R.string.clear_api_key)) }
                            TextButton(
                                enabled = !keyDraftSaving && !busy,
                                onClick = {
                                    val clipboard =
                                        context.getSystemService(Context.CLIPBOARD_SERVICE)
                                            as ClipboardManager
                                    val pasted =
                                        clipboard.primaryClip
                                            ?.getItemAt(0)
                                            ?.coerceToText(context)
                                            ?.toString()
                                    if (!pasted.isNullOrBlank()) {
                                        val draft = pasted.trim()
                                        val provider = candidate
                                        keyDraftSaving = true
                                        scope.launch {
                                            try { runCatching {
                                                withContext(Dispatchers.IO) { settings.saveKeyDraft(provider, draft) }
                                            }.onSuccess { if (candidate == provider) key = draft }
                                                .onFailure {
                                                    notice = appText(R.string.ui_couldn_t_save_your_key_try_pasting_again_ea558f6)
                                                } } finally { keyDraftSaving = false }
                                        }
                                    }
                                }
                            ) {
                                Text(
                                    appText(R.string.ui_paste_7e01771),
                                    color = MaterialTheme.colorScheme.secondary,
                                )
                            }
                            }
                        },
                    )
                    when (keyCheck) {
                        "checking" -> Text(appText(R.string.ui_checking_your_key_4299568))
                        "ok" ->
                            Panel(
                                appText(R.string.ui_your_key_works_6cbf015),
                                appText(R.string.ui_you_re_ready_to_continue_2e2387e),
                            )
                        "invalid" ->
                            Panel(
                                appText(R.string.ui_this_key_didn_t_work_6ac29b0),
                                appText(R.string.ui_check_that_you_copied_the_entire_key_then_pas_d51cdb3),
                            )
                        "offline" ->
                            Panel(
                                appText(R.string.ui_couldn_t_check_your_key_ce55d0b),
                                appText(R.string.ui_your_key_is_saved_securely_awaiting_verificat_47cbc43),
                            )
                    }
                }
                "help" -> {
                    Text(
                        appText(R.string.ui_get_a_key_in_a_few_steps_96b0d58),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        appText(R.string.ui_1_open_the_provider_website_and_create_an_acc_2c53c0e)
                    )
                }
                "3" -> {
                    RecordingAccessPage(device,setupVoiceAccess,setupCallAccess,customFolderHasAudio,sources.any { it.enabled }) { folderManagement(true) }
                }
                "5",
                "drive" -> {
                    Text(
                        if (settings.driveEnabled) { if (isSetup) appText(R.string.ui_google_drive_connected_b1ae97e) else "Google Drive" }
                        else appText(R.string.ui_connect_google_drive_f4d37b8),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    if (settings.driveEnabled) {
                        Row(verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Outlined.CheckCircle, null, tint = MaterialTheme.colorScheme.secondary)
                            Text(appText(R.string.ui_connected_f479903), color = MaterialTheme.colorScheme.secondary)
                        }
                        Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(appText(R.string.ui_transcript_folder_ca727d0),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                                TextButton(onClick = { open("https://drive.google.com/drive/folders/${settings.driveFolder}") },
                                    contentPadding = PaddingValues(0.dp)) {
                                    Icon(Icons.Outlined.Folder, null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(settings.driveFolderName, style = MaterialTheme.typography.titleLarge)
                                    Spacer(Modifier.width(8.dp))
                                    Icon(Icons.Outlined.OpenInNew, null, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                        Text(appText(R.string.ui_new_transcripts_are_saved_here_b1de520))
                    } else {
                        Text(
                            appText(R.string.ui_connect_drive_so_your_ai_assistant_can_find_y_95473ec)
                        )
                    }
                }
                "drive-folders" -> {
                    Text(appText(R.string.ui_choose_a_drive_folder_75ff22b), style = MaterialTheme.typography.headlineLarge)
                    Text(appText(R.string.ui_folders_available_to_ramble_you_can_also_crea_048b465))
                    Text(appText(R.string.ui_existing_files_stay_where_they_are_0c152d1),
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (busy) CircularProgressIndicator()
                    driveChoices.forEach { folder ->
                        RowLink(folder.name, if (folder.id == settings.driveFolder) appText(R.string.ui_selected_0b1ba11) else "") {
                            if (!busy) useDriveFolder(folder.id)
                        }
                    }
                    OutlinedTextField(value = driveFolderDraft, onValueChange = { driveFolderDraft = it.take(100) },
                        label = { Text(appText(R.string.ui_new_folder_name_a0989df)) }, singleLine = true,
                        modifier = Modifier.fillMaxWidth())
                    if (!busy && driveChoices.isEmpty()) TextButton(onClick = { loadDriveFolders() }) {
                        Text(appText(R.string.ui_reload_folders_fe8446c))
                    }
                }
                "6",
                "agent" -> {
                    AgentInstructionsPage(
                        english = promptEnglish,
                        text = if (settings.driveFolder.isNotBlank()) agentText() else null,
                        copied = promptCopied,
                        onLanguage = {
                            promptEnglish = it
                            settings.promptEnglish = it
                            promptCopied = false
                        },
                        onCopy = {
                            (context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager)
                                .setPrimaryClip(ClipData.newPlainText("Ramble", agentText()))
                            promptCopied = true
                        },
                    )
                }
                "7",
                "trial" -> {
                    FirstRecordingPage(
                        app,
                        trialCapture,
                        trialJob,
                        sources.any { it.enabled && it.kind in readableSources },
                    ) {
                        selected = trialJob
                        screen = "detail"
                    }
                }
                "recording-live" -> {
                    val capture by QuickRecordService.status.collectAsState()
                    Text(appText(R.string.ui_background_recording_ad4afe5), style = MaterialTheme.typography.headlineLarge)
                    RecordingPill(capture) {
                        context.startService(Intent(context, QuickRecordService::class.java).setAction(QuickRecordService.STOP))
                    }
                    Text(if(capture.mode == "recording") appText(R.string.ui_you_can_switch_apps_or_lock_your_phone_6147ef6)
                        else if(capture.mode == "saved") appText(R.string.ui_recording_saved_your_transcript_will_appear_h_a1fab96)
                        else appText(R.string.ui_message_6e340b9))
                }
                "8",
                "guide" -> {
                    QuickRecordingGuide(device)
                }
                "quickcheck" -> {
                    Text(
                        appText(R.string.ui_check_that_it_works_7b63b41),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    SideButtonArt()
                    Text(
                        appText(R.string.ui_quickly_press_the_side_button_twice_if_your_r_0e2166c)
                    )
                    Text(
                        appText(R.string.ui_didn_t_work_open_settings_again_or_record_as__04a5ffd),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                "9" -> {
                    Icon(
                        Icons.Outlined.CheckCircle,
                        null,
                        Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                    Text(
                        appText(R.string.ui_you_re_all_set_95bc22a),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        appText(R.string.ui_you_can_close_the_app_and_record_as_usual_new_6ffccb2)
                    )
                    Panel(
                        appText(R.string.ui_saved_on_your_phone_67c68af),
                        if (settings.outputTree.isBlank()) "Download/Ramble"
                        else appText(R.string.ui_your_chosen_folder_c4e0e6f),
                    )
                    if (settings.driveEnabled)
                        Panel(
                            appText(R.string.ui_also_saved_in_google_drive_a4c4b6c),
                            settings.driveFolderName,
                        )
                    Panel(
                        appText(R.string.ui_transcription_provider_c2ef7e2),
                        if (settings.provider == "groq") "Groq" else "OpenAI",
                    )
                }
                "detail" -> {
                    TranscriptDetailPage(app,jobs.find { it.id==selected?.id } ?: selected) { open(it) }
                }
                "settings" -> {
                    AppSettingsPage(activity,settings,
                        onAutoTranscription={settings.paused=!it;revision++;if(it)Schedule.scan(app)},
                        onKeepAudio={settings.keepAudio=it;revision++;app.maintainPrivateAudio()},
                        onClearAudio={task { val count=AudioRetention(app).clean(forceCompleted=true)
                            withContext(Dispatchers.Main) { snackbar.showSnackbar(appText(R.string.cleared_audio_count,count)) } }},
                        onWifiOnly={settings.wifiOnly=it;revision++;Schedule.scan(app)},
                        onNavigate={route -> if(route=="provider")candidate=settings.provider
                            if(route=="trial") {trialRecorder.reset();revision++};screen=route})
                }
                "license" -> {
                    LicensePage(context)
                }
                "output" -> {
                    Text(
                        appText(R.string.ui_where_to_save_new_transcripts_a2c239c),
                        style = MaterialTheme.typography.headlineLarge,
                    )
                    Text(
                        appText(R.string.ui_this_applies_to_new_recordings_existing_trans_a8fc2c4)
                    )
                }
                "sources" -> {
                    val visible = sources.filter { it.tree !in settings.removedSourceTrees && it.baselineComplete }
                    RecordingSourcesPage(
                        voiceOn = visible.any { !it.isCallRecordingSource() && it.enabled },
                        callsOn = visible.any { it.isCallRecordingSource() && it.enabled },
                        busy = busy, folders = connectedFolders,
                        onToggle = { kind, enabled ->
                            task {
                                val changed = SourceReader(app, app.database, settings).setCategoryEnabled(kind, enabled)
                                withContext(Dispatchers.Main) {
                                    if (!changed) {
                                        replacingFolderTree = null
                                        chooseSource(kind, repairAccess = true)
                                    }
                                }
                                if (changed && enabled) Schedule.scan(app)
                            }
                        },
                        onAdd = addRecordingFolder, onChange = changeRecordingFolder,
                        onRemove = removeRecordingFolder,
                    )
                }
            }
            Spacer(Modifier.height(24.dp))
            // Read revision to refresh private preference changes without saving secrets in Compose
            // state.
            if (revision < 0) Text("")
        }
    }
}

