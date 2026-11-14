package me.rerere.rikkahub.ui.pages.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.surfaceColorAtElevation
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import me.rerere.ai.core.MessageRole
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.ArrowDown01
import me.rerere.hugeicons.stroke.ArrowUp01
import me.rerere.hugeicons.stroke.Cancel01
import me.rerere.rikkahub.R
import me.rerere.rikkahub.data.model.Conversation
import me.rerere.rikkahub.data.model.MessageNode
import kotlin.math.abs

// 消息列表上浮着的小组件：底部的建议回复、侧边的快速跳转与对话地图

@Composable
internal fun ChatSuggestionsRow(
    modifier: Modifier = Modifier,
    conversation: Conversation,
    onClickSuggestion: (String) -> Unit
) {
    LazyRow(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items(conversation.chatSuggestions) { suggestion ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        onClickSuggestion(suggestion)
                    }
                    .background(MaterialTheme.colorScheme.surfaceColorAtElevation(2.dp))
                    .padding(vertical = 4.dp, horizontal = 8.dp),
            ) {
                Text(
                    text = suggestion,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

@Composable
internal fun BoxScope.MessageJumper(
    show: Boolean,
    onLeft: Boolean,
    scope: CoroutineScope,
    state: LazyListState,
    conversation: Conversation,
    onJumpToMessage: (Int) -> Unit,
    onToggleFavorite: ((MessageNode) -> Unit)? = null,
) {
    var showMapDialog by rememberSaveable { mutableStateOf(false) }

    val nodeCount = conversation.messageNodes.size
    val currentMessageIndex by remember(state, nodeCount) {
        derivedStateOf {
            if (nodeCount <= 0) {
                0
            } else if (!state.canScrollForward) {
                nodeCount - 1
            } else {
                val layout = state.layoutInfo
                val viewportCenter = (layout.viewportStartOffset + layout.viewportEndOffset) / 2
                val centerItem = layout.visibleItemsInfo
                    .filter { it.index in 0 until nodeCount }
                    .minByOrNull { abs((it.offset + it.size / 2) - viewportCenter) }
                (centerItem?.index ?: state.firstVisibleItemIndex).coerceIn(0, nodeCount - 1)
            }
        }
    }

    val progressFraction = if (nodeCount <= 1) {
        1f
    } else {
        (currentMessageIndex.toFloat() / (nodeCount - 1)).coerceIn(0f, 1f)
    }
    val dotBias by animateFloatAsState(
        targetValue = progressFraction * 2f - 1f,
        label = "MessageMapDotBias",
    )

    AnimatedVisibility(
        visible = show,
        modifier = Modifier.align(if (onLeft) Alignment.CenterStart else Alignment.CenterEnd),
        enter = fadeIn() + slideInHorizontally(
            initialOffsetX = { if (onLeft) -it else it },
        ),
        exit = fadeOut() + slideOutHorizontally(
            targetOffsetX = { if (onLeft) -it else it },
        )
    ) {
        val mapDescription = stringResource(R.string.chat_page_conversation_map)
        Column(
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Operit AI 风格常驻胶囊触发器：点一下立刻打开「跳转到消息」弹窗
            Surface(
                onClick = { showMapDialog = true },
                shape = RoundedCornerShape(50),
                tonalElevation = 4.dp,
                shadowElevation = 2.dp,
                border = BorderStroke(
                    width = 0.75.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                ),
                color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.84f),
                modifier = Modifier
                    .width(24.dp)
                    .height(54.dp)
                    .semantics { contentDescription = mapDescription }
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .width(1.5.dp)
                            .fillMaxHeight()
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f),
                                shape = CircleShape,
                            )
                    )
                    Box(
                        modifier = Modifier
                            .align(BiasAlignment(0f, dotBias))
                            .size(7.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primary,
                                shape = CircleShape,
                            )
                    )
                }
            }

            if (state.canScrollForward || state.canScrollBackward) {
                val jumpToBottom = state.canScrollForward
                Surface(
                    onClick = {
                        scope.launch {
                            if (jumpToBottom) {
                                val target = (state.layoutInfo.totalItemsCount - 1).coerceAtLeast(0)
                                state.scrollToItem(target)
                            } else {
                                state.scrollToItem(0)
                            }
                        }
                    },
                    shape = CircleShape,
                    tonalElevation = 4.dp,
                    shadowElevation = 2.dp,
                    border = BorderStroke(
                        width = 0.75.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    ),
                    color = MaterialTheme.colorScheme.surfaceColorAtElevation(4.dp).copy(alpha = 0.84f),
                    modifier = Modifier.size(28.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (jumpToBottom) HugeIcons.ArrowDown01 else HugeIcons.ArrowUp01,
                            contentDescription = stringResource(
                                if (jumpToBottom) R.string.chat_page_scroll_to_bottom
                                else R.string.chat_page_scroll_to_top
                            ),
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    if (showMapDialog && conversation.messageNodes.isNotEmpty()) {
        ChatMessageMapDialog(
            nodes = conversation.messageNodes,
            activeMessageIndex = currentMessageIndex,
            onJumpToMessage = { index ->
                showMapDialog = false
                onJumpToMessage(index)
            },
            onToggleFavorite = onToggleFavorite,
            onDismiss = { showMapDialog = false },
        )
    }
}

private data class MessageMapEntry(
    val index: Int,
    val node: MessageNode,
    val isUser: Boolean,
    val previewText: String,
    val rawText: String,
    val charCount: Int,
)

@Composable
private fun ChatMessageMapDialog(
    nodes: List<MessageNode>,
    activeMessageIndex: Int,
    onJumpToMessage: (Int) -> Unit,
    onToggleFavorite: ((MessageNode) -> Unit)?,
    onDismiss: () -> Unit,
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var onlyFavorite by rememberSaveable { mutableStateOf(false) }

    val mediaOnlyText = stringResource(R.string.chat_message_map_media_only)
    val roleUserText = stringResource(R.string.chat_message_map_role_user)
    val roleAiText = stringResource(R.string.chat_message_map_role_ai)
    val trimmedQuery = searchQuery.trim()

    val entries = remember(nodes, mediaOnlyText) {
        nodes.mapIndexedNotNull { index, node ->
            val message = node.messages.getOrNull(node.selectIndex) ?: node.messages.firstOrNull()
                ?: return@mapIndexedNotNull null
            val raw = message.toText()
            val cleaned = message.toPreviewText()
            val preview = when {
                cleaned.isNotEmpty() -> cleaned
                message.parts.isNotEmpty() -> mediaOnlyText
                else -> ""
            }
            MessageMapEntry(
                index = index,
                node = node,
                isUser = message.role == MessageRole.USER,
                previewText = preview,
                rawText = raw,
                charCount = raw.length,
            )
        }
    }

    val filteredEntries = remember(entries, trimmedQuery, onlyFavorite) {
        entries.filter { entry ->
            val matchFavorite = !onlyFavorite || entry.node.isFavorite
            val matchQuery = trimmedQuery.isEmpty() ||
                entry.previewText.contains(trimmedQuery, ignoreCase = true) ||
                entry.rawText.contains(trimmedQuery, ignoreCase = true)
            matchFavorite && matchQuery
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val targetPos = filteredEntries.indexOfFirst { it.index == activeMessageIndex }
        if (targetPos >= 0) {
            listState.scrollToItem((targetPos - 2).coerceAtLeast(0))
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.90f)
                .widthIn(max = 460.dp)
                .heightIn(min = 240.dp, max = 600.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            shadowElevation = 8.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top,
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = stringResource(R.string.chat_message_map_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = stringResource(
                                R.string.chat_message_map_current_position,
                                (activeMessageIndex + 1).coerceIn(1, nodes.size.coerceAtLeast(1)),
                                nodes.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    TextButton(
                        onClick = onDismiss,
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                    ) {
                        Text(text = stringResource(R.string.skill_detail_page_close))
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        placeholder = {
                            Text(
                                text = stringResource(R.string.history_page_search_messages),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        trailingIcon = if (searchQuery.isNotEmpty()) {
                            {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(
                                        imageVector = HugeIcons.Cancel01,
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        } else {
                            null
                        },
                    )

                    Surface(
                        onClick = { onlyFavorite = !onlyFavorite },
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(14.dp),
                        color = if (onlyFavorite) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceContainerLow
                        },
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (onlyFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                            },
                        ),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (onlyFavorite) StarFilledIcon else StarOutlineIcon,
                                contentDescription = stringResource(R.string.chat_message_map_filter_favorite),
                                modifier = Modifier.size(20.dp),
                                tint = if (onlyFavorite) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.chat_message_map_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                if (filteredEntries.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(160.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.chat_message_map_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(bottom = 4.dp),
                    ) {
                        items(
                            items = filteredEntries,
                            key = { it.node.id.toString() },
                        ) { entry ->
                            val isActive = entry.index == activeMessageIndex
                            MessageMapCard(
                                entry = entry,
                                isActive = isActive,
                                roleLabel = if (entry.isUser) roleUserText else roleAiText,
                                searchQuery = trimmedQuery,
                                onClick = { onJumpToMessage(entry.index) },
                                onToggleFavorite = if (onToggleFavorite != null) {
                                    { onToggleFavorite(entry.node) }
                                } else {
                                    null
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MessageMapCard(
    entry: MessageMapEntry,
    isActive: Boolean,
    roleLabel: String,
    searchQuery: String,
    onClick: () -> Unit,
    onToggleFavorite: (() -> Unit)?,
) {
    val pillContainer = if (entry.isUser) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
    } else {
        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.72f)
    }
    val pillContent = if (entry.isUser) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }

    Surface(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = if (isActive) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.20f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isActive) 1.5.dp else 0.75.dp,
            color = if (isActive) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.50f)
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Column(
                modifier = Modifier.widthIn(min = 44.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = (entry.index + 1).toString(),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium,
                        color = if (isActive) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        },
                    )
                    val favDesc = stringResource(
                        if (entry.node.isFavorite) R.string.chat_message_remove_favorite
                        else R.string.chat_message_add_favorite
                    )
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .clickable(enabled = onToggleFavorite != null) {
                                onToggleFavorite?.invoke()
                            }
                            .padding(2.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = if (entry.node.isFavorite) StarFilledIcon else StarOutlineIcon,
                            contentDescription = favDesc,
                            modifier = Modifier.size(14.dp),
                            tint = if (entry.node.isFavorite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                            },
                        )
                    }
                }
                Text(
                    text = roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Surface(
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(12.dp),
                color = pillContainer,
            ) {
                Text(
                    text = buildHighlightedText(
                        text = entry.previewText,
                        query = searchQuery,
                        highlightColor = MaterialTheme.colorScheme.tertiaryContainer,
                        highlightTextColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = pillContent,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                )
            }

            Text(
                text = entry.charCount.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private val StarOutlineIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StarOutline",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.EvenOdd,
        ) {
            moveTo(22f, 9.24f)
            lineTo(14.81f, 8.62f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f)
            lineTo(5.82f, 21f)
            lineTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineTo(16.55f, 13.97f)
            lineTo(22f, 9.24f)
            close()
            moveTo(12f, 15.4f)
            lineTo(8.24f, 17.67f)
            lineTo(9.24f, 13.39f)
            lineTo(5.92f, 10.51f)
            lineTo(10.3f, 10.13f)
            lineTo(12f, 6.1f)
            lineTo(13.71f, 10.14f)
            lineTo(18.09f, 10.52f)
            lineTo(14.77f, 13.4f)
            lineTo(15.77f, 17.68f)
            lineTo(12f, 15.4f)
            close()
        }
    }.build()
}

private val StarFilledIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "StarFilled",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        path(
            fill = SolidColor(Color.Black),
            pathFillType = PathFillType.NonZero,
        ) {
            moveTo(12f, 17.27f)
            lineTo(18.18f, 21f)
            lineTo(16.54f, 13.97f)
            lineTo(22f, 9.24f)
            lineTo(14.81f, 8.63f)
            lineTo(12f, 2f)
            lineTo(9.19f, 8.63f)
            lineTo(2f, 9.24f)
            lineTo(7.46f, 13.97f)
            lineTo(5.82f, 21f)
            close()
        }
    }.build()
}
