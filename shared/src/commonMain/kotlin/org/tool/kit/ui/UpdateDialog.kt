package org.tool.kit.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownTypography
import com.mikepenz.markdown.model.markdownPadding
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.BuildConfig
import org.tool.kit.model.Asset
import org.tool.kit.model.DownloadState
import org.tool.kit.model.Update
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.cancel
import org.tool.kit.shared.generated.resources.connecting
import org.tool.kit.shared.generated.resources.download_success
import org.tool.kit.shared.generated.resources.download_tips
import org.tool.kit.shared.generated.resources.downloading
import org.tool.kit.shared.generated.resources.exit_and_install
import org.tool.kit.shared.generated.resources.prepare_for_installation
import org.tool.kit.shared.generated.resources.release_time
import org.tool.kit.shared.generated.resources.update
import org.tool.kit.utils.downloadFile
import org.tool.kit.utils.formatFileSize
import org.tool.kit.vm.MainViewModel
import java.awt.Desktop
import java.io.File
import kotlin.math.roundToInt
import kotlin.system.exitProcess

/**
 * @author      : Eddy
 * @description : 描述
 * @createDate  : 2026/1/20 09:19
 */

private val logger = KotlinLogging.logger("UpdateDialog")

/**
 * 检查更新弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateDialog(vm: MainViewModel) {
    val update by vm.checkUpdateResult.collectAsState()
    var downloadState by remember { mutableStateOf(DownloadState.START) }
    var downloadProgress by remember { mutableStateOf(0f) }
    var downloadedByte by remember { mutableStateOf(0L) }
    var totalByte by remember { mutableStateOf(0L) }
    val coroutineScope = rememberCoroutineScope()
    var job: Job? = null
    var downloadFile: File? = null
    update?.let { update ->
        if (update.assets.isNotEmpty()) {
            val (selectedOption, onOptionSelected) = remember { mutableStateOf(update.assets[0]) }
            val download: () -> Unit = {
                job = coroutineScope.launch {
                    val destFile = File(vm.userData.value.defaultOutputPath, selectedOption.name)
                    downloadState = DownloadState.DOWNLOADING
                    val result = downloadFile(
                        selectedOption.browserDownloadUrl,
                        destFile
                    ) { downloaded, total ->
                        downloadedByte = downloaded
                        totalByte = total
                        downloadProgress = if (total > 0) {
                            (downloaded.toFloat() / total).coerceIn(0f, 1f)
                        } else {
                            0f
                        }
                    }
                    if (result.isSuccess) {
                        downloadFile = destFile
                        downloadState = DownloadState.FINISH
                    } else {
                        downloadState = DownloadState.START
                        vm.updateSnackbarVisuals(result.msg ?: return@launch)
                    }
                }
            }
            AlertDialog(
                icon = null, title = {
                    val title = when (downloadState) {
                        DownloadState.START -> "${BuildConfig.APP_NAME}-${update.version}"
                        DownloadState.DOWNLOADING -> if (downloadProgress <= 0f) {
                            stringResource(Res.string.connecting)
                        } else {
                            stringResource(
                                Res.string.downloading,
                                (downloadProgress * 100).roundToInt()
                            )
                        }

                        DownloadState.FINISH -> stringResource(Res.string.download_success)
                    }
                    Text(text = title)
                }, text = {
                    AnimatedContent(downloadState) { state ->
                        when (state) {
                            DownloadState.START -> DownloadStartUI(
                                update,
                                selectedOption,
                                onOptionSelected
                            )

                            DownloadState.DOWNLOADING, DownloadState.FINISH -> DownloadUI(
                                downloadProgress,
                                downloadedByte,
                                totalByte,
                                state
                            )
                        }
                    }
                }, onDismissRequest = {
                    if (downloadState != DownloadState.DOWNLOADING) {
                        vm.cancelUpdate()
                    }
                }, confirmButton = {
                    AnimatedContent(downloadState) { state ->
                        when (state) {
                            DownloadState.START -> TextButton(onClick = download) {
                                Text(text = stringResource(Res.string.update))
                            }

                            DownloadState.DOWNLOADING -> Unit
                            DownloadState.FINISH -> TextButton(onClick = {
                                vm.cancelUpdate()
                                if (downloadFile != null && downloadFile.exists()) {
                                    runCatching {
                                        Desktop.getDesktop().open(downloadFile)
                                    }.onFailure { e ->
                                        logger.error(e) { "org.tool.kit.UpdateDialog 打开安装文件异常, 异常信息: ${e.message}" }
                                    }.onSuccess {
                                        exitProcess(0)
                                    }
                                }
                            }) {
                                Text(text = stringResource(Res.string.exit_and_install))
                            }
                        }
                    }
                }, dismissButton = {
                    TextButton(onClick = {
                        if (downloadState == DownloadState.DOWNLOADING) {
                            job?.cancel()
                            job = null
                            downloadProgress = 0f
                            downloadedByte = 0L
                            totalByte = 0L
                            downloadState = DownloadState.START
                        } else {
                            vm.cancelUpdate()
                        }
                    }) {
                        Text(text = stringResource(Res.string.cancel))
                    }
                })
        }
    }
}

@Composable
private fun DownloadStartUI(
    update: Update,
    selectedOption: Asset,
    onOptionSelected: (Asset) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val scrollState = rememberScrollState()
        HorizontalDivider(thickness = 2.dp)
        Column(
            modifier = Modifier.fillMaxWidth()
                .heightIn(0.dp, 160.dp)
                .padding(vertical = 8.dp)
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            update.body?.let { body ->
                Markdown(
                    content = body.trimIndent(),
                    typography = markdownTypography(
                        h1 = MaterialTheme.typography.titleLarge,
                        h2 = MaterialTheme.typography.titleMedium,
                        h3 = MaterialTheme.typography.titleSmall,
                        h4 = MaterialTheme.typography.bodyLarge,
                        h5 = MaterialTheme.typography.bodyMedium,
                        h6 = MaterialTheme.typography.bodySmall,
                        text = MaterialTheme.typography.labelMedium,
                        paragraph = MaterialTheme.typography.labelMedium,
                        ordered = MaterialTheme.typography.labelMedium,
                        bullet = MaterialTheme.typography.labelMedium,
                        list = MaterialTheme.typography.labelMedium,
                        textLink = TextLinkStyles(
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = TextDecoration.Underline
                            ).toSpanStyle()
                        ),
                    ),
                    padding = markdownPadding(listItemTop = 2.dp, listItemBottom = 2.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                )
            }
            Text(
                text = stringResource(Res.string.release_time, update.createdAt),
                modifier = Modifier.padding(vertical = 4.dp)
                    .align(Alignment.End),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(bottom = 8.dp),
            thickness = 2.dp
        )
        Column(Modifier.selectableGroup()) {
            update.assets.forEach { asset ->
                val interactionSource =
                    remember { MutableInteractionSource() }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(40.dp)
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Start
                ) {
                    RadioButton(
                        selected = (asset == selectedOption),
                        onClick = { onOptionSelected(asset) },
                        interactionSource = interactionSource,
                    )
                    Text(
                        text = asset.name,
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.selectable(
                            selected = (asset == selectedOption),
                            onClick = { onOptionSelected(asset) },
                            role = Role.RadioButton,
                            interactionSource = interactionSource,
                            indication = null
                        )
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun DownloadUI(
    downloadProgress: Float,
    downloadedByte: Long,
    totalByte: Long,
    state: DownloadState
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.Start
    ) {
        val tips = when (state) {
            DownloadState.START -> stringResource(Res.string.download_tips)
            DownloadState.DOWNLOADING -> stringResource(Res.string.download_tips)
            DownloadState.FINISH -> stringResource(Res.string.prepare_for_installation)
        }
        Text(
            text = tips,
            modifier = Modifier.padding(vertical = 4.dp),
            style = MaterialTheme.typography.titleMedium,
        )
        AnimatedContent(
            downloadProgress == 0f,
            modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)
        ) { unspecified ->
            if (unspecified) {
                LinearWavyProgressIndicator()
            } else {
                LinearWavyProgressIndicator(progress = { downloadProgress })
            }
        }
        AnimatedVisibility(
            totalByte != 0L && state == DownloadState.DOWNLOADING,
            modifier = Modifier.align(Alignment.End)
        ) {
            Text(
                text = "${
                    downloadedByte.formatFileSize(1)
                } / ${
                    totalByte.formatFileSize(1)
                }",
                modifier = Modifier.padding(top = 4.dp),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}