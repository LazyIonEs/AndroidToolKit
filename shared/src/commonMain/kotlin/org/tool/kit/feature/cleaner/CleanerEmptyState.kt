package org.tool.kit.feature.cleaner

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ManageSearch
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.cache_title
import org.tool.kit.shared.generated.resources.cleaner_step_scope_title
import org.tool.kit.shared.generated.resources.cleaner_step_scope_description
import org.tool.kit.shared.generated.resources.cleaner_step_review_title
import org.tool.kit.shared.generated.resources.cleaner_step_review_description
import org.tool.kit.shared.generated.resources.cleaner_step_clear_title
import org.tool.kit.shared.generated.resources.cleaner_step_clear_description

/** Static guidance; selecting a directory remains the page's only start action. */
@Composable
internal fun CleanerWelcome() {
    Column(Modifier.fillMaxWidth().testTag("cleaner-welcome")) {
        Text(
            stringResource(Res.string.cache_title),
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.testTag("cleaner-welcome-title")
        )
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth().height(IntrinsicSize.Min).testTag("cleaner-welcome-steps"),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            CleanerStepCard(
                "01", Icons.Outlined.FolderOpen,
                stringResource(Res.string.cleaner_step_scope_title),
                stringResource(Res.string.cleaner_step_scope_description),
                Modifier.weight(1f).fillMaxHeight()
            )
            CleanerStepCard(
                "02", Icons.AutoMirrored.Outlined.ManageSearch,
                stringResource(Res.string.cleaner_step_review_title),
                stringResource(Res.string.cleaner_step_review_description),
                Modifier.weight(1f).fillMaxHeight()
            )
            CleanerStepCard(
                "03", Icons.Outlined.DeleteSweep,
                stringResource(Res.string.cleaner_step_clear_title),
                stringResource(Res.string.cleaner_step_clear_description),
                Modifier.weight(1f).fillMaxHeight(),
                emphasized = true
            )
        }
    }
}

@Composable
private fun CleanerStepCard(
    number: String,
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier,
    emphasized: Boolean = false,
) {
    val colors = MaterialTheme.colorScheme
    val contentColor = if (emphasized) colors.onPrimaryContainer else colors.onSurface
    val accentColor = if (emphasized) colors.onPrimaryContainer else colors.primary
    Card(
        modifier.heightIn(min = 152.dp).testTag("cleaner-welcome-step-$number"),
        colors = CardDefaults.cardColors(
            containerColor = if (emphasized) colors.primaryContainer else colors.surfaceContainer,
            contentColor = contentColor
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp), tint = accentColor)
                Spacer(Modifier.weight(1f))
                Text(number, style = MaterialTheme.typography.labelMedium, color = accentColor)
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (emphasized) colors.onPrimaryContainer else colors.onSurfaceVariant,
                modifier = Modifier.testTag("cleaner-step-description-$number")
            )
        }
    }
}
