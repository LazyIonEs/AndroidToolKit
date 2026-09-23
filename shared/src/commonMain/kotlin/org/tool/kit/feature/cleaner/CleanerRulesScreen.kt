package org.tool.kit.feature.cleaner

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.cleaner.*
import org.tool.kit.shared.generated.resources.*

internal val ruleContentEnter =
    fadeIn(tween(180)) + expandVertically(tween(240), expandFrom = Alignment.Top)
internal val ruleContentExit =
    fadeOut(tween(120)) + shrinkVertically(tween(240), shrinkTowards = Alignment.Top)

/** Crossfade the editor while its measured height changes continuously. */
@Composable
internal fun <T> RuleContentSwap(
    state: T,
    modifier: Modifier = Modifier,
    label: String,
    content: @Composable (T) -> Unit
) {
    AnimatedContent(
        state, modifier, contentAlignment = Alignment.TopStart,
        transitionSpec = {
            (fadeIn(tween(180, delayMillis = 60)) togetherWith fadeOut(tween(120)))
                .using(SizeTransform { _, _ -> tween(240) })
        }, label = label
    ) { content(it) }
}

/** Compact rule summaries, with one rule and one condition editor visible at a time. */
@Composable
fun CleanerRulesScreen(
    state: CleanerRulesUiState,
    viewModel: CleanerRulesViewModel,
    onCancel: () -> Unit,
    onSave: (Boolean) -> Unit = viewModel::save,
) {
    var restore by remember { mutableStateOf(false) }
    var pageMenu by remember { mutableStateOf(false) }
    var switchTarget by remember { mutableStateOf<String?>(null) }
    var expandedRuleId by rememberSaveable { mutableStateOf<String?>(null) }
    var scanSettingsExpanded by rememberSaveable { mutableStateOf(false) }
    val listState = rememberLazyListState()
    var previousRuleIds by remember { mutableStateOf(state.draft.rules.map { it.id }) }
    var loaded by remember { mutableStateOf(!state.loading) }
    val original = remember(state.loading) { state.draft.takeUnless { state.loading } }
    val dirty =
        original != null && (state.draft != original || state.depthText != original.maxDepth.toString())
    val editable = !state.loading && !state.saving
    val validation = state.validation
    val globalIssues = validation.errors.filter {
        it in listOf(
            CleanerRuleIssue.NO_ENABLED_RULES,
            CleanerRuleIssue.INVALID_SCHEMA,
            CleanerRuleIssue.INVALID_ID
        )
    }
    val globalWarnings = validation.warnings.filter {
        it in listOf(
            CleanerRuleIssue.DUPLICATE_NAME,
            CleanerRuleIssue.DUPLICATE_RULE
        )
    }
    LaunchedEffect(state.loading, state.draft.rules.map { it.id }) {
        if (!state.loading) {
            val added = state.draft.rules.indexOfFirst { it.id !in previousRuleIds }
            if (expandedRuleId !in state.draft.rules.map { it.id }) expandedRuleId = null
            if (loaded && added >= 0) {
                expandedRuleId = state.draft.rules[added].id
                // A permanent zero-height status item keeps rule indices stable.
                listState.animateScrollToItem(added + 1)
            }
            previousRuleIds = state.draft.rules.map { it.id }
            loaded = true
        }
    }
    val newName = stringResource(Res.string.cleaner_new_rule)
    Scaffold(
        topBar = {
            Surface {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Row(
                        Modifier.widthIn(max = 960.dp).fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                stringResource(
                                    Res.string.cleaner_rules_count,
                                    state.draft.rules.size,
                                    state.draft.rules.count { it.enabled }),
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                stringResource(Res.string.cleaner_rules_match_any),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        FilledTonalButton(
                            onClick = { viewModel.addRule(newName) },
                            enabled = editable
                        ) {
                            Icon(Icons.Outlined.Add, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.cleaner_add_rule))
                        }
                        Box {
                            IconButton(
                                onClick = { pageMenu = true },
                                enabled = editable,
                                modifier = Modifier.testTag("cleaner-rules-options")
                            ) {
                                Icon(
                                    Icons.Outlined.MoreHoriz,
                                    stringResource(Res.string.cleaner_rules_options)
                                )
                            }
                            DropdownMenu(pageMenu, { pageMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(Res.string.cleaner_restore)) },
                                    leadingIcon = { Icon(Icons.Outlined.Restore, null) },
                                    onClick = { pageMenu = false; restore = true })
                            }
                        }
                    }
                }
            }
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RuleContentSwap(
                            if (validation.valid) dirty else null,
                            Modifier.weight(1f),
                            "rules-draft-status"
                        ) { changed ->
                            Text(
                                stringResource(
                                    when (changed) {
                                        null -> Res.string.cleaner_fix_before_save
                                        true -> Res.string.cleaner_draft_changed
                                        false -> Res.string.cleaner_draft_unchanged
                                    }
                                ), style = MaterialTheme.typography.bodySmall,
                                color = if (changed == null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        AnimatedVisibility(
                            state.saving,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) { CircularProgressIndicator(Modifier.size(24.dp)) }
                        TextButton(onClick = onCancel, enabled = !state.saving) {
                            Text(
                                stringResource(Res.string.cancel)
                            )
                        }
                        TextButton(onClick = { onSave(true) }, enabled = state.canSave) {
                            Text(
                                stringResource(Res.string.cleaner_save_run)
                            )
                        }
                        Button(onClick = { onSave(false) }, enabled = state.canSave) {
                            Text(
                                stringResource(Res.string.cleaner_save)
                            )
                        }
                    }
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.TopCenter) {
            AnimatedVisibility(state.loading, enter = fadeIn(), exit = fadeOut()) {
                Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
            }
            AnimatedVisibility(
                !state.loading,
                enter = fadeIn(tween(180)),
                exit = fadeOut(tween(120))
            ) {
                LazyColumn(
                    Modifier.widthIn(max = 960.dp).fillMaxSize().testTag("cleaner-rule-list"),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = 4.dp,
                        bottom = 16.dp
                    )
                ) {
                    item(key = "status") {
                        AnimatedVisibility(
                            state.unsupported || state.saveFailed,
                            modifier = Modifier.animateItem(
                                placementSpec = tween(280),
                                fadeInSpec = null,
                                fadeOutSpec = null
                            ),
                            enter = ruleContentEnter, exit = ruleContentExit
                        ) {
                            Column(
                                Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (state.unsupported) Text(
                                    stringResource(Res.string.cleaner_unsupported),
                                    color = MaterialTheme.colorScheme.error
                                )
                                if (state.saveFailed) Text(
                                    stringResource(Res.string.cleaner_save_failed),
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                    itemsIndexed(state.draft.rules, key = { _, rule -> rule.id }) { index, rule ->
                        Column(
                            Modifier.animateItem(
                                fadeInSpec = tween(180),
                                placementSpec = tween(280),
                                fadeOutSpec = tween(180)
                            )
                        ) {
                            RuleCard(
                                rule,
                                state.conditionRows[rule.id].orEmpty(),
                                rule.id in state.addedRuleIds,
                                index,
                                state.draft.rules.size,
                                editable,
                                expandedRuleId == rule.id,
                                viewModel,
                                onToggle = {
                                    expandedRuleId = rule.id.takeUnless { expandedRuleId == it }
                                },
                                onDirectory = {
                                    if (rule.conditions.any { it is CleanerCondition.FileSizeGreaterThan }) switchTarget =
                                        rule.id
                                    else viewModel.updateRule(rule.copy(target = CleanerTarget.DIRECTORY))
                                })
                            HorizontalDivider(
                                Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                        }
                    }
                    item(key = "empty") {
                        AnimatedVisibility(
                            state.draft.rules.isEmpty(),
                            modifier = Modifier.animateItem(
                                placementSpec = tween(280),
                                fadeInSpec = null,
                                fadeOutSpec = null
                            ),
                            enter = ruleContentEnter, exit = ruleContentExit
                        ) {
                            Text(
                                stringResource(Res.string.cleaner_empty_rules),
                                Modifier.padding(16.dp),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                    item(key = "scan-settings") {
                        // This footer is displaced by rule insertion/removal too, so it participates
                        // in the same placement animation as the rules above it.
                        Box(
                            Modifier.animateItem(
                                placementSpec = tween(280),
                                fadeInSpec = null,
                                fadeOutSpec = null
                            )
                        ) {
                            ScanSettings(
                                state,
                                editable,
                                scanSettingsExpanded,
                                { scanSettingsExpanded = !scanSettingsExpanded },
                                viewModel
                            )
                        }
                    }
                    item(key = "validation") {
                        AnimatedVisibility(
                            globalIssues.isNotEmpty() || globalWarnings.isNotEmpty(),
                            modifier = Modifier.animateItem(
                                placementSpec = tween(280),
                                fadeInSpec = null,
                                fadeOutSpec = null
                            ),
                            enter = ruleContentEnter, exit = ruleContentExit
                        ) {
                            Column(
                                Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                globalIssues.forEach {
                                    Text(
                                        issueLabel(it),
                                        color = MaterialTheme.colorScheme.error,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                                globalWarnings.forEach {
                                    Text(
                                        issueLabel(it),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    if (restore) AlertDialog(
        onDismissRequest = { restore = false },
        title = { Text(stringResource(Res.string.cleaner_restore)) },
        text = { Text(stringResource(Res.string.cleaner_restore_message)) },
        confirmButton = {
            TextButton(onClick = {
                viewModel.restoreDefault(); restore = false
            }) { Text(stringResource(Res.string.cleaner_restore)) }
        },
        dismissButton = {
            TextButton(onClick = {
                restore = false
            }) { Text(stringResource(Res.string.cancel)) }
        })
    if (switchTarget != null) AlertDialog(
        onDismissRequest = { switchTarget = null },
        title = { Text(stringResource(Res.string.cleaner_switch_target)) },
        text = { Text(stringResource(Res.string.cleaner_switch_message)) },
        confirmButton = {
            TextButton(onClick = {
                state.draft.rules.firstOrNull { it.id == switchTarget }?.let {
                    viewModel.updateRule(
                        it.copy(
                            target = CleanerTarget.DIRECTORY,
                            conditions = it.conditions.filterNot { c -> c is CleanerCondition.FileSizeGreaterThan })
                    )
                }
                switchTarget = null
            }) { Text(stringResource(Res.string.cleaner_continue)) }
        },
        dismissButton = {
            TextButton(onClick = {
                switchTarget = null
            }) { Text(stringResource(Res.string.cancel)) }
        })
}

@Composable
private fun RuleCard(
    rule: CleanerRuleGroup,
    rows: List<CleanerConditionRow>,
    newlyAdded: Boolean,
    index: Int,
    count: Int,
    editable: Boolean,
    expanded: Boolean,
    vm: CleanerRulesViewModel,
    onToggle: () -> Unit,
    onDirectory: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    var addMenu by remember { mutableStateOf(false) }
    var advanced by rememberSaveable(rule.id) { mutableStateOf(false) }
    var renaming by rememberSaveable(rule.id) { mutableStateOf(false) }
    var editingConditionId by rememberSaveable(rule.id) {
        mutableStateOf(rows.firstOrNull {
            newlyAdded && conditionIssues(
                rule.target,
                it.condition
            ).isNotEmpty()
        }?.id)
    }
    val copyName = stringResource(Res.string.cleaner_copy_name, ruleName(rule))
    val issues = validateRule(rule)
    val warnings = ruleWarnings(rule)
    val expandLabel = stringResource(Res.string.cleaner_expand)
    val collapseLabel = stringResource(Res.string.cleaner_collapse)
    val enableLabel = "${stringResource(Res.string.cleaner_enable)}: ${ruleName(rule)}"
    val containerColor by animateColorAsState(
        if (expanded) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.surface,
        animationSpec = tween(240), label = "rule-background"
    )
    Surface(
        color = containerColor, shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().testTag("rule-${rule.id}")
    ) {
        Column(Modifier.padding(8.dp)) {
            // Only the stable header is interactive; editing content never toggles the rule.
            Row(
                Modifier.fillMaxWidth().testTag("rule-header-${rule.id}")
                    .clip(MaterialTheme.shapes.small).then(
                    if (!renaming) Modifier.clickable(
                        onClickLabel = if (expanded) collapseLabel else expandLabel,
                        onClick = onToggle
                    ) else Modifier
                ), verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    if (rule.target == CleanerTarget.FILE) Icons.Outlined.Description else Icons.Outlined.Folder,
                    null,
                    Modifier.padding(horizontal = 8.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Column(Modifier.weight(1f).padding(vertical = 8.dp, horizontal = 4.dp)) {
                    RuleContentSwap(
                        renaming && expanded,
                        Modifier.fillMaxWidth(),
                        "rule-name"
                    ) { editing ->
                        if (editing) Row(verticalAlignment = Alignment.CenterVertically) {
                            CleanerRuleTextField(
                                ruleName(rule),
                                { vm.updateRule(rule.copy(name = it)) },
                                Modifier.weight(1f),
                                label = { Text(stringResource(Res.string.cleaner_rule_name)) },
                                enabled = editable && renaming
                            )
                            IconButton(onClick = { renaming = false }, enabled = editable) {
                                Icon(
                                    Icons.Outlined.Check,
                                    stringResource(Res.string.cleaner_done_editing)
                                )
                            }
                        } else Text(ruleName(rule), style = MaterialTheme.typography.titleMedium)
                    }
                    Spacer(Modifier.height(4.dp))
                    Text(
                        ruleSummary(rule),
                        Modifier.fillMaxWidth().testTag("rule-summary-${rule.id}"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    AnimatedVisibility(
                        issues.isNotEmpty(),
                        enter = ruleContentEnter,
                        exit = ruleContentExit
                    ) {
                        Text(
                            stringResource(Res.string.cleaner_rule_incomplete),
                            Modifier.padding(top = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = if (rule.enabled) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Switch(
                        rule.enabled,
                        { vm.updateRule(rule.copy(enabled = it)) },
                        enabled = editable && (rule.enabled || issues.isEmpty()),
                        modifier = Modifier.testTag("rule-enabled-${rule.id}")
                            .semantics { contentDescription = enableLabel })
                    Text(
                        stringResource(if (rule.enabled) Res.string.cleaner_rule_enabled else Res.string.cleaner_rule_disabled),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onToggle) {
                    CleanerExpandIcon(
                        expanded,
                        if (expanded) collapseLabel else expandLabel
                    )
                }
                Box {
                    IconButton(
                        onClick = { menu = true },
                        enabled = editable
                    ) { Icon(Icons.Outlined.MoreVert, stringResource(Res.string.cleaner_more)) }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cleaner_copy)) },
                            leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) },
                            onClick = { vm.copyRule(rule, copyName); menu = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cleaner_move_up)) },
                            leadingIcon = { Icon(Icons.Outlined.ArrowUpward, null) },
                            enabled = index > 0,
                            onClick = { vm.moveRule(rule.id, -1); menu = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cleaner_move_down)) },
                            leadingIcon = { Icon(Icons.Outlined.ArrowDownward, null) },
                            enabled = index < count - 1,
                            onClick = { vm.moveRule(rule.id, 1); menu = false })
                        DropdownMenuItem(
                            text = { Text(stringResource(Res.string.cleaner_delete_rule)) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null) },
                            onClick = { vm.deleteRule(rule.id); menu = false })
                    }
                }
            }
            // All optional padding stays inside the animated body, so no gap vanishes at its last frame.
            AnimatedVisibility(expanded, enter = ruleContentEnter, exit = ruleContentExit) {
                Column(Modifier.padding(start = 36.dp, end = 8.dp, top = 8.dp, bottom = 12.dp)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        RuleConnectedButtons(
                            rule.target,
                            listOf(CleanerTarget.DIRECTORY, CleanerTarget.FILE),
                            { targetLabel(it) },
                            editable,
                            Modifier.testTag("rule-target-${rule.id}")
                        ) {
                            if (it == CleanerTarget.DIRECTORY && rule.target != it) onDirectory() else vm.updateRule(
                                rule.copy(target = it)
                            )
                        }
                        Spacer(Modifier.weight(1f))
                        TextButton(
                            onClick = { renaming = !renaming },
                            enabled = editable,
                            modifier = Modifier.testTag("rule-rename-${rule.id}")
                        ) {
                            Icon(
                                Icons.Outlined.Edit,
                                null,
                                Modifier.size(18.dp)
                            ); Spacer(Modifier.width(8.dp))
                            Text(stringResource(Res.string.cleaner_rename))
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    FlowRow(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        itemVerticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            stringResource(Res.string.cleaner_conditions),
                            style = MaterialTheme.typography.titleSmall
                        )
                        RuleContentSwap(rows.size > 1, label = "condition-relation") { multiple ->
                            if (multiple) RuleConnectedButtons(
                                rule.relation,
                                ConditionRelation.entries,
                                { stringResource(if (it == ConditionRelation.ALL) Res.string.cleaner_all else Res.string.cleaner_any) },
                                editable,
                                Modifier.testTag("rule-relation-${rule.id}")
                            ) { vm.updateRule(rule.copy(relation = it)) }
                            else Text(
                                stringResource(Res.string.cleaner_condition_single),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    AnimatedConditions(
                        rule.target,
                        rows,
                        editable && expanded,
                        editingConditionId,
                        onEdit = { editingConditionId = it },
                        onDone = { editingConditionId = null },
                        onChange = { id, condition -> vm.updateCondition(rule.id, id, condition) },
                        onRemove = { id ->
                            if (editingConditionId == id) editingConditionId =
                                null; vm.removeCondition(rule.id, id)
                        })
                    AnimatedVisibility(
                        rows.isEmpty(),
                        enter = ruleContentEnter,
                        exit = ruleContentExit
                    ) {
                        Text(
                            stringResource(Res.string.cleaner_empty_conditions),
                            Modifier.padding(vertical = 8.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    Box {
                        TextButton(onClick = { addMenu = true }, enabled = editable) {
                            Icon(
                                Icons.Outlined.Add,
                                null,
                                Modifier.size(18.dp)
                            ); Spacer(Modifier.width(8.dp)); Text(stringResource(Res.string.cleaner_add_condition))
                        }
                        DropdownMenu(addMenu, { addMenu = false }) {
                            conditionFields(rule.target).forEach { field ->
                                DropdownMenuItem(
                                    text = { Text(fieldLabel(field, rule.target)) },
                                    onClick = {
                                        vm.addCondition(rule.id, newCondition(field))
                                        editingConditionId =
                                            vm.uiState.value.conditionRows[rule.id]?.lastOrNull()?.id
                                        addMenu = false
                                    })
                            }
                        }
                    }
                    Column(Modifier.fillMaxWidth().testTag("rule-options-surface-${rule.id}")) {
                        TextButton(
                            onClick = { advanced = !advanced },
                            modifier = Modifier.fillMaxWidth().testTag("rule-options-${rule.id}"),
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(stringResource(Res.string.cleaner_other_options)); Spacer(
                            Modifier.weight(
                                1f
                            )
                        ); CleanerExpandIcon(advanced, null)
                        }
                        AnimatedVisibility(
                            advanced,
                            enter = ruleContentEnter,
                            exit = ruleContentExit
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(bottom = 8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    rule.defaultSelected,
                                    { vm.updateRule(rule.copy(defaultSelected = it)) },
                                    enabled = editable
                                )
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        stringResource(Res.string.cleaner_default_selected),
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        stringResource(Res.string.cleaner_default_selected_help),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                    AnimatedVisibility(
                        warnings.isNotEmpty(),
                        enter = ruleContentEnter,
                        exit = ruleContentExit
                    ) {
                        Column(
                            Modifier.padding(top = 8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            warnings.forEach {
                                Text(
                                    issueLabel(it),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun <T> RuleConnectedButtons(
    value: T, options: List<T>, text: @Composable (T) -> String, editable: Boolean,
    modifier: Modifier = Modifier, onChange: (T) -> Unit
) {
    val labels = options.map { text(it) }
    val labelStyle = MaterialTheme.typography.labelLarge
    val textMeasurer = rememberTextMeasurer()
    val contentPadding = ButtonDefaults.contentPaddingFor(ButtonDefaults.MinHeight)
    val direction = LocalLayoutDirection.current
    val labelWidth = with(LocalDensity.current) {
        labels.maxOf { textMeasurer.measure(it, style = labelStyle, maxLines = 1).size.width }
            .toDp()
    }
    // Reserve the longest label plus its checkmark in every state. AnimatedVisibility's
    // changing intrinsic size must not resize the group when its outgoing icon is disposed.
    val buttonWidth = labelWidth + FilterChipDefaults.IconSize + ToggleButtonDefaults.IconSpacing +
            contentPadding.calculateLeftPadding(direction) + contentPadding.calculateRightPadding(
        direction
    )
    Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)
    ) {
        options.forEachIndexed { index, option ->
            val checked = value == option
            ToggleButton(
                checked = checked,
                onCheckedChange = { onChange(option) },
                modifier = Modifier.width(buttonWidth),
                enabled = editable,
                contentPadding = contentPadding,
                colors = ToggleButtonDefaults.elevatedToggleButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                ),
                shapes = when (index) {
                    0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                    options.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                    else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                },
            ) {
                AnimatedVisibility(checked) {
                    Row {
                        Icon(Icons.Outlined.Check, null, Modifier.size(FilterChipDefaults.IconSize))
                        Spacer(Modifier.size(ToggleButtonDefaults.IconSpacing))
                    }
                }
                Text(labels[index], style = labelStyle, maxLines = 1)
            }
        }
    }
}

@Composable
private fun ScanSettings(
    state: CleanerRulesUiState,
    editable: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    vm: CleanerRulesViewModel
) {
    val invalid = state.depthText.toIntOrNull() !in 1..50
    val depthLabel = stringResource(Res.string.cleaner_max_depth)
    Surface(
        modifier = Modifier.fillMaxWidth().padding(top = 16.dp).testTag("cleaner-scan-settings"),
        shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column {
            Row(
                Modifier.fillMaxWidth().testTag("cleaner-scan-settings-header")
                    .clip(MaterialTheme.shapes.medium)
                    .clickable(onClick = onToggle)
                    .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Outlined.Tune,
                    null,
                    Modifier.size(24.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        stringResource(Res.string.cleaner_scan_settings),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        stringResource(
                            if (invalid) Res.string.cleaner_invalid_depth else Res.string.cleaner_scan_options_summary,
                            state.depthText.toIntOrNull() ?: 0
                        ), style = MaterialTheme.typography.bodySmall,
                        color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = onToggle,
                    modifier = Modifier.testTag("cleaner-scan-settings-toggle")
                ) {
                    CleanerExpandIcon(expanded, stringResource(Res.string.cleaner_scan_settings))
                }
            }
            AnimatedVisibility(expanded, enter = ruleContentEnter, exit = ruleContentExit) {
                Column {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    Column(Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(depthLabel, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    stringResource(Res.string.cleaner_depth_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (invalid) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            CleanerRuleTextField(
                                state.depthText,
                                vm::changeDepth,
                                Modifier.width(120.dp).testTag("cleaner-max-depth")
                                    .semantics { contentDescription = depthLabel },
                                enabled = editable,
                                label = { Text(stringResource(Res.string.cleaner_depth_level)) },
                                isError = invalid
                            )
                        }
                        HorizontalDivider(
                            Modifier.padding(vertical = 16.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                        Row(
                            Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .toggleable(
                                    value = state.draft.includeHidden,
                                    enabled = editable,
                                    role = Role.Switch,
                                    onValueChange = { include -> vm.edit { it.copy(includeHidden = include) } })
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(24.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(
                                Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    stringResource(Res.string.cleaner_hidden),
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    stringResource(Res.string.cleaner_hidden_help),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                state.draft.includeHidden,
                                onCheckedChange = null,
                                enabled = editable
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                        Surface(
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Row(
                                Modifier.fillMaxWidth().padding(12.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Outlined.Info,
                                    null,
                                    Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        stringResource(Res.string.cleaner_scan_fixed_behavior),
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                    Text(
                                        stringResource(Res.string.cleaner_no_symlinks),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
