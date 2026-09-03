package co.chinho.readabilityreader.ui.feeds

/**
 * Collapsed ids are tracked rather than expanded ones so a group that appears in a later sync
 * defaults to expanded instead of silently rendering collapsed.
 */
fun isGroupExpanded(collapsedIds: List<Long>, groupId: Long): Boolean =
    !collapsedIds.contains(groupId)

fun toggleGroupCollapsed(collapsedIds: List<Long>, groupId: Long): List<Long> =
    if (collapsedIds.contains(groupId)) collapsedIds - groupId else collapsedIds + groupId
