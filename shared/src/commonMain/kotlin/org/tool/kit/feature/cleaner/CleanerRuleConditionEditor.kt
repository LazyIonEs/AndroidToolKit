package org.tool.kit.feature.cleaner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.domain.cleaner.*
import org.tool.kit.shared.generated.resources.*

/** Keep a removed row's last editor intact until its own exit completes. */
@Composable
internal fun AnimatedConditions(
    target: CleanerTarget,
    rows: List<CleanerConditionRow>,
    editable: Boolean,
    editingId: String?,
    onEdit: (String) -> Unit,
    onDone: () -> Unit,
    onChange: (String, CleanerCondition) -> Unit,
    onRemove: (String) -> Unit,
) {
    val displayed = remember { mutableStateListOf<CleanerConditionRow>().apply { addAll(rows) } }
    val initiallyVisible = remember { rows.map { it.id }.toSet() }
    LaunchedEffect(rows.map { it.id }) {
        rows.filter { row -> displayed.none { it.id == row.id } }.forEach { displayed.add(it) }
    }
    Column {
        displayed.forEach { previous ->
            key(previous.id) {
                val current = rows.firstOrNull { it.id == previous.id }
                val visibility = remember { MutableTransitionState(previous.id in initiallyVisible) }
                var lastCondition by remember { mutableStateOf(previous.condition) }
                var lastEditing by remember { mutableStateOf(editingId == previous.id) }
                var lastTarget by remember { mutableStateOf(target) }
                LaunchedEffect(current, editingId, target) {
                    if (current != null) {
                        lastCondition = current.condition
                        lastEditing = editingId == previous.id
                        lastTarget = target
                    }
                    visibility.targetState = current != null
                }
                LaunchedEffect(visibility.isIdle, visibility.currentState, visibility.targetState) {
                    if (visibility.isIdle && !visibility.currentState && !visibility.targetState && current == null) {
                        displayed.removeAll { it.id == previous.id }
                    }
                }
                val editing = if (current != null) editingId == previous.id else lastEditing
                val condition = current?.condition ?: lastCondition
                val displayedTarget = if (current != null) target else lastTarget
                val canEdit = editable && current != null
                AnimatedVisibility(visibility, enter = ruleContentEnter, exit = ruleContentExit) {
                    Column(Modifier.testTag("condition-${previous.id}")) {
                        RuleContentSwap(editing, Modifier.fillMaxWidth(), "condition-editor-${previous.id}") { showEditor ->
                            if (showEditor) ConditionEditor(displayedTarget, condition, canEdit && editing,
                                onChange = { onChange(previous.id, it) }, onDone = onDone,
                                onRemove = { onRemove(previous.id) })
                            else Row(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                                .clickable(enabled = canEdit && !editing) { onEdit(previous.id) }, verticalAlignment = Alignment.Top) {
                                ConditionReadout(displayedTarget, condition, Modifier.weight(1f))
                                IconButton(onClick = { onEdit(previous.id) }, enabled = canEdit && !editing,
                                    modifier = Modifier.testTag("condition-edit-${previous.id}")) {
                                    Icon(Icons.Outlined.Edit, stringResource(Res.string.cleaner_edit_condition), Modifier.size(18.dp))
                                }
                                IconButton(onClick = { onRemove(previous.id) }, enabled = canEdit && !editing) {
                                    Icon(Icons.Outlined.Close, stringResource(Res.string.cleaner_remove_condition), Modifier.size(18.dp))
                                }
                            }
                        }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

/** Shared column proportions keep conditions aligned, while long values wrap in place. */
@Composable
private fun ConditionReadout(target: CleanerTarget, condition: CleanerCondition, modifier: Modifier = Modifier) {
    val (field, operator, value) = conditionParts(target, condition)
    val style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium)
    val color = if (conditionIssues(target, condition).isNotEmpty()) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Row(modifier.padding(horizontal = 8.dp, vertical = 12.dp), horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.Top) {
        Text(field, Modifier.weight(1f).testTag("condition-field"), style = style, color = color)
        Text(operator, Modifier.weight(1f).testTag("condition-operator"), style = style, color = color)
        Text(value, Modifier.weight(2f).testTag("condition-value"), style = style, color = color)
    }
}

internal enum class ConditionField { NAME, PATH, SIZE }
internal fun conditionFields(target: CleanerTarget) = if (target == CleanerTarget.FILE) ConditionField.entries else listOf(ConditionField.NAME, ConditionField.PATH)
internal fun newCondition(field: ConditionField): CleanerCondition = when (field) {
    ConditionField.NAME -> CleanerCondition.Text()
    ConditionField.PATH -> CleanerCondition.Text(field = CleanerTextField.RELATIVE_PATH)
    ConditionField.SIZE -> CleanerCondition.FileSizeGreaterThan()
}
@Composable internal fun fieldLabel(field: ConditionField, target: CleanerTarget) = stringResource(when (field) {
    ConditionField.NAME -> if (target == CleanerTarget.FILE) Res.string.cleaner_file_name else Res.string.cleaner_directory_name
    ConditionField.PATH -> Res.string.cleaner_relative_path
    ConditionField.SIZE -> Res.string.cleaner_file_size
})
internal fun conditionIssues(target: CleanerTarget, condition: CleanerCondition) =
    validateRule(CleanerRuleGroup("validation", "", target = target, conditions = listOf(condition)))

@Composable
private fun ConditionEditor(target: CleanerTarget, condition: CleanerCondition, editable: Boolean,
    onChange: (CleanerCondition) -> Unit, onDone: () -> Unit, onRemove: () -> Unit) {
    val field = when (condition) {
        is CleanerCondition.Text -> if (condition.field == CleanerTextField.NAME) ConditionField.NAME else ConditionField.PATH
        is CleanerCondition.FileSizeGreaterThan -> ConditionField.SIZE
    }
    val issues = conditionIssues(target, condition)
    Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp),
                itemVerticalAlignment = Alignment.CenterVertically) {
                ConditionChoice(stringResource(Res.string.cleaner_field), field, conditionFields(target), { fieldLabel(it, target) }, editable) { next ->
                    onChange(if (condition is CleanerCondition.Text && next != ConditionField.SIZE) condition.copy(field = if (next == ConditionField.NAME) CleanerTextField.NAME else CleanerTextField.RELATIVE_PATH) else newCondition(next))
                }
                when (condition) {
                    is CleanerCondition.Text -> {
                        ConditionChoice(stringResource(Res.string.cleaner_operator), condition.operator, TextOperator.entries,
                            { operatorLabel(it) }, editable) { onChange(condition.copy(operator = it)) }
                        CleanerRuleTextField(condition.value, { onChange(condition.copy(value = it)) }, Modifier.width(200.dp),
                            enabled = editable, label = { Text(stringResource(Res.string.cleaner_value)) }, isError = issues.isNotEmpty())
                    }
                    is CleanerCondition.FileSizeGreaterThan -> {
                        CleanerRuleTextField(condition.displayValue, { onChange(condition.copy(displayValue = it, bytes = sizeInBytes(it, condition.displayUnit) ?: 0)) },
                            Modifier.width(180.dp), enabled = editable,
                            label = { Text(stringResource(Res.string.cleaner_greater_than)) }, isError = issues.isNotEmpty())
                        ConditionChoice(stringResource(Res.string.cleaner_unit), condition.displayUnit, SizeUnit.entries,
                            { it.name }, editable) { onChange(condition.copy(displayUnit = it, bytes = sizeInBytes(condition.displayValue, it) ?: 0)) }
                    }
                }
            }
            IconButton(onClick = onDone, enabled = editable) { Icon(Icons.Outlined.Check, stringResource(Res.string.cleaner_done_editing)) }
            IconButton(onClick = onRemove, enabled = editable) { Icon(Icons.Outlined.Close, stringResource(Res.string.cleaner_remove_condition), Modifier.size(18.dp)) }
        }
        AnimatedVisibility(issues.isNotEmpty(), enter = ruleContentEnter, exit = ruleContentExit) {
            Column(Modifier.padding(top = 4.dp, start = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                issues.forEach { Text(issueLabel(it), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

/** Choice menus do not look like editable text fields. Only the actual match value needs a field. */
@Composable
private fun <T> ConditionChoice(label: String, value: T, options: List<T>, text: @Composable (T) -> String,
    enabled: Boolean, onChange: (T) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.width(140.dp)) {
        Text(label, Modifier.padding(start = 8.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Box {
            TextButton(onClick = { expanded = true }, enabled = enabled, shape = MaterialTheme.shapes.small,
                contentPadding = PaddingValues(horizontal = 8.dp), modifier = Modifier.fillMaxWidth()) {
                Text(text(value), Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                CleanerExpandIcon(expanded, null, Modifier.size(18.dp))
            }
            DropdownMenu(expanded, { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(text(option)) }, onClick = { onChange(option); expanded = false })
                }
            }
        }
    }
}
