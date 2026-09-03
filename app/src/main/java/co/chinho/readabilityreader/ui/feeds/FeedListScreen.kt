package co.chinho.readabilityreader.ui.feeds

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.KeyboardDoubleArrowDown
import androidx.compose.material.icons.filled.KeyboardDoubleArrowUp
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import co.chinho.readabilityreader.domain.model.Feed
import co.chinho.readabilityreader.domain.model.Group
import co.chinho.readabilityreader.ui.components.EInkPageButtonDirection
import co.chinho.readabilityreader.ui.components.launchLazyListPageScroll
import co.chinho.readabilityreader.ui.components.rememberEInkPageButtonModifier
import co.chinho.readabilityreader.ui.components.rememberTwoTapConfirm
import co.chinho.readabilityreader.ui.components.SyncStatus
import co.chinho.readabilityreader.ui.components.SyncStatusBottomBar
import co.chinho.readabilityreader.ui.theme.AppTheme
import co.chinho.readabilityreader.ui.theme.LocalEInkMode
import co.chinho.readabilityreader.R
import coil.compose.AsyncImage
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeedListScreen(
    onFeedClick: (Long?) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onSavedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedFeedId: Long? = null,
    selectedGroupId: Long? = null,
    showSyncStatusBottomBar: Boolean = true,
    onFeedPaneToggleClick: (() -> Unit)? = null,
    viewModel: FeedListViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val savedCount by viewModel.savedCount.collectAsStateWithLifecycle()
    val hideSavedWhenEmpty by viewModel.hideSavedWhenEmpty.collectAsStateWithLifecycle()
    val showSavedArticlesSection by viewModel.showSavedArticlesSection.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val dockedCategoryCount by viewModel.dockedCategoryCount.collectAsStateWithLifecycle()

    FeedListContent(
        uiState = uiState,
        isOffline = isOffline,
        savedCount = savedCount,
        hideSavedWhenEmpty = hideSavedWhenEmpty,
        showSavedArticlesSection = showSavedArticlesSection,
        syncStatus = syncStatus,
        onRefresh = viewModel::refresh,
        onFeedClick = onFeedClick,
        onCategoryClick = onCategoryClick,
        onMarkFeedRead = viewModel::markFeedRead,
        onSavedClick = onSavedClick,
        onSettingsClick = onSettingsClick,
        selectedFeedId = selectedFeedId,
        selectedGroupId = selectedGroupId,
        showSyncStatusBottomBar = showSyncStatusBottomBar,
        onFeedPaneToggleClick = onFeedPaneToggleClick,
        dockedCategoryCount = dockedCategoryCount,
    )
}

@Composable
private fun FeedListContent(
    uiState: FeedListUiState,
    isOffline: Boolean,
    savedCount: Int,
    hideSavedWhenEmpty: Boolean = false,
    showSavedArticlesSection: Boolean = true,
    syncStatus: SyncStatus,
    onRefresh: () -> Unit,
    onFeedClick: (Long?) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onMarkFeedRead: (Long) -> Unit,
    onSavedClick: () -> Unit,
    onSettingsClick: () -> Unit,
    selectedFeedId: Long? = null,
    selectedGroupId: Long? = null,
    showSyncStatusBottomBar: Boolean = true,
    onFeedPaneToggleClick: (() -> Unit)? = null,
    dockedCategoryCount: Int = MaxDockedPerEnd,
) {
    val isEInk = LocalEInkMode.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var scrollAreaHeightPx by remember { mutableStateOf(0) }
    val pageButtonModifier = rememberEInkPageButtonModifier(enabled = isEInk) { direction ->
        coroutineScope.launchLazyListPageScroll(
            state = listState,
            direction = direction,
            viewportHeightPx = scrollAreaHeightPx,
            pageFraction = 0.6f,
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            FeedListTopBar(
                onRefresh = onRefresh,
                onFeedPaneToggleClick = onFeedPaneToggleClick,
            )
        },
        bottomBar = {
            if (showSyncStatusBottomBar) {
                SyncStatusBottomBar(
                    syncStatus = syncStatus,
                    isEInk = isEInk,
                    onSettingsClick = onSettingsClick,
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(MaterialTheme.colorScheme.background)
        ) {
            val allUnread = (uiState as? FeedListUiState.Success)?.allUnreadCount ?: 0
            PinnedHeaderRows(
                allUnreadCount = allUnread,
                savedCount = savedCount,
                showSavedRow = showSavedArticlesSection && !(hideSavedWhenEmpty && savedCount == 0),
                selectedFeedId = selectedFeedId,
                onAllSourcesClick = { onFeedClick(null) },
                onSavedClick = onSavedClick,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                thickness = 1.dp,
                modifier = Modifier
                    .padding(top = 8.dp)
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(pageButtonModifier)
                    .onSizeChanged { scrollAreaHeightPx = it.height }
            ) {
                when (val state = uiState) {
                    is FeedListUiState.Loading -> {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (isEInk) {
                                Text("Loading...", style = MaterialTheme.typography.bodyLarge)
                            } else {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    is FeedListUiState.Error -> {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(text = state.message, color = MaterialTheme.colorScheme.error)
                            Button(onClick = onRefresh, modifier = Modifier.padding(top = 16.dp)) {
                                Text("Retry Sync")
                            }
                        }
                    }

                    is FeedListUiState.Success -> {
                        ScrollableFeedList(
                            groups = state.groups,
                            listState = listState,
                            modifier = Modifier.fillMaxSize(),
                            onFeedClick = onFeedClick,
                            onCategoryClick = onCategoryClick,
                            onMarkFeedRead = onMarkFeedRead,
                            selectedFeedId = selectedFeedId,
                            selectedGroupId = selectedGroupId,
                            dockedCategoryCount = dockedCategoryCount,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FeedListTopBar(
    onRefresh: () -> Unit,
    onFeedPaneToggleClick: (() -> Unit)? = null,
) {
    val isEInk = LocalEInkMode.current
    Surface(color = MaterialTheme.colorScheme.background) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(start = if (onFeedPaneToggleClick == null) 16.dp else 8.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onFeedPaneToggleClick != null) {
                IconButton(onClick = onFeedPaneToggleClick) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Collapse feed sources",
                    )
                }
                Spacer(Modifier.width(4.dp))
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = if (onFeedPaneToggleClick == null) 4.dp else 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.mipmap.ic_launcher_foreground),
                    contentDescription = "ReadabilityRSS Logo",
                    modifier = Modifier.size(32.dp),
                    colorFilter = if (isEInk) ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) }) else null
                )
                Text(
                    text = "ReadabilityRSS",
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = androidx.compose.ui.text.font.FontFamily(androidx.compose.ui.text.font.Font(R.font.ibm_plex_mono_medium)),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 0.04.em,
                        color = if (isEInk) MaterialTheme.colorScheme.onBackground else Color(0xFFD4943E)
                    )
                )
            }
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = "Refresh")
            }
        }
    }
}

@Composable
private fun PinnedHeaderRows(
    allUnreadCount: Int,
    savedCount: Int,
    showSavedRow: Boolean,
    selectedFeedId: Long?,
    onAllSourcesClick: () -> Unit,
    onSavedClick: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        PinnedRow(
            title = "All Sources",
            count = allUnreadCount,
            bold = true,
            selected = selectedFeedId == 0L,
            onClick = onAllSourcesClick,
        )
        if (showSavedRow) {
            PinnedRow(
                title = "Saved",
                count = savedCount,
                bold = false,
                selected = false,
                onClick = onSavedClick,
            )
        }
    }
}

@Composable
private fun PinnedRow(
    title: String,
    count: Int,
    bold: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (selected) accentColor else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium.copy(
                fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal
            ),
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier
                .weight(1f)
        )
        if (count > 0) {
            UnreadBadge(count = count)
        }
        }
    }
}

private val LiveHeaderHeight = 40.dp
private val DockedHeaderHeight = 28.dp
private val DockedHeaderLabelSize = 14.sp
private const val DockedContentAlpha = 0.55f

@Composable
private fun ScrollableFeedList(
    groups: List<Group>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
    onFeedClick: (Long?) -> Unit,
    onCategoryClick: (Long) -> Unit,
    onMarkFeedRead: (Long) -> Unit,
    selectedFeedId: Long? = null,
    selectedGroupId: Long? = null,
    dockedCategoryCount: Int = MaxDockedPerEnd,
) {
    var collapsedGroupIds by rememberSaveable { mutableStateOf(listOf<Long>()) }
    val isEInk = LocalEInkMode.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val liveHeaderPx = with(density) { LiveHeaderHeight.roundToPx() }
    // Whole pixels only: the rows are measured at a rounded height, so a fractional pitch drifts
    // against them down the stack and clips the rule off the row nearest the list.
    val dockedHeaderPx = with(density) {
        (if (isEInk) LiveHeaderHeight else DockedHeaderHeight).roundToPx().toFloat()
    }

    // The header's item index depends on which groups are expanded, so it is derived from the same
    // data that builds the list rather than tracked separately.
    val headerIndices = remember(groups, collapsedGroupIds) {
        var index = 0
        buildMap {
            groups.forEach { group ->
                put(group.id, index)
                index++
                if (isGroupExpanded(collapsedGroupIds, group.id)) index += group.feeds.size
            }
        }
    }
    val groupsById = remember(groups) { groups.associateBy { it.id } }

    // Discrete only: this recomposes when dock membership changes, not on every scroll frame.
    val docks by remember(groups, headerIndices, dockedCategoryCount) {
        derivedStateOf {
            val info = listState.layoutInfo
            val visible = info.visibleItemsInfo.associateBy { it.index }
            val slots = groups.map { group ->
                val index = headerIndices.getValue(group.id)
                val item = visible[index]
                HeaderSlot(group.id, index, item?.offset, item?.let { it.offset + it.size })
            }
            computeGroupDocks(
                slots = slots,
                firstVisibleItemIndex = listState.firstVisibleItemIndex,
                viewportTopPx = info.viewportStartOffset,
                viewportBottomPx = info.viewportEndOffset,
                liveHeaderHeightPx = liveHeaderPx,
                maxPerDock = dockedCategoryCount,
            )
        }
    }

    fun scrollTo(groupId: Long) {
        val index = headerIndices[groupId] ?: return
        scope.launch {
            if (isEInk) listState.scrollToItem(index) else listState.animateScrollToItem(index)
        }
    }

    val enteringAbove: () -> Float = {
        if (isEInk) 0f else {
            val id = docks.crossingAbove
            val index = id?.let { headerIndices[it] }
            val item = index?.let { i -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i } }
            if (item == null) 0f
            // item.size, not the constant: the header's real height includes its divider, and a
            // denominator that understates it makes the fraction reach 1 before the row has left.
            else crossingFraction(item.offset, listState.layoutInfo.viewportStartOffset, item.size, DockSide.Above)
        }
    }

    val enteringBelow: () -> Float = {
        if (isEInk) 0f else {
            val id = docks.crossingBelow
            val index = id?.let { headerIndices[it] }
            val item = index?.let { i -> listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == i } }
            if (item == null) 0f
            else crossingFraction(item.offset, listState.layoutInfo.viewportEndOffset, item.size, DockSide.Below)
        }
    }

    val aboveRender = remember(docks, isEInk) {
        docks.aboveRows + listOfNotNull(docks.crossingAbove.takeIf { !isEInk })
    }
    val belowRender = remember(docks, isEInk) {
        listOfNotNull(docks.crossingBelow.takeIf { !isEInk }) + docks.belowRows
    }

    val crossingAlpha: (Long) -> Float = { groupId ->
        when (groupId) {
            docks.crossingAbove -> (1f - enteringAbove() * 1.6f).coerceAtLeast(0f)
            docks.crossingBelow -> (1f - enteringBelow() * 1.6f).coerceAtLeast(0f)
            else -> 1f
        }
    }

    Column(modifier = modifier) {
        CategoryDock(
            side = DockSide.Above,
            rowIds = aboveRender,
            dockedCount = docks.aboveCount,
            cap = dockedCategoryCount,
            groupsById = groupsById,
            dockedHeaderPx = dockedHeaderPx,
            selectedGroupId = selectedGroupId,
            onCategoryClick = onCategoryClick,
            onScrollToGroup = ::scrollTo,
            crossingId = docks.crossingAbove.takeIf { !isEInk },
            enteringFraction = enteringAbove,
            enteringHeightPx = { dockedHeaderPx + (liveHeaderPx - dockedHeaderPx) * (1f - enteringAbove()) },
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(bottom = 8.dp)
        ) {
            groups.forEachIndexed { index, group ->
                val isExpanded = isGroupExpanded(collapsedGroupIds, group.id)
                item(key = "group_${group.id}") {
                    GroupHeaderRow(
                        group = group,
                        isExpanded = isExpanded,
                        selected = selectedGroupId == group.id,
                        showTopDivider = !(index == 0 && aboveRender.isEmpty()),
                        onCategoryClick = { onCategoryClick(group.id) },
                        onToggle = {
                            collapsedGroupIds = toggleGroupCollapsed(collapsedGroupIds, group.id)
                        },
                        modifier = Modifier.graphicsLayer {
                            alpha = crossingAlpha(group.id)
                        }
                    )
                }
                if (isExpanded) {
                    items(group.feeds, key = { it.id }) { feed ->
                        FeedRow(
                            feed = feed,
                            selected = selectedFeedId == feed.id,
                            onClick = { onFeedClick(feed.id) },
                            onMarkRead = { onMarkFeedRead(feed.id) }
                        )
                    }
                }
            }
        }

        CategoryDock(
            side = DockSide.Below,
            rowIds = belowRender,
            dockedCount = docks.belowCount,
            cap = dockedCategoryCount,
            groupsById = groupsById,
            dockedHeaderPx = dockedHeaderPx,
            selectedGroupId = selectedGroupId,
            onCategoryClick = onCategoryClick,
            onScrollToGroup = ::scrollTo,
            crossingId = docks.crossingBelow.takeIf { !isEInk },
            enteringFraction = enteringBelow,
            enteringHeightPx = { dockedHeaderPx + (liveHeaderPx - dockedHeaderPx) * (1f - enteringBelow()) },
        )
    }
}

@Composable
private fun CategoryDock(
    side: DockSide,
    rowIds: List<Long>,
    dockedCount: Int,
    cap: Int,
    groupsById: Map<Long, Group>,
    dockedHeaderPx: Float,
    selectedGroupId: Long?,
    onCategoryClick: (Long) -> Unit,
    onScrollToGroup: (Long) -> Unit,
    crossingId: Long? = null,
    enteringFraction: () -> Float = { 0f },
    enteringHeightPx: () -> Float = { dockedHeaderPx },
) {
    if (rowIds.isEmpty() || cap <= 0) return

    Layout(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .clipToBounds(),
        content = {
            rowIds.forEachIndexed { index, id ->
                val group = groupsById[id] ?: return@forEachIndexed
                val isCrossing = (id == crossingId)
                GroupHeaderRow(
                    group = group,
                    isExpanded = true,
                    selected = selectedGroupId == group.id,
                    onCategoryClick = { onCategoryClick(group.id) },
                    onToggle = {},
                    dock = side,
                    // A row's rule hangs below its fixed-height box. The top dock renders one row
                    // more than it can show and anchors the stack to its bottom, so the extra row
                    // sits entirely above the dock with only its rule landing on the top edge —
                    // a second hairline directly under the All Sources block.
                    showBottomDivider = !(side == DockSide.Above && index == 0 && dockedCount > cap),
                    onScrollToGroup = { onScrollToGroup(group.id) },
                    modifier = if (isCrossing) {
                        Modifier.graphicsLayer {
                            alpha = (enteringFraction() * 1.6f).coerceAtMost(1f)
                        }
                    } else {
                        Modifier
                    }
                )
            }
        }
    ) { measurables, constraints ->
        val t = enteringFraction()
        val hd = dockedHeaderPx
        val he = enteringHeightPx()
        val dockHeight = (minOf(dockedCount + t, cap.toFloat()) * hd)
        val crossingIndex = when {
            t <= 0f -> -1
            side == DockSide.Above -> measurables.lastIndex
            else -> 0
        }
        // Pin every row to exactly the height the anchor arithmetic below assumes. GroupHeaderRow
        // puts its divider outside the fixed-height Row, so its intrinsic height exceeds the
        // constant by that hairline; measuring loosely lets the difference accumulate down the
        // stack and clips the row nearest the list.
        val placeables = measurables.mapIndexed { index, measurable ->
            val rowPx = (if (index == crossingIndex) he else hd).roundToInt().coerceAtLeast(0)
            measurable.measure(constraints.copy(minHeight = rowPx, maxHeight = rowPx))
        }

        layout(constraints.maxWidth, dockHeight.toInt().coerceAtLeast(0)) {
            // The stack is anchored so its bottom-most row meets the dock's bottom edge (top dock)
            // or its top-most row meets the top edge (bottom dock). While a header is crossing, the
            // bottom-most row is the entering one and sits at index nDocked; with nothing crossing
            // there is no row at that index, so the anchor drops by one row. Getting this wrong
            // leaves a band of bare background against the list.
            var y = when (side) {
                DockSide.Above -> {
                    val nDocked = if (t > 0f) placeables.size - 1 else placeables.size
                    val base = if (t > 0f) dockHeight - t * he else dockHeight
                    (base - nDocked * hd).toInt()
                }
                DockSide.Below -> if (t > 0f) (-(1f - t) * he).toInt() else 0
            }
            placeables.forEach { placeable ->
                placeable.placeRelative(0, y)
                y += placeable.height
            }
        }
    }
}

@Composable
private fun GroupHeaderRow(
    group: Group,
    isExpanded: Boolean,
    selected: Boolean,
    onCategoryClick: () -> Unit,
    onToggle: () -> Unit,
    dock: DockSide? = null,
    showTopDivider: Boolean = true,
    showBottomDivider: Boolean = true,
    onScrollToGroup: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val isEInk = LocalEInkMode.current
    val accentColor = MaterialTheme.colorScheme.primary
    // On E-Ink the compact row fights the deliberately bold labels and larger minimum type.
    val height = if (dock == null || isEInk) LiveHeaderHeight else DockedHeaderHeight
    val labelSize = if (dock == null || isEInk) 15.sp else DockedHeaderLabelSize
    // Docked rows are chrome, not content, so they sit back from the live list. Not on E-Ink, where
    // reduced contrast costs legibility rather than buying hierarchy.
    val contentColor = MaterialTheme.colorScheme.onSurfaceVariant.let {
        if (dock != null && !isEInk) it.copy(alpha = DockedContentAlpha) else it
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // Nothing at the very top of the scroll area draws its own rule, because the All Sources
        // block already ends in one. Docked rows carry their hairline below instead of above, and
        // the first live header suppresses its own.
        if (dock == null && showTopDivider) GroupHeaderDivider()
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height)
                .clickable { onCategoryClick() }
                .padding(end = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .fillMaxHeight()
                    .background(if (selected) accentColor else Color.Transparent)
            )
            Text(
                text = group.title.uppercase(),
                style = MaterialTheme.typography.labelLarge.copy(
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.8.sp,
                    fontSize = labelSize
                ),
                color = contentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 16.dp)
            )
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .fillMaxHeight()
                    .clickable(onClick = if (dock == null) onToggle else onScrollToGroup),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        dock == DockSide.Above -> Icons.Default.KeyboardDoubleArrowUp
                        dock == DockSide.Below -> Icons.Default.KeyboardDoubleArrowDown
                        isExpanded -> Icons.Default.KeyboardArrowUp
                        else -> Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = when {
                        dock != null -> "Scroll to ${group.title}"
                        isExpanded -> "Collapse ${group.title}"
                        else -> "Expand ${group.title}"
                    },
                    tint = contentColor,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        if (showBottomDivider) GroupHeaderDivider()
    }
}

// Inset to the row's own content: the title's left edge (4dp accent bar + 16dp) and the chevron's
// right edge (2dp row padding + 14dp of the 48dp touch target's own inset).
private val GroupHeaderDividerStartInset = 20.dp
private val GroupHeaderDividerEndInset = 16.dp

@Composable
private fun GroupHeaderDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(
            start = GroupHeaderDividerStartInset,
            end = GroupHeaderDividerEndInset,
        ),
        thickness = 1.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

@Composable
private fun FeedRow(
    feed: Feed,
    selected: Boolean = false,
    onClick: () -> Unit,
    onMarkRead: () -> Unit,
) {
    val isEInk = LocalEInkMode.current
    val accentColor = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 48.dp)
            .clickable { onClick() }
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(if (selected) accentColor else Color.Transparent)
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 25.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
        if (feed.faviconUrl != null) {
            AsyncImage(
                model = feed.faviconUrl,
                contentDescription = "${feed.title} icon",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape),
                colorFilter = if (isEInk) {
                    ColorFilter.colorMatrix(ColorMatrix().apply { setToSaturation(0f) })
                } else null
            )
        } else {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        val backgroundColor = MaterialTheme.colorScheme.background
        var titleOverflows by remember(feed.title) { mutableStateOf(false) }
        Box(modifier = Modifier.weight(1f)) {
            Text(
                text = feed.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Normal
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
                onTextLayout = { result ->
                    val overflow = result.hasVisualOverflow
                    if (overflow != titleOverflows) titleOverflows = overflow
                },
                modifier = Modifier.fillMaxWidth()
            )
            if (titleOverflows) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.75f to Color.Transparent,
                                    1.0f to backgroundColor,
                                )
                            )
                        )
                )
            }
        }

        if (feed.unreadCount > 0) {
            UnreadBadge(
                count = feed.unreadCount,
                onMarkRead = onMarkRead
            )
        }
        }
    }
}

@Composable
private fun UnreadBadge(
    count: Int,
    onMarkRead: (() -> Unit)? = null
) {
    val confirmState = if (onMarkRead != null) {
        rememberTwoTapConfirm(onConfirm = onMarkRead)
    } else null
    val showReadAll = confirmState?.armed == true

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (showReadAll) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.8f)
        } else {
            Color(0xFF373E44)
        },
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .then(
                if (confirmState != null) {
                    Modifier.clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) {
                        confirmState.tap()
                    }
                } else Modifier
            )
    ) {
        Box(
            modifier = Modifier
                .widthIn(min = 56.dp)
                .padding(vertical = 2.dp, horizontal = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = if (showReadAll) "Read all" else count.toString(),
                color = if (showReadAll) {
                    MaterialTheme.colorScheme.onErrorContainer
                } else {
                    Color.White
                },
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            )
        }
    }
}

@Preview(showBackground = true, name = "Light Mode")
@Composable
fun FeedListPreview() {
    AppTheme(isEInkMode = false, isDarkTheme = false) {
        FeedListContent(
            uiState = FeedListUiState.Success(sampleGroups, 42),
            isOffline = false,
            savedCount = 7,
            syncStatus = SyncStatus(totalCount = 1484),
            onRefresh = {},
            onFeedClick = {},
            onCategoryClick = {},
            onMarkFeedRead = {},
            onSavedClick = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "Dark Mode", backgroundColor = 0xFF1C1B1F)
@Composable
fun FeedListDarkModePreview() {
    AppTheme(isEInkMode = false, isDarkTheme = true) {
        FeedListContent(
            uiState = FeedListUiState.Success(sampleGroups, 1355),
            isOffline = true,
            savedCount = 42,
            syncStatus = SyncStatus(isOffline = true, totalCount = 1355),
            onRefresh = {},
            onFeedClick = {},
            onCategoryClick = {},
            onMarkFeedRead = {},
            onSavedClick = {},
            onSettingsClick = {},
        )
    }
}

@Preview(showBackground = true, name = "E-Ink Mode")
@Composable
fun FeedListEInkPreview() {
    AppTheme(isEInkMode = true) {
        FeedListContent(
            uiState = FeedListUiState.Success(sampleGroups, 42),
            isOffline = false,
            savedCount = 7,
            syncStatus = SyncStatus(isSyncing = true, syncedCount = 20, totalCount = 100),
            onRefresh = {},
            onFeedClick = {},
            onCategoryClick = {},
            onMarkFeedRead = {},
            onSavedClick = {},
            onSettingsClick = {},
        )
    }
}

private val sampleFeeds = listOf(
    Feed(1, 1, "The Verge", "https://theverge.com", null, 5, "standard"),
    Feed(2, 1, "Android Central", "https://androidcentral.com", null, 12, "full_image"),
    Feed(3, 2, "Daring Fireball", "https://daringfireball.net", null, 0, "standard"),
    Feed(4, 2, "Stratechery", "https://stratechery.com", null, 3, "standard"),
)

private val sampleGroups = listOf(
    Group(1, "⚛ Technology", sampleFeeds.take(2)),
    Group(2, "✈ Travel", sampleFeeds.drop(2))
)
