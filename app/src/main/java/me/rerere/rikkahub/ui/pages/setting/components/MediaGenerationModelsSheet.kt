package me.rerere.rikkahub.ui.pages.setting.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.FilledTonalIconToggleButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.rerere.hugeicons.HugeIcons
import me.rerere.hugeicons.stroke.Add01
import me.rerere.hugeicons.stroke.Refresh01
import me.rerere.hugeicons.stroke.Tick01
import me.rerere.mediagen.model.MediaGenerationModel
import me.rerere.mediagen.model.MediaKind
import me.rerere.mediagen.provider.MediaGenerationManager
import me.rerere.mediagen.provider.MediaGenerationProviderSetting
import me.rerere.mediagen.provider.RemoteMediaModel
import me.rerere.rikkahub.R
import me.rerere.rikkahub.ui.components.ui.AutoAIIcon
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceButtons
import me.rerere.rikkahub.ui.components.ui.ConnectedChoiceLabel
import me.rerere.rikkahub.ui.components.ui.Tag
import me.rerere.rikkahub.ui.components.ui.TagType
import me.rerere.rikkahub.ui.components.ui.cardGroupItemShape
import org.koin.compose.koinInject

private enum class RemoteMediaKindFilter { ALL, IMAGE, VIDEO }

@Composable
internal fun MediaGenerationRemoteModelsSheet(
    show: Boolean,
    setting: MediaGenerationProviderSetting,
    onDismissRequest: () -> Unit,
    onUpdateModels: (List<MediaGenerationModel>) -> Unit,
) {
    if (!show) return

    val manager = koinInject<MediaGenerationManager>()
    var refreshTrigger by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var remoteModels by remember { mutableStateOf<List<RemoteMediaModel>>(emptyList()) }
    var filterText by remember { mutableStateOf("") }
    var kindFilter by remember { mutableStateOf(RemoteMediaKindFilter.ALL) }
    var recommendedOnly by remember { mutableStateOf(true) }

    LaunchedEffect(show, refreshTrigger, setting.apiKey, setting.baseUrl) {
        loading = true
        errorMessage = null
        manager.listModels(setting)
            .onSuccess { fetched ->
                remoteModels = fetched
                recommendedOnly = fetched.any { it.isRecommendedMedia }
            }
            .onFailure { error ->
                remoteModels = emptyList()
                errorMessage = error.message ?: error.javaClass.simpleName
            }
        loading = false
    }

    val hasMixedNoise = remember(remoteModels) {
        remoteModels.any { it.isRecommendedMedia } && remoteModels.any { !it.isRecommendedMedia }
    }
    val supportedKinds = remember(setting.supportedKinds) {
        MediaKind.entries.filter { it in setting.supportedKinds }
    }
    val filteredModels = remember(remoteModels, filterText, kindFilter, recommendedOnly, hasMixedNoise) {
        val query = filterText.trim().lowercase()
        remoteModels.filter { item ->
            val passRecommended = !hasMixedNoise || !recommendedOnly || item.isRecommendedMedia
            val passKind = when (kindFilter) {
                RemoteMediaKindFilter.ALL -> true
                RemoteMediaKindFilter.IMAGE -> item.kind == MediaKind.IMAGE
                RemoteMediaKindFilter.VIDEO -> item.kind == MediaKind.VIDEO
            }
            val passQuery = query.isEmpty() ||
                item.modelId.lowercase().contains(query) ||
                item.displayName.lowercase().contains(query)
            passRecommended && passKind && passQuery
        }
    }

    val selectedModels = setting.models
    val unselectedCount = filteredModels.count { remote ->
        selectedModels.none { it.modelId == remote.modelId }
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.9f)
                .padding(8.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.setting_provider_page_avaliable_models),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { refreshTrigger++ },
                    enabled = !loading,
                ) {
                    Icon(HugeIcons.Refresh01, contentDescription = null)
                }
                if (filteredModels.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            if (unselectedCount > 0) {
                                val existingIds = selectedModels.mapTo(hashSetOf()) { it.modelId }
                                val toAdd = filteredModels
                                    .filter { it.modelId !in existingIds }
                                    .map {
                                        MediaGenerationModel(
                                            modelId = it.modelId,
                                            kind = it.kind,
                                            displayName = it.displayName,
                                        )
                                    }
                                onUpdateModels(selectedModels + toAdd)
                            } else {
                                val removeIds = filteredModels.mapTo(hashSetOf()) { it.modelId }
                                onUpdateModels(selectedModels.filterNot { it.modelId in removeIds })
                            }
                        },
                    ) {
                        Text(
                            if (unselectedCount > 0) {
                                stringResource(R.string.setting_provider_page_select_all, unselectedCount)
                            } else {
                                stringResource(R.string.setting_provider_page_deselect_models)
                            }
                        )
                    }
                }
            }

            if (supportedKinds.size > 1 || hasMixedNoise) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (supportedKinds.size > 1) {
                        val filterOptions = listOf(RemoteMediaKindFilter.ALL) + supportedKinds.map { kind ->
                            when (kind) {
                                MediaKind.IMAGE -> RemoteMediaKindFilter.IMAGE
                                MediaKind.VIDEO -> RemoteMediaKindFilter.VIDEO
                            }
                        }
                        ConnectedChoiceButtons(
                            options = filterOptions,
                            selected = kindFilter,
                            onSelect = { kindFilter = it },
                            modifier = Modifier.weight(1f),
                        ) { option, selected ->
                            val label = when (option) {
                                RemoteMediaKindFilter.ALL -> "All"
                                RemoteMediaKindFilter.IMAGE -> MediaKind.IMAGE.label
                                RemoteMediaKindFilter.VIDEO -> MediaKind.VIDEO.label
                            }
                            ConnectedChoiceLabel(text = label, selected = selected)
                        }
                    }
                    if (hasMixedNoise) {
                        Tag(
                            type = if (recommendedOnly) TagType.INFO else TagType.DEFAULT,
                            onClick = { recommendedOnly = !recommendedOnly },
                        ) {
                            Text(if (recommendedOnly) "Media" else "All (${remoteModels.size})")
                        }
                    }
                }
            }

            if (loading) {
                LinearWavyProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                )
            }

            errorMessage?.let { message ->
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(12.dp),
                    )
                }
            }

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(ListItemDefaults.SegmentedGap),
                contentPadding = PaddingValues(8.dp),
            ) {
                itemsIndexed(filteredModels, key = { _, item -> item.modelId }) { index, item ->
                    Surface(
                        shape = cardGroupItemShape(index, filteredModels.size),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, end = 8.dp, bottom = 8.dp),
                        ) {
                            AutoAIIcon(item.modelId, Modifier.size(32.dp))
                            Column(
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = item.modelId,
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Tag(type = if (item.kind == MediaKind.VIDEO) TagType.INFO else TagType.DEFAULT) {
                                        Text(item.kind.label)
                                    }
                                    if (item.displayName.isNotBlank() && item.displayName != item.modelId) {
                                        Text(
                                            text = item.displayName,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                    }
                                }
                            }
                            val added = selectedModels.any { it.modelId == item.modelId }
                            FilledTonalIconToggleButton(
                                checked = added,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onUpdateModels(
                                            selectedModels + MediaGenerationModel(
                                                modelId = item.modelId,
                                                kind = item.kind,
                                                displayName = item.displayName,
                                            )
                                        )
                                    } else {
                                        onUpdateModels(selectedModels.filterNot { it.modelId == item.modelId })
                                    }
                                },
                            ) {
                                Icon(if (added) HugeIcons.Tick01 else HugeIcons.Add01, contentDescription = null)
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = filterText,
                onValueChange = { filterText = it },
                label = { Text(stringResource(R.string.setting_provider_page_filter_placeholder)) },
                placeholder = { Text("sora-2 / veo-3 / gpt-image") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
        }
    }
}
