package org.tool.kit.feature.ui

import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun FileButton(value: String, expanded: Boolean, onClick: () -> Unit) {
    ExtendedFloatingActionButton(
        modifier = Modifier.padding(end = 16.dp, bottom = 16.dp),
        onClick = onClick,
        icon = { Icon(Icons.Rounded.DriveFolderUpload, value) },
        text = { Text(value) },
        expanded = expanded
    )
}
