package com.example.farmapp.inspector

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Assignment
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.farmapp.network.SessionSnapshot
import com.example.farmapp.ui.screens.ProductImage
import com.example.farmapp.ui.theme.FarmAppTheme
import com.example.farmapp.ui.theme.KilivanaAccents
import com.example.farmapp.ui.theme.KilivanaSpacing
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

private enum class InspectorTab(val title: String) { DASHBOARD("Dashboard"), INSPECTIONS("My inspections"), PROFILE("Profile") }

@Composable
fun InspectorShell(session: SessionSnapshot, onLogout: () -> Unit, isDarkTheme: Boolean,
    onToggleTheme: () -> Unit, signingOut: Boolean, modifier: Modifier = Modifier) {
    val model: InspectorViewModel = viewModel(key = "inspector", factory = InspectorViewModel.factory)
    val state by model.uiState.collectAsStateWithLifecycle()
    // Hide data synchronously with session changes, even before the state collector catches up.
    val visible = if (!session.loggingOut && state.generation == session.generation) state else
        InspectorUiState(loadingJobs = true, loadingProfile = true)
    InspectorContent(visible, session.auth?.fullName.orEmpty(), onLogout, isDarkTheme, onToggleTheme,
        signingOut || session.loggingOut, model::refresh, model::open, model::closeDetail, modifier,
        model::changeStatus, model::submitFinding)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InspectorContent(state: InspectorUiState, name: String, onLogout: () -> Unit,
    isDarkTheme: Boolean, onToggleTheme: () -> Unit, signingOut: Boolean, onRefresh: () -> Unit,
    onOpen: (Long) -> Unit, onBack: () -> Unit, modifier: Modifier = Modifier,
    onChangeStatus: (InspectionStatus) -> Unit = {}, onSubmitFinding: (InspectionFinding, String) -> Unit = { _, _ -> }) {
    var tab by rememberSaveable { mutableStateOf(InspectorTab.DASHBOARD) }
    var profileEditor by remember { mutableStateOf(false) }
    BackHandler(state.selectedId != null) { if (!state.submitting) onBack() }
    Scaffold(modifier.fillMaxSize(), topBar = {
        TopAppBar(title = { Column {
            Text(if (state.selectedId != null) "Inspection details" else "Kilivana", fontWeight = FontWeight.Bold)
            Text("Inspector", style = MaterialTheme.typography.labelMedium, color = KilivanaAccents.inspectorContent)
        } }, navigationIcon = { if (state.selectedId != null) IconButton(onClick = onBack, enabled = !state.submitting) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back to inspections")
        } }, actions = {
            IconButton(onClick = onToggleTheme) { Icon(if (isDarkTheme) Icons.Default.WbSunny else Icons.Default.Bedtime, "Toggle color theme") }
            IconButton(onClick = onLogout, enabled = !signingOut) { Icon(Icons.AutoMirrored.Filled.ExitToApp, "Sign out") }
        })
    }, bottomBar = { if (state.selectedId == null) NavigationBar {
        InspectorTab.entries.forEach { destination -> NavigationBarItem(selected = tab == destination,
            onClick = { tab = destination }, label = { Text(destination.title) }, icon = {
                Icon(when (destination) { InspectorTab.DASHBOARD -> Icons.Default.Dashboard
                    InspectorTab.INSPECTIONS -> Icons.AutoMirrored.Filled.Assignment
                    InspectorTab.PROFILE -> Icons.Default.Person }, destination.title)
            }) }
    } }) { padding ->
        PullToRefreshBox(isRefreshing = state.loadingJobs || state.loadingProfile, onRefresh = {
            if (!state.submitting && !signingOut) {
                if (state.selectedId != null) onOpen(state.selectedId) else onRefresh()
            }
        }, modifier = Modifier.fillMaxSize().padding(padding)) {
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(KilivanaSpacing.content),
            verticalArrangement = Arrangement.spacedBy(KilivanaSpacing.medium)) {
            if (state.selectedId != null) {
                item { Text("Inspection #${state.selectedId}", style = MaterialTheme.typography.headlineSmall) }
                if (state.loadingDetail) item { Loading("Loading inspection details") }
                state.detailError?.let { error -> item { Notice(error) { onOpen(state.selectedId) } } }
                state.detail?.let { job -> item {
                    InspectionCard(job)
                    Column(Modifier.padding(top = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Field("Inspector ID", job.inspectorId.toString())
                        Field("Result", job.result?.replace('_', ' '))
                        Field("Notes", job.notes)
                        Field("Inspected at", displayDate(job.inspectedAt))
                        Field("Created", displayDate(job.createdAt))
                        Field("Last updated", displayDate(job.updatedAt))
                    }
                } }
                state.detail?.let { job ->
                    item { InspectionWorkflow(job, state, onChangeStatus, onSubmitFinding) { onOpen(job.id) } }
                    item { Text("Inspection evidence", style = MaterialTheme.typography.titleLarge) }
                    item { Text(EVIDENCE_PRIVACY_BLOCKER, style = MaterialTheme.typography.bodyMedium) }
                    item { OutlinedButton(onClick = {}, enabled = false) { Text("Upload evidence unavailable") } }
                    if (state.loadingEvidence) item { Loading("Loading existing evidence") }
                    else if (state.evidenceError != null) item { Notice(state.evidenceError) { onOpen(job.id) } }
                    else if (state.evidence.isEmpty()) item { Text("No evidence images returned by the inspection evidence API.") }
                    items(state.evidence, key = { it.id!! }) { image ->
                        Card { Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Evidence #${image.id}", style = MaterialTheme.typography.titleMedium)
                            image.url?.let { ProductImage(it, "Inspection evidence") }
                        } }
                    }
                }
            } else when (tab) {
                InspectorTab.DASHBOARD -> {
                    item { Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.tertiaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("YOUR INSPECTION DESK", style = MaterialTheme.typography.labelMedium)
                            Text("Welcome, ${state.profile?.account?.fullName ?: name}", style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold)
                            Text("Your assigned work, in one place.", style = MaterialTheme.typography.bodyLarge)
                        }
                    } }
                    if (state.loadingJobs) item { Loading("Loading your assignments") }
                    if (state.jobsError != null) item { Notice(state.jobsError, onRefresh) }
                    if (state.jobs.isNotEmpty() || !state.loadingJobs && state.jobsError == null) {
                        item { val counts = inspectionCounts(state.jobs)
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Metric("Assigned to you", counts.assigned, Modifier.weight(1f))
                                    Metric("Pending", counts.pending, Modifier.weight(1f))
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Metric("In progress", counts.inProgress, Modifier.weight(1f))
                                    Metric("Completed", counts.completed, Modifier.weight(1f))
                                }
                                Text("Pending includes Assigned and Pending statuses. Cancelled: ${counts.cancelled}.",
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                        item { Text("Recent assignments", style = MaterialTheme.typography.titleLarge) }
                        if (state.jobs.isEmpty()) item { EmptyAssignments() }
                        items(state.jobs.take(5), key = { it.id }) { job -> InspectionCard(job) { onOpen(job.id) } }
                    }
                }
                InspectorTab.INSPECTIONS -> {
                    item { Text("My inspections", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item { Text("Only inspections assigned to your account.") }
                    if (state.loadingJobs) item { Loading("Loading your assignments") }
                    if (state.jobsError != null) item { Notice(state.jobsError, onRefresh) }
                    if (!state.loadingJobs && state.jobsError == null && state.jobs.isEmpty()) item { EmptyAssignments() }
                    items(state.jobs, key = { it.id }) { job -> InspectionCard(job) { onOpen(job.id) } }
                }
                InspectorTab.PROFILE -> {
                    item { Text("Inspector profile", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) }
                    item { TextButton(onClick = { profileEditor = true }) { Text("Edit account profile") } }
                    if (state.loadingProfile) item { Loading("Loading your profile") }
                    else if (state.profileError != null) item { Notice(state.profileError, onRefresh) }
                    state.profile?.let { profile ->
                        item { Card(shape = RoundedCornerShape(24.dp)) {
                            Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                                Field("Name", profile.account.fullName)
                                Field("Email", profile.account.email)
                                Field("Phone", profile.account.phoneNumber)
                                profile.account.region?.let { Field("Account region", it) }
                                Field("Account status", profile.account.accountStatus)
                                if (profile.details == null) Text("No Inspector profile has been assigned yet. Contact your administrator.")
                                profile.details?.let { details ->
                                    Field("Specialization", details.specialization)
                                    Field("Assigned area", details.assignedArea)
                                    Field("Inspector status", details.status)
                                    Field("Background", details.inspectorDetails)
                                    val image = details.images.orEmpty().firstOrNull { it.isPrimary == true }
                                        ?: details.images.orEmpty().firstOrNull()
                                    image?.url?.takeIf { it.isNotBlank() }?.let {
                                        Text("Profile image", style = MaterialTheme.typography.labelLarge)
                                        ProductImage(it, "Inspector profile")
                                    }
                                }
                                Text("Assignments and profile details are managed by your administrator.", style = MaterialTheme.typography.bodySmall)
                            }
                        } }
                    }
                    item { com.example.farmapp.profile.AboutKilivana() }
                }
            }
        }
        }
    }
    if (profileEditor) com.example.farmapp.profile.ProfileEditor { profileEditor = false; onRefresh() }
}

@Composable
private fun InspectionWorkflow(job: Inspection, state: InspectorUiState,
    changeStatus: (InspectionStatus) -> Unit, submitFinding: (InspectionFinding, String) -> Unit, reconcile: () -> Unit) {
    // Findings stay in memory, never SavedState or a plaintext draft journal.
    var selectedStatus by remember(job.id, job.status) { mutableStateOf(job.status) }
    var selectedResult by remember(job.id, job.result) { mutableStateOf(InspectionFinding.entries.firstOrNull { it.name == job.result }) }
    var notes by remember(job.id, job.notes) { mutableStateOf(job.notes.orEmpty()) }
    var statusMenu by remember { mutableStateOf(false) }
    var resultMenu by remember { mutableStateOf(false) }
    var confirmation by remember { mutableStateOf<String?>(null) }
    var formError by remember { mutableStateOf<String?>(null) }
    val uncertain = job.id in state.reconciliationRequired
    val enabled = !state.submitting && !uncertain
    OutlinedCard {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Inspection workflow", style = MaterialTheme.typography.titleLarge)
            Text("Changes apply to inspection #${job.id} assigned to your account. Review each change before sending.")
            state.writeNotice?.let { Text(it, color = MaterialTheme.colorScheme.primary) }
            state.writeError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            if (state.submitting) Loading("Submitting once. Please wait for the server response.")
            if (uncertain) OutlinedButton(onClick = reconcile, enabled = !state.submitting) { Text("Reload server state") }
            Box {
                OutlinedButton(onClick = { statusMenu = true }, enabled = enabled) { Text("Status: ${selectedStatus.label}") }
                DropdownMenu(expanded = statusMenu, onDismissRequest = { statusMenu = false }) {
                    supportedStatusChanges(job.status).forEach { phase -> DropdownMenuItem(text = { Text(phase.label) },
                        onClick = { selectedStatus = phase; statusMenu = false }) }
                }
            }
            Button(onClick = { confirmation = "status" }, enabled = enabled && selectedStatus != job.status) { Text("Review status change") }
            HorizontalDivider()
            Text("Findings", style = MaterialTheme.typography.titleMedium)
            Text("Record observations only. Do not enter national ID numbers or document contents.", style = MaterialTheme.typography.bodySmall)
            Box {
                OutlinedButton(onClick = { resultMenu = true }, enabled = enabled) { Text("Result: ${selectedResult?.label ?: "Choose a result"}") }
                DropdownMenu(expanded = resultMenu, onDismissRequest = { resultMenu = false }) {
                    InspectionFinding.entries.forEach { result -> DropdownMenuItem(text = { Text(result.label) },
                        onClick = { selectedResult = result; resultMenu = false }) }
                }
            }
            OutlinedTextField(notes, { notes = it; formError = null }, label = { Text("Inspection findings notes") },
                enabled = enabled, minLines = 3, modifier = Modifier.fillMaxWidth(), supportingText = { Text("${notes.length} / 4000 characters") })
            formError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            Button(onClick = {
                formError = try { validateFindingNotes(notes); if (selectedResult == null) "Choose an inspection result." else null }
                    catch (e: InspectorFailure) { e.message }
                if (formError == null) confirmation = "finding"
            }, enabled = enabled) { Text("Review findings") }
        }
    }
    confirmation?.let { action -> AlertDialog(onDismissRequest = { confirmation = null },
        title = { Text(if (action == "status") "Confirm status change" else "Confirm inspection findings") },
        text = { Text(if (action == "status") "Inspection #${job.id}: ${job.status.label} → ${selectedStatus.label}."
            else "Record ${selectedResult?.label} and your notes for inspection #${job.id}? This records an inspection result; product approval remains with the administrator.") },
        confirmButton = { TextButton(enabled = enabled, onClick = {
            confirmation = null
            if (action == "status") changeStatus(selectedStatus) else selectedResult?.let { submitFinding(it, notes) }
        }) { Text("Confirm submission") } },
        dismissButton = { TextButton(onClick = { confirmation = null }) { Text("Keep editing") } }) }
}

@Composable private fun Metric(label: String, count: Int, modifier: Modifier) {
    OutlinedCard(modifier, shape = RoundedCornerShape(20.dp)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(count.toString(), style = MaterialTheme.typography.headlineLarge, color = KilivanaAccents.inspectorContent)
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
@Composable private fun InspectionCard(job: Inspection, onClick: (() -> Unit)? = null) {
    val content: @Composable ColumnScope.() -> Unit = {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("${job.targetType} #${job.targetId}", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text("Inspection #${job.id}", style = MaterialTheme.typography.bodySmall)
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(8.dp)) {
                Text(job.status.label, Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
            }
            displayDate(job.createdAt)?.let { Text("Assigned record created $it", style = MaterialTheme.typography.bodyMedium) }
        }
    }
    if (onClick != null) Card(onClick = onClick, shape = RoundedCornerShape(20.dp), content = content)
    else Card(shape = RoundedCornerShape(20.dp), content = content)
}
@Composable private fun Field(label: String, value: String?) {
    Column { Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value?.takeIf { it.isNotBlank() } ?: "Not provided", style = MaterialTheme.typography.bodyLarge) }
}
@Composable private fun Notice(message: String, retry: () -> Unit) {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(20.dp)) {
        Text(message, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = retry) { Text("Try again") }
    } }
}
@Composable private fun Loading(label: String) {
    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text(label) }
}
@Composable private fun EmptyAssignments() {
    OutlinedCard { Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("No inspections assigned", style = MaterialTheme.typography.titleMedium)
        Text("New assignments from your administrator will appear here. Use Refresh to check again.")
    } }
}
private fun displayDate(date: LocalDateTime?) = date?.format(DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm"))

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable private fun InspectorDashboardPreview() {
    FarmAppTheme {
        InspectorContent(InspectorUiState(jobs = listOf(
            Inspection(101, 42, "PRODUCT", 7, InspectionStatus.ASSIGNED, null, null, null, LocalDateTime.of(2026, 10, 16, 9, 0), null),
            Inspection(102, 42, "FARMER", 8, InspectionStatus.IN_PROGRESS, null, null, null, null, null)
        )), "Synthetic Inspector (preview)", {}, false, {}, false, {}, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable private fun InspectorEmptyPreview() {
    FarmAppTheme { InspectorContent(InspectorUiState(), "Synthetic Inspector (preview)", {}, false, {}, false, {}, {}, {}) }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 780)
@Composable private fun InspectorErrorPreview() {
    FarmAppTheme { InspectorContent(InspectorUiState(jobsError = "Synthetic preview: connection unavailable"),
        "Synthetic Inspector (preview)", {}, true, {}, false, {}, {}, {}) }
}
