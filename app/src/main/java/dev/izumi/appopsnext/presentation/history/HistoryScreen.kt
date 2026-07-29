package dev.izumi.appopsnext.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.izumi.appopsnext.R
import dev.izumi.appopsnext.apps.model.InstalledApp
import dev.izumi.appopsnext.history.model.AppOpHistoryFailureReason
import dev.izumi.appopsnext.history.model.HistoryPermission
import dev.izumi.appopsnext.presentation.components.AppIcon
import java.text.DateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryOverviewScreen(
    uiState: HistoryUiState,
    onRefresh: () -> Unit,
    onPermissionSelected: (HistoryPermission) -> Unit,
    onPermissionsChanged: (List<String>) -> Unit,
    onPermissionOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
    bottomBar: @Composable () -> Unit = {},
) {
    var showPermissionManagement by remember { mutableStateOf(false) }
    var showInformation by remember { mutableStateOf(false) }
    Scaffold(
        modifier = modifier,
        bottomBar = bottomBar,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.history_title),
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                actions = {
                    IconButton(
                        onClick = { showPermissionManagement = true },
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_action_manage,
                            ),
                            contentDescription = stringResource(
                                R.string.history_manage_permissions,
                            ),
                        )
                    }
                    IconButton(
                        onClick = { showInformation = true },
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_action_info,
                            ),
                            contentDescription = stringResource(
                                R.string.history_information,
                            ),
                        )
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !uiState.isLoading,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_refresh),
                            contentDescription = stringResource(
                                R.string.history_refresh,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        HistoryOverviewContent(
            uiState = uiState,
            onPermissionSelected = onPermissionSelected,
            onPermissionOrderChanged = onPermissionOrderChanged,
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
        )
    }

    if (showPermissionManagement) {
        HistoryPermissionManagementDialog(
            availablePermissions = uiState.availablePermissions,
            selectedPermissions = uiState.permissions
                .map { it.permission.shellOperationName }
                .toSet(),
            onApply = { operationNames ->
                showPermissionManagement = false
                onPermissionsChanged(operationNames)
            },
            onDismiss = { showPermissionManagement = false },
        )
    }
    if (showInformation) {
        HistoryOverviewInformationDialog(
            uiState = uiState,
            onDismiss = { showInformation = false },
        )
    }
}

@Composable
private fun HistoryOverviewContent(
    uiState: HistoryUiState,
    onPermissionSelected: (HistoryPermission) -> Unit,
    onPermissionOrderChanged: (List<String>) -> Unit,
    modifier: Modifier = Modifier,
) {
    var displayedPermissions by remember {
        mutableStateOf(uiState.permissions)
    }
    var draggedOperationName by remember {
        mutableStateOf<String?>(null)
    }
    var draggedOffset by remember {
        mutableStateOf(0f)
    }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    LaunchedEffect(uiState.permissions) {
        if (draggedOperationName == null) {
            displayedPermissions = uiState.permissions
        }
    }

    LazyColumn(
        state = listState,
        modifier = modifier.pointerInput(uiState.permissions) {
            detectDragGesturesAfterLongPress(
                onDragStart = { pointerOffset ->
                    val item = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull { itemInfo ->
                            pointerOffset.y.toInt() in
                                itemInfo.offset until
                                (itemInfo.offset + itemInfo.size) &&
                                itemInfo.key
                                    .toString()
                                    .startsWith(
                                        HISTORY_PERMISSION_ITEM_KEY_PREFIX,
                                    )
                        }
                    draggedOperationName = item
                        ?.key
                        ?.toString()
                        ?.removePrefix(
                            HISTORY_PERMISSION_ITEM_KEY_PREFIX,
                        )
                    draggedOffset = 0f
                },
                onDrag = { change, dragAmount ->
                    val operationName = draggedOperationName
                        ?: return@detectDragGesturesAfterLongPress
                    change.consume()
                    draggedOffset += dragAmount.y
                    val currentItem = listState.layoutInfo.visibleItemsInfo
                        .firstOrNull {
                            it.key == historyPermissionItemKey(
                                operationName,
                            )
                        } ?: return@detectDragGesturesAfterLongPress
                    val draggedCenter = currentItem.offset +
                        currentItem.size / 2f +
                        draggedOffset
                    val targetItem = listState.layoutInfo.visibleItemsInfo
                        .filter {
                            it.key
                                .toString()
                                .startsWith(
                                    HISTORY_PERMISSION_ITEM_KEY_PREFIX,
                                )
                        }
                        .minByOrNull {
                            abs(
                                draggedCenter -
                                    (it.offset + it.size / 2f),
                            )
                        }
                    if (
                        targetItem != null &&
                        targetItem.key != currentItem.key
                    ) {
                        val targetOperation = targetItem.key
                            .toString()
                            .removePrefix(
                                HISTORY_PERMISSION_ITEM_KEY_PREFIX,
                            )
                        val fromIndex =
                            displayedPermissions.indexOfFirst {
                                it.permission.shellOperationName ==
                                    operationName
                            }
                        val toIndex =
                            displayedPermissions.indexOfFirst {
                                it.permission.shellOperationName ==
                                    targetOperation
                            }
                        if (fromIndex >= 0 && toIndex >= 0) {
                            displayedPermissions =
                                displayedPermissions
                                    .toMutableList()
                                    .apply {
                                        add(
                                            toIndex,
                                            removeAt(fromIndex),
                                        )
                                    }
                            draggedOffset +=
                                currentItem.offset - targetItem.offset
                        }
                    }
                    val scrollAmount = when {
                        draggedCenter <
                            listState.layoutInfo.viewportStartOffset +
                            HISTORY_DRAG_SCROLL_EDGE_PX ->
                            -HISTORY_DRAG_SCROLL_STEP_PX

                        draggedCenter >
                            listState.layoutInfo.viewportEndOffset -
                            HISTORY_DRAG_SCROLL_EDGE_PX ->
                            HISTORY_DRAG_SCROLL_STEP_PX

                        else -> 0f
                    }
                    if (scrollAmount != 0f) {
                        coroutineScope.launch {
                            listState.scrollBy(scrollAmount)
                        }
                    }
                },
                onDragEnd = {
                    if (draggedOperationName != null) {
                        onPermissionOrderChanged(
                            displayedPermissions.map {
                                it.permission.shellOperationName
                            },
                        )
                    }
                    draggedOperationName = null
                    draggedOffset = 0f
                },
                onDragCancel = {
                    displayedPermissions = uiState.permissions
                    draggedOperationName = null
                    draggedOffset = 0f
                },
            )
        },
        contentPadding = HistoryContentPadding,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (uiState.waitingForBackend) {
            item {
                HistoryStatusCard(
                    text = stringResource(
                        R.string.history_waiting_for_backend,
                    ),
                    showProgress = true,
                )
            }
        } else {
            uiState.failureReason?.let { failureReason ->
                item {
                    HistoryStatusCard(
                        text = historyFailureMessage(failureReason),
                    )
                }
            }
            if (uiState.partialFailureCount > 0) {
                item {
                    HistoryStatusCard(
                        text = stringResource(
                            R.string.history_partial_failure,
                            uiState.partialFailureCount,
                        ),
                    )
                }
            }
            if (uiState.permissions.isEmpty()) {
                item {
                    HistoryStatusCard(
                        text = stringResource(
                            R.string.history_no_selected_permissions,
                        ),
                    )
                }
            } else {
                item {
                    PermissionDistributionChart(
                        permissions = uiState.permissions,
                    )
                }
                item {
                    Text(
                        text = stringResource(
                            R.string.history_permission_list_title,
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                items(
                    items = displayedPermissions,
                    key = {
                        historyPermissionItemKey(
                            it.permission.shellOperationName,
                        )
                    },
                ) { history ->
                    val isDragging =
                        history.permission.shellOperationName ==
                            draggedOperationName
                    PermissionHistoryCard(
                        history = history,
                        onClick = {
                            onPermissionSelected(history.permission)
                        },
                        isDragging = isDragging,
                        modifier = Modifier
                            .zIndex(if (isDragging) 1f else 0f)
                            .graphicsLayer {
                                translationY = if (isDragging) {
                                    draggedOffset
                                } else {
                                    0f
                                }
                            },
                    )
                }
            }
        }
        if (uiState.isLoading) {
            item {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionHistoryDetailScreen(
    permission: HistoryPermission,
    history: PermissionHistory?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onAppsSelected: () -> Unit,
    onAppSelected: (InstalledApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showInformation by remember(permission.shellOperationName) {
        mutableStateOf(false)
    }
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = permission.displayName(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.SemiBold,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back),
                            contentDescription = stringResource(
                                R.string.action_back,
                            ),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showInformation = true },
                    ) {
                        Icon(
                            painter = painterResource(
                                R.drawable.ic_action_info,
                            ),
                            contentDescription = stringResource(
                                R.string.history_information,
                            ),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { contentPadding ->
        val events = history?.events.orEmpty()
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(contentPadding),
            contentPadding = HistoryContentPadding,
            verticalArrangement = Arrangement.Top,
        ) {
            history?.failureReason?.let { failureReason ->
                item {
                    HistoryStatusCard(
                        text = historyFailureMessage(failureReason),
                        modifier = Modifier.padding(bottom = 12.dp),
                    )
                }
            }
            item {
                DetailSummary(
                    recordCount = history?.recordCount ?: 0,
                    appCount = history?.appCount ?: 0,
                    onAppsSelected = onAppsSelected,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            item {
                SevenDayHistoryChart(
                    events = events,
                    modifier = Modifier.padding(bottom = 12.dp),
                )
            }
            item {
                Text(
                    text = stringResource(R.string.history_timeline_title),
                    modifier = Modifier.padding(bottom = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            if (isLoading && history == null) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (events.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.history_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                itemsIndexed(
                    items = events,
                    key = { index, item ->
                        "${item.event.packageName}:" +
                            "${item.event.accessTimeMillis}:" +
                            "${item.event.attributionTag}:$index"
                    },
                ) { index, item ->
                    TimelineHistoryItem(
                        item = item,
                        isFirst = index == 0,
                        isLast = index == events.lastIndex,
                        onClick = { onAppSelected(item.app) },
                    )
                }
            }
        }
    }

    if (showInformation) {
        PermissionHistoryInformationDialog(
            permission = permission,
            onDismiss = { showInformation = false },
        )
    }
}

@Composable
private fun PermissionDistributionChart(
    permissions: List<PermissionHistory>,
    modifier: Modifier = Modifier,
) {
    val maximum = permissions.maxOfOrNull(PermissionHistory::recordCount)
        ?.coerceAtLeast(1)
        ?: 1
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.history_distribution_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            permissions.forEach { history ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = history.permission.displayName(),
                            style = MaterialTheme.typography.labelLarge,
                        )
                        Text(
                            text = history.recordCount.toString(),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                    LinearProgressIndicator(
                        progress = {
                            history.recordCount.toFloat() /
                                maximum.toFloat()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun PermissionHistoryCard(
    history: PermissionHistory,
    onClick: () -> Unit,
    isDragging: Boolean,
    modifier: Modifier = Modifier,
) {
    val locale = LocalConfiguration.current.locales[0]
        ?: Locale.getDefault()
    val latestText = history.latestAccessTimeMillis?.let { timestamp ->
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.SHORT,
            locale,
        ).format(Date(timestamp))
    } ?: stringResource(R.string.history_never_recorded)
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isDragging) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLow
            },
        ),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isDragging) 8.dp else 0.dp,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = history.permission.displayName(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = history.permission.systemOperationName(),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(
                    painter = painterResource(R.drawable.ic_drag_handle),
                    contentDescription = stringResource(
                        R.string.history_reorder_permissions,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(2.dp))
            history.failureReason?.let { failureReason ->
                Text(
                    text = historyFailureMessage(failureReason),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            Text(
                text = stringResource(
                    R.string.history_permission_summary,
                    history.recordCount,
                    history.appCount,
                ),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = stringResource(
                    R.string.history_latest_record,
                    latestText,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailSummary(
    recordCount: Int,
    appCount: Int,
    onAppsSelected: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SummaryMetric(
            value = recordCount.toString(),
            label = stringResource(R.string.history_system_records),
            modifier = Modifier.weight(1f),
        )
        SummaryMetric(
            value = appCount.toString(),
            label = stringResource(R.string.history_apps_involved),
            onClick = onAppsSelected,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SummaryMetric(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    Card(
        modifier = modifier.then(
            if (onClick != null) {
                Modifier.clickable(onClick = onClick)
            } else {
                Modifier
            },
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onClick != null) {
                    Icon(
                        painter = painterResource(
                            R.drawable.ic_chevron_right,
                        ),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SevenDayHistoryChart(
    events: List<ResolvedHistoryEvent>,
    modifier: Modifier = Modifier,
) {
    val zoneId = remember { ZoneId.systemDefault() }
    val locale = LocalConfiguration.current.locales[0]
        ?: Locale.getDefault()
    val counts = remember(events, zoneId) {
        HistoryStatistics.dailyCounts(
            events = events,
            nowMillis = System.currentTimeMillis(),
            zoneId = zoneId,
        )
    }
    val maximum = counts.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    val dayFormatter = remember(locale) {
        DateTimeFormatter.ofPattern("M/d", locale)
    }
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.history_seven_day_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(156.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Bottom,
            ) {
                counts.forEach { dailyCount ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            text = dailyCount.count.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(4.dp))
                        Box(
                            modifier = Modifier
                                .widthIn(max = 28.dp)
                                .fillMaxWidth()
                                .height(
                                    (
                                        92f *
                                            dailyCount.count.toFloat() /
                                            maximum.toFloat()
                                    ).coerceAtLeast(
                                        if (dailyCount.count > 0) 4f else 1f,
                                    ).dp,
                                )
                                .clip(
                                    RoundedCornerShape(
                                        topStart = 5.dp,
                                        topEnd = 5.dp,
                                    ),
                                )
                                .background(
                                    if (dailyCount.count > 0) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme
                                            .surfaceContainerHighest
                                    },
                                ),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = dailyCount.date.format(dayFormatter),
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TimelineHistoryItem(
    item: ResolvedHistoryEvent,
    isFirst: Boolean,
    isLast: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    val markerColor = MaterialTheme.colorScheme.primary
    val locale = LocalConfiguration.current.locales[0]
        ?: Locale.getDefault()
    val timestamp = remember(item.event.accessTimeMillis, locale) {
        DateFormat.getDateTimeInstance(
            DateFormat.MEDIUM,
            DateFormat.MEDIUM,
            locale,
        ).format(Date(item.event.accessTimeMillis))
    }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                val centerX = 12.dp.toPx()
                val markerY = 18.dp.toPx()
                if (!isFirst) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, 0f),
                        end = Offset(centerX, markerY),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                if (!isLast) {
                    drawLine(
                        color = lineColor,
                        start = Offset(centerX, markerY),
                        end = Offset(centerX, size.height),
                        strokeWidth = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawCircle(
                    color = markerColor,
                    radius = 5.dp.toPx(),
                    center = Offset(centerX, markerY),
                )
            }
            .clickable(onClick = onClick),
    ) {
        Spacer(modifier = Modifier.width(24.dp))
        AppIcon(
            packageName = item.app.packageName,
            appLabel = item.app.label,
            modifier = Modifier.padding(start = 10.dp),
            size = 38.dp,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 10.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.app.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = item.app.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = timestamp,
                style = MaterialTheme.typography.bodyMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val foreground = item.event.uidState in ForegroundUidStates
                Text(
                    text = stringResource(
                        if (foreground) {
                            R.string.history_foreground
                        } else {
                            R.string.history_background
                        },
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                item.event.durationMillis?.let { duration ->
                    Text(
                        text = formatHistoryDuration(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (item.event.isAggregated) {
                Text(
                    text = stringResource(
                        R.string.history_aggregated_access_count,
                        item.event.accessCount,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.event.attributionTag?.let { attribution ->
                Text(
                    text = stringResource(
                        R.string.history_attribution,
                        attribution,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
private fun formatHistoryDuration(durationMillis: Long): String {
    val seconds = durationMillis / 1_000L
    return when {
        seconds < 1L -> stringResource(
            R.string.app_op_usage_duration_less_than_second,
        )

        seconds < 60L -> stringResource(
            R.string.app_op_usage_duration,
            stringResource(R.string.app_op_usage_seconds, seconds),
        )

        else -> stringResource(
            R.string.app_op_usage_duration,
            stringResource(R.string.app_op_usage_minutes, seconds / 60L),
        )
    }
}

@Composable
private fun HistoryStatusCard(
    text: String,
    modifier: Modifier = Modifier,
    showProgress: Boolean = false,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (showProgress) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

@Composable
private fun historyFailureMessage(
    reason: AppOpHistoryFailureReason,
): String = stringResource(
    when (reason) {
        AppOpHistoryFailureReason.BACKEND_UNAVAILABLE ->
            R.string.history_failure_backend

        AppOpHistoryFailureReason.COMMAND_FAILED ->
            R.string.history_failure_command

        AppOpHistoryFailureReason.COMMAND_TIMED_OUT ->
            R.string.history_failure_timeout
    },
)

internal val HistoryContentPadding = PaddingValues(
    horizontal = 20.dp,
    vertical = 14.dp,
)

private fun historyPermissionItemKey(operationName: String): String =
    "$HISTORY_PERMISSION_ITEM_KEY_PREFIX$operationName"

private const val HISTORY_PERMISSION_ITEM_KEY_PREFIX =
    "history_permission:"
private const val HISTORY_DRAG_SCROLL_EDGE_PX = 96
private const val HISTORY_DRAG_SCROLL_STEP_PX = 24f

private val ForegroundUidStates = setOf("top", "fg", "fgs")
