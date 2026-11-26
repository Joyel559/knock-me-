package com.joyel.tripguard.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavController
import com.joyel.tripguard.BuildConfig
import com.joyel.tripguard.geo.LatLon
import com.joyel.tripguard.geo.haversineMeters
import com.joyel.tripguard.history.HistoryRepository
import com.joyel.tripguard.history.MemoryMirror
import com.joyel.tripguard.location.LocationRepositoryHolder
import com.joyel.tripguard.location.LocationState
import com.joyel.tripguard.routing.OsrmRoutingProvider
import com.joyel.tripguard.search.NominatimClient
import com.joyel.tripguard.search.PlaceResult
import com.joyel.tripguard.search.PlaceSearchRepository
import com.joyel.tripguard.service.ACTION_START
import com.joyel.tripguard.service.TripService
import com.joyel.tripguard.share.LinkDebugLog
import com.joyel.tripguard.share.OfflineParse
import com.joyel.tripguard.share.Place
import com.joyel.tripguard.share.PendingPick
import com.joyel.tripguard.share.PendingPickRepository
import com.joyel.tripguard.share.PickTarget
import com.joyel.tripguard.share.PlaceInputResolver
import com.joyel.tripguard.share.ResolveState
import com.joyel.tripguard.share.RoomLinkCache
import com.joyel.tripguard.share.SharedDestinationHolder
import com.joyel.tripguard.share.TripSetupDraft
import com.joyel.tripguard.share.looksLikePlaceText
import com.joyel.tripguard.share.openGoogleMapsPicker
import com.joyel.tripguard.share.openUrlExternally
import com.joyel.tripguard.share.viewLocationInGoogleMaps
import com.joyel.tripguard.trip.Mode
import com.joyel.tripguard.trip.SerLatLon
import com.joyel.tripguard.trip.SettingsRepository
import com.joyel.tripguard.trip.Trip
import com.joyel.tripguard.trip.TripPrefillHolder
import com.joyel.tripguard.trip.TripRepository
import com.joyel.tripguard.trip.TripState
import com.joyel.tripguard.ui.Routes
import com.joyel.tripguard.ui.components.KnockCustomDistanceDialog
import com.joyel.tripguard.ui.components.KnockDistanceChipsRow
import com.joyel.tripguard.ui.components.formatMeters
import com.joyel.tripguard.ui.permissions.PermCheckId
import com.joyel.tripguard.ui.permissions.buildPermissionChecklist
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.UUID

private val MAIN_RADIUS_PRESETS_M = listOf(1000, 2000, 3000, 5000)
private val FINAL_ALARM_PRESETS_M = listOf(100, 200, 300, 500)
private const val DEFAULT_CENTER_LAT = 12.9716
private const val DEFAULT_CENTER_LON = 77.5946

enum class EditingField { FROM, TO }

@Composable
fun TripSetupScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val nominatim = remember { NominatimClient() }
    val placeSearchRepo = remember { PlaceSearchRepository(context) }
    val pendingPickRepo = remember { PendingPickRepository(context) }
    val linkCache = remember { RoomLinkCache(context) }

    var fromLocation by remember { mutableStateOf<LatLon?>(null) }
    var fromLabel by remember { mutableStateOf("Current location") }
    var fromQuery by remember { mutableStateOf("") }
    var fromPlace by remember { mutableStateOf<Place?>(null) }
    var fromReading by remember { mutableStateOf(false) }
    var fromFailed by remember { mutableStateOf(false) }
    var fromFailedText by remember { mutableStateOf<String?>(null) }

    var destination by remember { mutableStateOf<LatLon?>(null) }
    var destLabel by remember { mutableStateOf("") }
    var toQuery by remember { mutableStateOf("") }
    var destPlace by remember { mutableStateOf<Place?>(null) }
    var toFilledViaDefaultShare by remember { mutableStateOf(false) }
    var toReading by remember { mutableStateOf(false) }
    var toFailed by remember { mutableStateOf(false) }
    var toFailedText by remember { mutableStateOf<String?>(null) }

    var activeEditingField by remember { mutableStateOf<EditingField?>(null) }
    var searchResults by remember { mutableStateOf<List<PlaceResult>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchFailed by remember { mutableStateOf(false) }

    var radiusMeters by remember { mutableIntStateOf(3000) }
    var mode by remember { mutableStateOf(Mode.CAB) }
    var finalAlarmEnabled by remember { mutableStateOf(false) }
    var finalAlarmMeters by remember { mutableIntStateOf(com.joyel.tripguard.trip.DEFAULT_FINAL_ALARM_RADIUS_M) }
    var showCustomFinalDialog by remember { mutableStateOf(false) }
    var pasteBusy by remember { mutableStateOf(false) }

    var errorText by remember { mutableStateOf<String?>(null) }
    var showAlreadyInsideDialog by remember { mutableStateOf(false) }
    var starting by remember { mutableStateOf(false) }

    var showSavedPlacesDropdown by remember { mutableStateOf(false) }
    var savedPlaceTargetField by remember { mutableStateOf(EditingField.TO) }
    var showCustomRadiusDialog by remember { mutableStateOf(false) }
    var showSavePlaceDialog by remember { mutableStateOf(false) }
    var showFullScreenIntentAsk by remember { mutableStateOf(false) }
    var pendingStartDest by remember { mutableStateOf<LatLon?>(null) }
    var savePlaceNameText by remember { mutableStateOf("") }

    var showMapsHintDialog by remember { mutableStateOf(false) }
    var showClipboardOfferSheet by remember { mutableStateOf(false) }
    var clipboardOfferText by remember { mutableStateOf<String?>(null) }
    var clipboardOfferTarget by remember { mutableStateOf(PickTarget.TO) }
    var lastClipboardOfferedText by remember { mutableStateOf<String?>(null) }

    var settings by remember { mutableStateOf(com.joyel.tripguard.trip.AppSettings()) }
    var showLinksNotice by remember { mutableStateOf(false) }

    val currentReadyLatLon0 = (LocationRepositoryHolder.get(context).state.value as? LocationState.Ready)?.let { LatLon(it.lat, it.lon) }

    fun clearFrom() {
        fromLocation = null
        fromLabel = "Current location"
        fromQuery = ""
        fromPlace = null
        fromFailed = false
    }

    fun clearTo() {
        destination = null
        destLabel = ""
        toQuery = ""
        destPlace = null
        toFilledViaDefaultShare = false
        toFailed = false
    }

    fun applyPlaceTo(target: PickTarget, place: Place) {
        if (target == PickTarget.FROM) {
            fromLocation = place.location
            fromLabel = place.name
            fromQuery = place.name
            fromPlace = place
            fromFailed = false
            com.joyel.tripguard.share.TripSetupTestState.lastAppliedFrom = place
        } else {
            destination = place.location
            destLabel = place.name
            toQuery = place.name
            destPlace = place
            toFilledViaDefaultShare = false
            toFailed = false
            com.joyel.tripguard.share.TripSetupTestState.lastAppliedTo = place
        }
    }

    /** The single entry point (v2.0) for share/paste/clipboard/typed-link input: every call site
     * below funnels through this instead of parsing text itself. */
    fun resolveInto(target: PickTarget, text: String, onFound: (Place) -> Unit = {}) {
        if (text.isBlank()) return
        scope.launch {
            val near = currentReadyLatLon0
            val cc = runCatching { SettingsRepository(context).get().searchCountryCode }.getOrNull()
            PlaceInputResolver(
                linkCache = linkCache,
                onlineResolverEnabled = {
                    runCatching { SettingsRepository(context).get().useOnlineLinkResolver }.getOrDefault(true)
                },
                debugLog = { if (BuildConfig.DEBUG) LinkDebugLog.record(it) }
            ).resolve(text, near = near, countryCode = cc).collect { state ->
                when (state) {
                    is ResolveState.Reading -> {
                        if (target == PickTarget.FROM) {
                            fromReading = true; fromFailed = false
                            if (!state.name.isNullOrBlank()) fromQuery = state.name
                        } else {
                            toReading = true; toFailed = false
                            if (!state.name.isNullOrBlank()) toQuery = state.name
                        }
                    }
                    is ResolveState.Found -> {
                        applyPlaceTo(target, state.place)
                        if (target == PickTarget.FROM) fromReading = false else toReading = false
                        onFound(state.place)
                    }
                    ResolveState.Failed -> {
                        if (target == PickTarget.FROM) {
                            fromReading = false; fromFailed = true; fromFailedText = text
                        } else {
                            toReading = false; toFailed = true; toFailedText = text
                        }
                    }
                }
            }
        }
    }

    fun buildDraft(): TripSetupDraft = TripSetupDraft(
        fromLat = fromLocation?.lat,
        fromLon = fromLocation?.lon,
        fromLabel = fromLabel,
        toLat = destination?.lat,
        toLon = destination?.lon,
        toLabel = destLabel,
        radiusMeters = radiusMeters,
        mode = mode,
        finalAlarmEnabled = finalAlarmEnabled,
        finalAlarmMeters = finalAlarmMeters
    )

    fun applyDraft(d: TripSetupDraft) {
        if (d.fromLat != null && d.fromLon != null) {
            fromLocation = LatLon(d.fromLat, d.fromLon)
        }
        fromLabel = d.fromLabel
        fromQuery = if (d.fromLabel == "Current location") "" else d.fromLabel
        if (d.toLat != null && d.toLon != null) {
            destination = LatLon(d.toLat, d.toLon)
            destLabel = d.toLabel
            toQuery = d.toLabel
        }
        radiusMeters = d.radiusMeters
        mode = d.mode
        finalAlarmEnabled = d.finalAlarmEnabled
        finalAlarmMeters = d.finalAlarmMeters
    }

    fun startChooseInMaps(target: PickTarget) {
        scope.launch {
            pendingPickRepo.set(PendingPick(target, System.currentTimeMillis(), buildDraft()))
            val s = SettingsRepository(context).get()
            if (s.mapsPickerHintDismissed) {
                openGoogleMapsPicker(context)
            } else {
                showMapsHintDialog = true
            }
        }
    }

    fun pasteInto(target: PickTarget) {
        scope.launch {
            val cm = context.getSystemService(android.content.ClipboardManager::class.java)
            val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
            if (clip.isNullOrBlank()) {
                errorText = "Nothing copied yet"
                return@launch
            }
            pasteBusy = true
            resolveInto(target, clip)
            pasteBusy = false
        }
    }

    val locationRepo = remember { LocationRepositoryHolder.get(context) }
    val locationState by locationRepo.state.collectAsState()
    LaunchedEffect(Unit) { locationRepo.start() }
    DisposableEffect(Unit) { onDispose { locationRepo.stop() } }

    val historyRepo = remember { HistoryRepository(context) }
    val savedPlaces by historyRepo.observeSavedPlaces().collectAsState(initial = emptyList())
    val recentTrips by historyRepo.observeRecentTrips(10).collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        TripPrefillHolder.pending?.let { prefill ->
            destination = prefill.location
            destLabel = prefill.label
            toQuery = prefill.label
            mode = prefill.mode
            radiusMeters = prefill.radiusMeters
        }
        TripPrefillHolder.pending = null
    }

    // Restore the draft (and clear the pick) from a prior "Choose in Google Maps" trip, if this
    // Trip Setup instance is a fresh one created to receive the resulting share (see MainActivity).
    LaunchedEffect(Unit) {
        pendingPickRepo.consume()?.draft?.let { applyDraft(it) }
    }

    // Single entry point for an incoming share (cold start or onNewIntent): resolve it exactly
    // the same way Paste/clipboard-offer do.
    LaunchedEffect(Unit) {
        val rawText = SharedDestinationHolder.rawText
        val target = SharedDestinationHolder.target
        val wasDefault = SharedDestinationHolder.targetWasDefault
        SharedDestinationHolder.rawText = null
        SharedDestinationHolder.target = PickTarget.TO
        SharedDestinationHolder.targetWasDefault = false
        if (rawText != null) {
            resolveInto(target, rawText) {
                if (target == PickTarget.TO && wasDefault) toFilledViaDefaultShare = true
            }
        }
    }

    LaunchedEffect(Unit) {
        val settingsRepo = SettingsRepository(context)
        val defaults = settingsRepo.get()
        settings = defaults
        showLinksNotice = !defaults.linksNoticeShown
        radiusMeters = defaults.defaultRadiusMeters
        mode = defaults.defaultMode
        finalAlarmEnabled = defaults.defaultFinalAlarmEnabled
        finalAlarmMeters = defaults.defaultFinalAlarmMeters

        if (defaults.searchCountryCode == null) {
            val fix = withTimeoutOrNull(10_000) { locationRepo.state.first { it is LocationState.Ready } } as? LocationState.Ready
            if (fix != null) {
                val cc = nominatim.reverseCountryCode(fix.lat, fix.lon)
                if (cc != null) settingsRepo.update { it.copy(searchCountryCode = cc) }
            }
        }
    }

    // Warm-up (v2.0): a cheap HEAD to the Photon host, and to the unshortener host when the
    // online switch is on, so the first real resolve isn't paying a cold-connection cost too.
    LaunchedEffect(Unit) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            runCatching {
                okhttp3.OkHttpClient().newCall(
                    okhttp3.Request.Builder().url(BuildConfig.PHOTON_BASE_URL).head().build()
                ).execute().close()
            }
            val s = runCatching { SettingsRepository(context).get() }.getOrNull()
            if (s?.useOnlineLinkResolver == true) {
                runCatching {
                    okhttp3.OkHttpClient().newCall(
                        okhttp3.Request.Builder().url(BuildConfig.UNSHORTENER_BASE_URL).head().build()
                    ).execute().close()
                }
            }
        }
    }

    // Final alarm must stay smaller than the main radius; auto-correct rather than let it stick.
    LaunchedEffect(radiusMeters, finalAlarmMeters) {
        if (finalAlarmMeters >= radiusMeters) {
            finalAlarmMeters = (radiusMeters / 2).coerceAtLeast(50)
        }
    }

    // Clipboard fallback: only while a pick is pending, and only once per regained focus.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                scope.launch {
                    val pick = pendingPickRepo.peek() ?: return@launch
                    val cm = context.getSystemService(android.content.ClipboardManager::class.java)
                    val clip = cm?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.text?.toString()
                    if (com.joyel.tripguard.share.shouldOfferClipboard(true, clip, lastClipboardOfferedText)) {
                        clipboardOfferText = clip
                        clipboardOfferTarget = pick.target
                        showClipboardOfferSheet = true
                    }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentReadyLatLon = (locationState as? LocationState.Ready)?.let { LatLon(it.lat, it.lon) }
    val effectiveFrom = fromLocation ?: currentReadyLatLon ?: LatLon(DEFAULT_CENTER_LAT, DEFAULT_CENTER_LON)

    // Debounced search: min 3 chars, 600ms debounce, previous request cancelled by the key change.
    LaunchedEffect(activeEditingField, fromQuery, toQuery) {
        val field = activeEditingField ?: return@LaunchedEffect
        val q = if (field == EditingField.FROM) fromQuery else toQuery
        if (q.isBlank()) {
            searchResults = placeSearchRepo.browseLocal()
            searching = false
            searchFailed = false
            return@LaunchedEffect
        }
        if (q.trim().length < 3) {
            searchResults = placeSearchRepo.searchLocal(q)
            searching = false
            searchFailed = false
            return@LaunchedEffect
        }
        searching = true
        searchFailed = false
        kotlinx.coroutines.delay(600)
        val s = runCatching { SettingsRepository(context).get() }.getOrNull()
        val results = placeSearchRepo.search(q, near = currentReadyLatLon, countryCode = s?.searchCountryCode)
        searching = false
        searchResults = results
        searchFailed = results.isEmpty()
    }

    fun effectiveRadiusMeters(): Int = radiusMeters

    fun proceedWithStart(dest: LatLon, skipInsideCheck: Boolean) {
        starting = true
        scope.launch {
            locationRepo.start()
            val fix = withTimeoutOrNull(20_000) {
                locationRepo.state.first { it is LocationState.Ready } as LocationState.Ready
            }
            if (fix == null) {
                errorText = "Still finding your location, try again in a moment"
                starting = false
                return@launch
            }
            val currentLatLon = LatLon(fix.lat, fix.lon)
            val radiusMeters = effectiveRadiusMeters()

            if (!skipInsideCheck && haversineMeters(currentLatLon, dest) <= radiusMeters) {
                starting = false
                showAlreadyInsideDialog = true
                return@launch
            }

            var route: List<LatLon>? = null
            if (mode == Mode.CAB) {
                route = OsrmRoutingProvider().route(effectiveFrom, dest)
                if (route == null) {
                    errorText = "Route not available, you will still get the arrival alarm"
                }
            }

            val trip = Trip(
                id = UUID.randomUUID().toString(),
                destLat = dest.lat,
                destLon = dest.lon,
                destLabel = destLabel.ifBlank { "Destination" },
                radiusMeters = radiusMeters,
                mode = mode,
                finalAlarmEnabled = finalAlarmEnabled,
                finalAlarmMeters = finalAlarmMeters,
                route = route?.map { SerLatLon.from(it) },
                state = TripState.ACTIVE,
                startedAt = System.currentTimeMillis(),
                fromLat = fromLocation?.lat,
                fromLon = fromLocation?.lon,
                fromLabel = fromLabel
            )
            TripRepository(context).saveActiveTrip(trip)
            context.startForegroundService(Intent(context, TripService::class.java).setAction(ACTION_START))
            MemoryMirror.requestWrite(context)
            starting = false
            navController.navigate(Routes.ACTIVE) { popUpTo(Routes.HOME) }
        }
    }

    fun startTrip(skipInsideCheck: Boolean) {
        val dest = destination
        if (dest == null) {
            errorText = "Set destination To first"
            return
        }
        val checklist = buildPermissionChecklist(context)
        val missingCritical = checklist.filter {
            it.id == PermCheckId.FINE_LOCATION || it.id == PermCheckId.NOTIFICATIONS
        }.any { !it.granted }
        if (missingCritical) {
            navController.navigate(Routes.ONBOARDING)
            return
        }
        if (!checklist.first { it.id == PermCheckId.GPS_ENABLED }.granted) {
            errorText = "Turn on GPS, then try again"
            return
        }

        starting = true
        scope.launch {
            // Lean mode (v1.5): ask for the full-screen alarm permission at most once, only when
            // the OS actually requires it, right before the first trip — never during onboarding.
            if (android.os.Build.VERSION.SDK_INT >= 34 &&
                !com.joyel.tripguard.ui.permissions.canUseFullScreenIntent(context)
            ) {
                val onboardingRepo = com.joyel.tripguard.ui.onboarding.OnboardingRepository(context)
                if ("FULL_SCREEN_INTENT" !in onboardingRepo.skippedSteps()) {
                    starting = false
                    pendingStartDest = dest
                    showFullScreenIntentAsk = true
                    return@launch
                }
            }
            proceedWithStart(dest, skipInsideCheck)
        }
    }

    Scaffold(
        containerColor = Color.Black,
        bottomBar = {
            Surface(
                color = Color.Black,
                modifier = Modifier.fillMaxWidth().padding(16.dp)
            ) {
                Button(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    enabled = destination != null && !starting,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White),
                    onClick = { errorText = null; startTrip(skipInsideCheck = false) }
                ) {
                    Text(if (starting) "Starting…" else "Start Trip", color = Color.White)
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("Trip Setup", style = MaterialTheme.typography.titleLarge, color = Color.White, modifier = Modifier.padding(vertical = 8.dp))

            if (showLinksNotice) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "Sends the shared Google Maps link to mapu.retiolus.net to read the place faster. " +
                                "Turn off to keep links on your phone. A short link is also opened directly from your phone.",
                            color = Color.LightGray,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                            TextButton(onClick = {
                                scope.launch {
                                    SettingsRepository(context).update { it.copy(useOnlineLinkResolver = false, linksNoticeShown = true) }
                                }
                                showLinksNotice = false
                            }) { Text("Turn off", color = Color.White) }
                            TextButton(onClick = {
                                scope.launch { SettingsRepository(context).update { it.copy(linksNoticeShown = true) } }
                                showLinksNotice = false
                            }) { Text("OK", color = Color.White) }
                        }
                    }
                }
            }

            // FROM CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = fromQuery,
                        onValueChange = { q ->
                            val previousLength = fromQuery.length
                            fromQuery = q
                            fromLabel = q.ifBlank { "Current location" }
                            fromLocation = null
                            fromPlace = null
                            activeEditingField = EditingField.FROM
                            val pastedChunk = q.length - previousLength >= 15
                            if (pastedChunk && looksLikePlaceText(q)) resolveInto(PickTarget.FROM, q)
                        },
                        placeholder = { Text("Current location", color = Color.Gray) },
                        label = { Text(if (fromReading) "Reading place…" else "From", color = Color.LightGray) },
                        trailingIcon = {
                            if (fromReading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) activeEditingField = EditingField.FROM }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        TextButton(onClick = { clearFrom() }) { Text("My location", color = Color.White) }
                        TextButton(onClick = { startChooseInMaps(PickTarget.FROM) }) { Text("Choose in Google Maps", color = Color.White) }
                        TextButton(onClick = {
                            savedPlaceTargetField = EditingField.FROM
                            showSavedPlacesDropdown = true
                        }) { Text("Saved places", color = Color.White) }
                        TextButton(enabled = !pasteBusy, onClick = { pasteInto(PickTarget.FROM) }) {
                            Text(if (pasteBusy) "Reading…" else "Paste", color = Color.White)
                        }
                    }
                    Text(
                        "Tip: in Google Maps, long-press a spot, tap the coordinates to copy them, then tap Paste.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    PlaceOrFailureCard(
                        place = fromPlace,
                        plainLat = fromLocation?.lat,
                        plainLon = fromLocation?.lon,
                        plainLabel = fromLabel,
                        failed = fromFailed,
                        onChange = { clearFrom(); activeEditingField = EditingField.FROM },
                        onTryAgain = { fromFailedText?.let { resolveInto(PickTarget.FROM, it) } },
                        onViewLink = { fromFailedText?.let { OfflineParse.extractUrl(it) }?.let { openUrlExternally(context, it) } },
                        onPasteCoordinates = { pasteInto(PickTarget.FROM) },
                        onViewInMaps = { fromLocation?.let { viewLocationInGoogleMaps(context, it.lat, it.lon, fromLabel) } }
                    )
                }
            }

            // SWAP BUTTON
            Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.CenterStart) {
                OutlinedButton(
                    enabled = destination != null,
                    onClick = {
                        val dest = destination ?: return@OutlinedButton
                        val oldFromLocation = fromLocation
                        val oldFromLabel = fromQuery.ifBlank { fromLabel }
                        val oldFromPlace = fromPlace
                        val oldDestLabel = destLabel
                        val oldDestPlace = destPlace

                        fromLocation = dest
                        fromLabel = oldDestLabel
                        fromQuery = oldDestLabel
                        fromPlace = oldDestPlace

                        destination = oldFromLocation
                        destLabel = oldFromLabel
                        toQuery = oldFromLabel
                        destPlace = oldFromPlace
                        toFilledViaDefaultShare = false
                    }
                ) {
                    Text("Swap From and To", color = Color.White)
                }
            }

            // TO CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    OutlinedTextField(
                        value = toQuery,
                        onValueChange = { q ->
                            val previousLength = toQuery.length
                            toQuery = q
                            destLabel = q
                            destPlace = null
                            activeEditingField = EditingField.TO
                            toFilledViaDefaultShare = false
                            val pastedChunk = q.length - previousLength >= 15
                            if (pastedChunk && looksLikePlaceText(q)) resolveInto(PickTarget.TO, q)
                        },
                        label = { Text(if (toReading) "Reading place…" else "To (Destination)*", color = Color.LightGray) },
                        trailingIcon = {
                            if (toReading) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = Color.White)
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = Color.White,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { if (it.isFocused) activeEditingField = EditingField.TO }
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp)
                    ) {
                        TextButton(onClick = { startChooseInMaps(PickTarget.TO) }) { Text("Choose in Google Maps", color = Color.White) }
                        TextButton(onClick = {
                            val fix = currentReadyLatLon
                            if (fix != null) {
                                destination = fix
                                destLabel = "Current location"
                                toQuery = "Current location"
                                destPlace = null
                                toFilledViaDefaultShare = false
                            } else {
                                errorText = "Still finding your location, try again in a moment"
                            }
                        }) { Text("My location", color = Color.White) }
                        TextButton(onClick = {
                            savedPlaceTargetField = EditingField.TO
                            showSavedPlacesDropdown = true
                        }) { Text("Saved places", color = Color.White) }
                        TextButton(enabled = !pasteBusy, onClick = { pasteInto(PickTarget.TO) }) {
                            Text(if (pasteBusy) "Reading…" else "Paste", color = Color.White)
                        }
                        TextButton(
                            enabled = destination != null,
                            onClick = { savePlaceNameText = destLabel; showSavePlaceDialog = true }
                        ) { Text("Save", color = Color.White) }
                    }
                    Text(
                        "Tip: in Google Maps, long-press a spot, tap the coordinates to copy them, then tap Paste.",
                        color = Color.Gray,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 2.dp)
                    )

                    PlaceOrFailureCard(
                        place = destPlace,
                        plainLat = destination?.lat,
                        plainLon = destination?.lon,
                        plainLabel = destLabel.ifBlank { "Destination" },
                        failed = toFailed,
                        onChange = { clearTo(); activeEditingField = EditingField.TO },
                        onTryAgain = { toFailedText?.let { resolveInto(PickTarget.TO, it) } },
                        onViewLink = { toFailedText?.let { OfflineParse.extractUrl(it) }?.let { openUrlExternally(context, it) } },
                        onPasteCoordinates = { pasteInto(PickTarget.TO) },
                        onViewInMaps = { destination?.let { viewLocationInGoogleMaps(context, it.lat, it.lon, destLabel) } },
                        extraAction = if (toFilledViaDefaultShare) {
                            {
                                TextButton(onClick = {
                                    val dest = destination ?: return@TextButton
                                    fromLocation = dest
                                    fromLabel = destLabel
                                    fromQuery = destLabel
                                    fromPlace = destPlace
                                    clearTo()
                                }) { Text("Use as start instead", color = Color.White) }
                            }
                        } else null
                    )

                    // SEARCH RESULTS DROPDOWN (max 8 rows, local matches tagged)
                    if (activeEditingField != null) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black)
                                .padding(4.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (searching) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(14.dp),
                                            strokeWidth = 2.dp,
                                            color = Color.White
                                        )
                                        Text(" Searching…", color = Color.LightGray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 6.dp))
                                    }
                                } else if (searchResults.isNotEmpty()) {
                                    Text("Results:", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                } else {
                                    Text("Places near you:", color = Color.LightGray, style = MaterialTheme.typography.bodySmall)
                                }
                                TextButton(onClick = { activeEditingField = null }) {
                                    Text("Close", color = Color.Cyan)
                                }
                            }
                            searchResults.take(8).forEach { result ->
                                TextButton(
                                    onClick = {
                                        if (activeEditingField == EditingField.FROM) {
                                            fromLocation = result.location
                                            fromLabel = result.label
                                            fromQuery = result.label
                                            fromPlace = null
                                        } else {
                                            destination = result.location
                                            destLabel = result.label
                                            toQuery = result.label
                                            destPlace = null
                                            toFilledViaDefaultShare = false
                                        }
                                        searchResults = emptyList()
                                        activeEditingField = null
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(result.label, color = Color.White, maxLines = 1, modifier = Modifier.weight(1f))
                                        result.tag?.let {
                                            Text(it, color = Color.Gray, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 8.dp))
                                        }
                                    }
                                }
                            }
                            if (!searching && searchResults.isEmpty() && searchFailed) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Text("No match. Try adding the city, or Choose in Google Maps.", color = Color.White)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                                        TextButton(onClick = {
                                            startChooseInMaps(if (activeEditingField == EditingField.FROM) PickTarget.FROM else PickTarget.TO)
                                            activeEditingField = null
                                        }) { Text("Choose in Google Maps", color = Color.White) }
                                        TextButton(onClick = {
                                            val field = activeEditingField
                                            scope.launch {
                                                val q = if (field == EditingField.FROM) fromQuery else toQuery
                                                searching = true
                                                val s = runCatching { SettingsRepository(context).get() }.getOrNull()
                                                val results = placeSearchRepo.search(q, near = currentReadyLatLon, countryCode = s?.searchCountryCode)
                                                searching = false
                                                searchResults = results
                                                searchFailed = results.isEmpty()
                                            }
                                        }) { Text("Try again", color = Color.White) }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Saved places chips & Recent trips chips
            if (savedPlaces.isNotEmpty() || recentTrips.isNotEmpty()) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        if (savedPlaces.isNotEmpty()) {
                            Text("Saved places", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium)
                            LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                                items(savedPlaces, key = { it.id }) { place ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            destination = LatLon(place.lat, place.lon)
                                            destLabel = place.name
                                            toQuery = place.name
                                            destPlace = null
                                            toFilledViaDefaultShare = false
                                        },
                                        label = { Text(place.name, color = Color.White) },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }

                        if (recentTrips.isNotEmpty()) {
                            Text("Recent trips", color = Color.LightGray, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 8.dp))
                            LazyRow(modifier = Modifier.padding(vertical = 4.dp)) {
                                items(recentTrips, key = { it.id }) { trip ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            destination = LatLon(trip.toLat, trip.toLon)
                                            destLabel = trip.toLabel
                                            toQuery = trip.toLabel
                                            destPlace = null
                                            toFilledViaDefaultShare = false
                                            mode = trip.mode
                                            radiusMeters = trip.radiusMeters
                                            trip.finalAlarmMeters?.let { finalAlarmEnabled = true; finalAlarmMeters = it }
                                        },
                                        label = { Text("${trip.fromLabel} > ${trip.toLabel}", color = Color.White) },
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Alarm radius, Mode toggle, Final alarm switch
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.DarkGray),
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Alarm radius", color = Color.LightGray)
                    KnockDistanceChipsRow(
                        presetsMeters = MAIN_RADIUS_PRESETS_M,
                        selectedMeters = radiusMeters,
                        isCustom = radiusMeters !in MAIN_RADIUS_PRESETS_M,
                        onSelectPreset = { radiusMeters = it },
                        onCustomClick = { showCustomRadiusDialog = true },
                        modifier = Modifier.padding(vertical = 4.dp)
                    )

                    Text("Mode", color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                        FilterChip(
                            selected = mode == Mode.CAB,
                            onClick = { mode = Mode.CAB },
                            label = { Text("Cab", color = if (mode == Mode.CAB) Color.Black else Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White)
                        )
                        FilterChip(
                            selected = mode == Mode.BUS_TRAIN,
                            onClick = { mode = Mode.BUS_TRAIN },
                            label = { Text("Bus / Train", color = if (mode == Mode.BUS_TRAIN) Color.Black else Color.White) },
                            colors = FilterChipDefaults.filterChipColors(selectedContainerColor = Color.White)
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Switch(checked = finalAlarmEnabled, onCheckedChange = { finalAlarmEnabled = it })
                        Text("Final alarm at ${formatMeters(finalAlarmMeters)}", color = Color.White, modifier = Modifier.padding(start = 8.dp))
                    }
                    if (finalAlarmEnabled) {
                        KnockDistanceChipsRow(
                            presetsMeters = FINAL_ALARM_PRESETS_M,
                            selectedMeters = finalAlarmMeters,
                            isCustom = finalAlarmMeters !in FINAL_ALARM_PRESETS_M,
                            onSelectPreset = { finalAlarmMeters = it },
                            onCustomClick = { showCustomFinalDialog = true },
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        if (finalAlarmMeters >= radiusMeters) {
                            Text(
                                "Final alarm must be smaller than the main radius, adjusted to ${formatMeters(radiusMeters / 2)}",
                                color = Color.LightGray,
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            errorText?.let {
                Text(it, color = Color.LightGray, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }

    if (showMapsHintDialog) {
        AlertDialog(
            onDismissRequest = { showMapsHintDialog = false },
            containerColor = Color.Black,
            title = { Text("Choose in Google Maps", color = Color.White) },
            text = {
                Text(
                    "In Google Maps, search the place, tap Share, then choose Knock Me. Or long-press a " +
                        "spot, tap the coordinates to copy them, then come back here and tap Paste.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showMapsHintDialog = false
                    openGoogleMapsPicker(context)
                }) { Text("Continue", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch { SettingsRepository(context).update { it.copy(mapsPickerHintDismissed = true) } }
                    showMapsHintDialog = false
                    openGoogleMapsPicker(context)
                }) { Text("Don't show again", color = Color.White) }
            }
        )
    }

    if (showClipboardOfferSheet) {
        val target = clipboardOfferTarget
        AlertDialog(
            onDismissRequest = {
                lastClipboardOfferedText = clipboardOfferText
                showClipboardOfferSheet = false
            },
            containerColor = Color.Black,
            title = { Text("Use the copied place for ${if (target == PickTarget.FROM) "From" else "To"}?", color = Color.White) },
            text = { Text(clipboardOfferText.orEmpty(), color = Color.LightGray, maxLines = 3) },
            confirmButton = {
                TextButton(onClick = {
                    val text = clipboardOfferText
                    lastClipboardOfferedText = text
                    showClipboardOfferSheet = false
                    if (text != null) {
                        resolveInto(target, text)
                        scope.launch { pendingPickRepo.clear() }
                    }
                }) { Text("Use", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = {
                    lastClipboardOfferedText = clipboardOfferText
                    showClipboardOfferSheet = false
                }) { Text("Dismiss", color = Color.White) }
            }
        )
    }

    if (showSavedPlacesDropdown) {
        AlertDialog(
            onDismissRequest = { showSavedPlacesDropdown = false },
            containerColor = Color.Black,
            title = { Text("Select saved place", color = Color.White) },
            text = {
                Column {
                    if (savedPlaces.isEmpty()) {
                        Text("No saved places yet", color = Color.White)
                    } else {
                        savedPlaces.forEach { place ->
                            TextButton(onClick = {
                                if (savedPlaceTargetField == EditingField.FROM) {
                                    fromLocation = LatLon(place.lat, place.lon)
                                    fromLabel = place.name
                                    fromQuery = place.name
                                    fromPlace = null
                                } else {
                                    destination = LatLon(place.lat, place.lon)
                                    destLabel = place.name
                                    toQuery = place.name
                                    destPlace = null
                                    toFilledViaDefaultShare = false
                                }
                                showSavedPlacesDropdown = false
                            }) {
                                Text(place.name, color = Color.White)
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSavedPlacesDropdown = false }) { Text("Close", color = Color.White) }
            }
        )
    }

    if (showCustomRadiusDialog) {
        KnockCustomDistanceDialog(
            title = "Custom alarm radius",
            minMeters = 100,
            maxMeters = 50_000,
            allowUnitToggle = true,
            initialMeters = radiusMeters,
            onConfirm = { radiusMeters = it },
            onDismiss = { showCustomRadiusDialog = false }
        )
    }

    if (showCustomFinalDialog) {
        KnockCustomDistanceDialog(
            title = "Custom final alarm",
            minMeters = 50,
            maxMeters = 5000,
            allowUnitToggle = false,
            initialMeters = finalAlarmMeters,
            onConfirm = { finalAlarmMeters = it },
            onDismiss = { showCustomFinalDialog = false }
        )
    }

    if (showSavePlaceDialog) {
        AlertDialog(
            onDismissRequest = { showSavePlaceDialog = false },
            containerColor = Color.Black,
            title = { Text("Save place", color = Color.White) },
            text = {
                OutlinedTextField(
                    value = savePlaceNameText,
                    onValueChange = { savePlaceNameText = it },
                    label = { Text("Name (e.g. Home, Office)", color = Color.LightGray) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val dest = destination
                    if (dest != null && savePlaceNameText.isNotBlank()) {
                        scope.launch {
                            historyRepo.insertSavedPlace(
                                com.joyel.tripguard.history.SavedPlace(
                                    name = savePlaceNameText,
                                    label = destLabel,
                                    lat = dest.lat,
                                    lon = dest.lon,
                                    createdAt = System.currentTimeMillis()
                                )
                            )
                            MemoryMirror.requestWrite(context)
                        }
                    }
                    showSavePlaceDialog = false
                }) { Text("Save", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = { showSavePlaceDialog = false }) { Text("Cancel", color = Color.White) }
            }
        )
    }

    if (showFullScreenIntentAsk) {
        AlertDialog(
            onDismissRequest = { showFullScreenIntentAsk = false },
            containerColor = Color.Black,
            title = { Text("Alarms over the lock screen", color = Color.White) },
            text = {
                Text(
                    "So the arrival alarm can wake your screen even while it's locked. " +
                        "If you skip this, Knock Me will still alert you with a sound and a notification.",
                    color = Color.LightGray
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        com.joyel.tripguard.ui.onboarding.OnboardingRepository(context).skip("FULL_SCREEN_INTENT")
                        context.startActivity(
                            Intent(
                                android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                                android.net.Uri.parse("package:${context.packageName}")
                            )
                        )
                        showFullScreenIntentAsk = false
                        pendingStartDest?.let { proceedWithStart(it, false) }
                    }
                }) { Text("Allow", color = Color.White) }
            },
            dismissButton = {
                TextButton(onClick = {
                    scope.launch {
                        com.joyel.tripguard.ui.onboarding.OnboardingRepository(context).skip("FULL_SCREEN_INTENT")
                        showFullScreenIntentAsk = false
                        pendingStartDest?.let { proceedWithStart(it, false) }
                    }
                }) { Text("Skip", color = Color.White) }
            }
        )
    }

    if (showAlreadyInsideDialog) {
        AlertDialog(
            onDismissRequest = { showAlreadyInsideDialog = false },
            containerColor = Color.Black,
            title = { Text("You are already within ${effectiveRadiusMeters() / 1000.0} km", color = Color.White) },
            text = { Text("Start the trip anyway?", color = Color.White) },
            confirmButton = {
                TextButton(onClick = {
                    showAlreadyInsideDialog = false
                    startTrip(skipInsideCheck = true)
                }) { Text("Continue", color = Color.Cyan) }
            },
            dismissButton = {
                TextButton(onClick = { showAlreadyInsideDialog = false }) { Text("Cancel", color = Color.White) }
            }
        )
    }
}

/** Below a From/To field: once filled, a place card (name, address, grey coordinates, Change +
 * View in Google Maps); on a resolve failure, a calm grey card instead — never a raw URL, never
 * red text (v2.0 rule, red is reserved for the alarm screen and Stop button). */
@Composable
private fun PlaceOrFailureCard(
    place: Place?,
    plainLat: Double?,
    plainLon: Double?,
    plainLabel: String,
    failed: Boolean,
    onChange: () -> Unit,
    onTryAgain: () -> Unit,
    onViewLink: () -> Unit,
    onPasteCoordinates: () -> Unit,
    onViewInMaps: () -> Unit,
    extraAction: (@Composable () -> Unit)? = null
) {
    if (failed) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.Black),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                Text("Could not read this link.", color = Color.LightGray)
                FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                    TextButton(onClick = onTryAgain) { Text("Try again", color = Color.White) }
                    TextButton(onClick = onViewLink) { Text("View link in Google Maps", color = Color.White) }
                    TextButton(onClick = onPasteCoordinates) { Text("Paste coordinates", color = Color.White) }
                }
            }
        }
        return
    }
    if (plainLat == null || plainLon == null) return
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black),
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(place?.name ?: plainLabel, color = Color.White, style = MaterialTheme.typography.bodyMedium)
            place?.address?.let { Text(it, color = Color.LightGray, style = MaterialTheme.typography.bodySmall) }
            place?.source?.let { Text("Shared from $it", color = Color.Gray, style = MaterialTheme.typography.bodySmall) }
            Text(OfflineParse.coordinateLabel(plainLat, plainLon), color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            FlowRow(modifier = Modifier.padding(top = 4.dp)) {
                TextButton(onClick = onChange) { Text("Change", color = Color.White) }
                TextButton(onClick = onViewInMaps) { Text("View in Google Maps", color = Color.White) }
                extraAction?.invoke()
            }
        }
    }
}
