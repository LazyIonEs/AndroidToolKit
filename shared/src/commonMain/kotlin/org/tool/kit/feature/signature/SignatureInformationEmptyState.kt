package org.tool.kit.feature.signature

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.DriveFolderUpload
import androidx.compose.material.icons.outlined.Fingerprint
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.signature_information_tooltip
import org.tool.kit.shared.generated.resources.signature_empty_description
import org.tool.kit.shared.generated.resources.signature_empty_start
import org.tool.kit.shared.generated.resources.signature_empty_instruction
import org.tool.kit.shared.generated.resources.signature_empty_pick
import org.tool.kit.shared.generated.resources.signature_empty_formats
import org.tool.kit.shared.generated.resources.signature_empty_certificate
import org.tool.kit.shared.generated.resources.signature_empty_certificate_detail
import org.tool.kit.shared.generated.resources.signature_empty_fingerprint
import org.tool.kit.shared.generated.resources.signature_empty_fingerprint_detail
import org.tool.kit.shared.generated.resources.signature_empty_verification
import org.tool.kit.shared.generated.resources.signature_empty_verification_detail
import org.tool.kit.shared.generated.resources.signature_empty_keystore_hint
import org.tool.kit.shared.generated.resources.signature_empty_types
import org.tool.kit.shared.generated.resources.signature_empty_copy_hint
import org.tool.kit.shared.generated.resources.signature_empty_apk_label

/** E layout fits the content area of an 800 × 600 window, including its rail and title bar. */
@Composable
internal fun SignatureInformationEmptyState(onPickFile: () -> Unit) {
    Surface(Modifier.fillMaxSize().testTag("signature-empty"), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.signature_information_tooltip), Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineSmall)
                Text(stringResource(Res.string.signature_empty_types), style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.signature_empty_description), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth().height(300.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                SignatureImportCard(onPickFile, Modifier.width(344.dp).fillMaxHeight())
                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    SignatureFingerprintCard(Modifier.fillMaxWidth().height(156.dp))
                    SignatureCertificateCard(Modifier.fillMaxWidth().weight(1f))
                }
            }
            Spacer(Modifier.height(16.dp))
            SignatureVerificationCard()
            Spacer(Modifier.height(12.dp))
            Spacer(Modifier.weight(1f))
            Row(Modifier.fillMaxWidth().testTag("signature-keystore-hint"), verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Outlined.Info, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(stringResource(Res.string.signature_empty_keystore_hint), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun SignatureImportCard(onPickFile: () -> Unit, modifier: Modifier) {
    Card(modifier.testTag("signature-import-card"), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Icon(Icons.Outlined.DriveFolderUpload, null, Modifier.size(32.dp))
            Spacer(Modifier.height(12.dp))
            Text(stringResource(Res.string.signature_empty_start), style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.signature_empty_instruction), style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Button(onClick = onPickFile, modifier = Modifier.testTag("signature-pick-file")) {
                Icon(Icons.Outlined.FolderOpen, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(Res.string.signature_empty_pick))
            }
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.signature_empty_formats), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun SignatureFingerprintCard(modifier: Modifier) {
    Card(modifier.testTag("signature-fingerprint-card"), shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(Res.string.signature_empty_fingerprint), Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge)
                Icon(Icons.Outlined.Fingerprint, null, Modifier.size(40.dp))
            }
            Spacer(Modifier.weight(1f))
            Text(stringResource(Res.string.signature_empty_fingerprint_detail), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(Res.string.signature_empty_copy_hint), style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun SignatureCertificateCard(modifier: Modifier) {
    OutlinedCard(modifier.testTag("signature-certificate-card"), shape = MaterialTheme.shapes.extraLarge) {
        Column(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 20.dp), verticalArrangement = Arrangement.Center) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Icon(Icons.Outlined.Badge, null, Modifier.size(24.dp))
                Text(stringResource(Res.string.signature_empty_certificate), style = MaterialTheme.typography.titleLarge)
            }
            Spacer(Modifier.height(16.dp))
            Text(stringResource(Res.string.signature_empty_certificate_detail), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SignatureVerificationCard() {
    Card(Modifier.fillMaxWidth().height(80.dp).testTag("signature-verification-card"), shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)) {
        Row(Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Icon(Icons.Outlined.VerifiedUser, null, Modifier.size(32.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(stringResource(Res.string.signature_empty_verification), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(Res.string.signature_empty_verification_detail), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(stringResource(Res.string.signature_empty_apk_label), style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
