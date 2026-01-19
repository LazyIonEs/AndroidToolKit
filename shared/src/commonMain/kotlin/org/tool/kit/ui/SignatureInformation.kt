package org.tool.kit.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.LibraryBooks
import androidx.compose.material.icons.automirrored.rounded.Subject
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.SentimentDissatisfied
import androidx.compose.material.icons.outlined.SentimentVeryDissatisfied
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.DriveFolderUpload
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QueryBuilder
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.SentimentSatisfied
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.SplitButtonDefaults
import androidx.compose.material3.SplitButtonLayout
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults.rememberTooltipPositionProvider
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.vinceglb.filekit.dialogs.FileKitMode
import io.github.vinceglb.filekit.dialogs.FileKitType
import io.github.vinceglb.filekit.dialogs.compose.rememberFilePickerLauncher
import io.github.vinceglb.filekit.path
import org.jetbrains.compose.resources.stringResource
import org.tool.kit.model.CopyMode
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.FileSelectorType
import org.tool.kit.model.Verifier
import org.tool.kit.model.VerifierResult
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.cancel
import org.tool.kit.shared.generated.resources.confirm
import org.tool.kit.shared.generated.resources.key_alias
import org.tool.kit.shared.generated.resources.key_store_password
import org.tool.kit.shared.generated.resources.let_go
import org.tool.kit.shared.generated.resources.password_verification
import org.tool.kit.shared.generated.resources.upload
import org.tool.kit.shared.generated.resources.upload_apk_signature_file
import org.tool.kit.shared.generated.resources.wrong_key_store_password
import org.tool.kit.utils.LottieAnimation
import org.tool.kit.utils.checkFile
import org.tool.kit.utils.copy
import org.tool.kit.utils.isApk
import org.tool.kit.utils.isKey
import org.tool.kit.utils.toFileExtensions
import org.tool.kit.vm.MainViewModel
import org.tool.kit.vm.UIState
import kotlin.io.path.pathString

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/2/5 19:47
 * @Description : 签名信息
 * @Version     : 1.0
 */
@Composable
fun SignatureInformation(
    viewModel: MainViewModel
) {
    val signaturePath = remember { mutableStateOf("") }
    if (viewModel.verifierState == UIState.WAIT) {
        SignatureLottie(viewModel)
    }
    SignatureList(viewModel)
    SignatureBox(viewModel, signaturePath)
    SignatureDialog(viewModel, signaturePath)
}

/**
 * 主页动画
 */
@Composable
private fun SignatureLottie(viewModel: MainViewModel) {
    val themeConfig by viewModel.themeConfig.collectAsState()
    val useDarkTheme = when (themeConfig) {
        DarkThemeConfig.LIGHT -> false
        DarkThemeConfig.DARK -> true
        DarkThemeConfig.FOLLOW_SYSTEM -> isSystemInDarkTheme()
    }
    Box(
        modifier = Modifier.padding(6.dp), contentAlignment = Alignment.Center
    ) {
        if (useDarkTheme) {
            LottieAnimation("files/lottie_main_1_dark.json")
        } else {
            LottieAnimation("files/lottie_main_1_light.json")
        }
    }
}

/**
 * 签名主页，包含拖拽文件逻辑
 */
@OptIn(
    ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class,
    ExperimentalMaterial3ExpressiveApi::class
)
@Composable
private fun SignatureBox(
    viewModel: MainViewModel, signaturePath: MutableState<String>
) {
    var dragging by remember { mutableStateOf(false) }
    val copyMode by viewModel.copyMode.collectAsState()
    UploadAnimate(dragging)
    Box(
        modifier = Modifier.fillMaxSize()
            .dragAndDropTarget(
                shouldStartDragAndDrop = accept@{ true }, target = dragAndDropTarget(dragging = {
                    dragging = it
                }, onFinish = { result ->
                    result.onSuccess { fileList ->
                        fileList.firstOrNull()?.let {
                            val path = it.toAbsolutePath().pathString
                            if (path.isApk) {
                                viewModel.apkVerifier(path)
                            } else if (path.isKey) {
                                signaturePath.value = path
                            }
                        }
                    }
                })
            ), contentAlignment = Alignment.TopCenter
    ) {
        Row(modifier = Modifier.align(Alignment.BottomEnd)) {
            AnimatedVisibility(
                visible = viewModel.verifierState == UIState.WAIT,
            ) {
                FileButton(
                    value = if (dragging) {
                        stringResource(Res.string.let_go)
                    } else {
                        stringResource(Res.string.upload_apk_signature_file)
                    },
                    expanded = true,
                    FileSelectorType.KEY,
                    FileSelectorType.APK
                ) { path ->
                    if (path.isApk) {
                        viewModel.apkVerifier(path)
                    } else if (path.isKey) {
                        signaturePath.value = path
                    }
                }
            }
            AnimatedVisibility(
                visible = viewModel.verifierState is UIState.Success,
                modifier = Modifier.padding(end = 16.dp, bottom = 8.dp)
            ) {
                Box(modifier = Modifier.wrapContentSize()) {
                    var checked by remember { mutableStateOf(false) }
                    val type = arrayOf(FileSelectorType.KEY, FileSelectorType.APK)
                    val launcher = rememberFilePickerLauncher(
                        type = FileKitType.File(type.toFileExtensions()),
                        mode = FileKitMode.Single
                    ) { file ->
                        if (type.checkFile(file?.path ?: return@rememberFilePickerLauncher)) {
                            if (file.path.isApk) {
                                viewModel.apkVerifier(file.path)
                            } else if (file.path.isKey) {
                                signaturePath.value = file.path
                            }
                        }
                    }
                    val size = SplitButtonDefaults.SmallContainerHeight
                    SplitButtonLayout(
                        leadingButton = {
                            SplitButtonDefaults.LeadingButton(
                                onClick = {
                                    launcher.launch()
                                },
                                modifier = Modifier.heightIn(size),
                                shapes = SplitButtonDefaults.leadingButtonShapesFor(size),
                                contentPadding = SplitButtonDefaults.leadingButtonContentPaddingFor(
                                    size
                                ),
                                elevation = ButtonDefaults.elevatedButtonElevation(),
                            ) {
                                Icon(
                                    Icons.Rounded.DriveFolderUpload,
                                    "DriveFolderUpload",
                                    Modifier.size(SplitButtonDefaults.leadingButtonIconSizeFor(size))
                                )
                                Spacer(Modifier.size(ButtonDefaults.iconSpacingFor(size)))
                                Text(
                                    text = stringResource(Res.string.upload),
                                    style = ButtonDefaults.textStyleFor(size)
                                )
                            }
                        },
                        trailingButton = {
                            val description = "切换复制模式"
                            TooltipBox(
                                positionProvider = rememberTooltipPositionProvider(
                                    TooltipAnchorPosition.Above
                                ),
                                tooltip = { PlainTooltip { Text(description) } },
                                state = rememberTooltipState(),
                            ) {
                                SplitButtonDefaults.TrailingButton(
                                    checked = checked,
                                    onCheckedChange = { checked = it },
                                    modifier = Modifier.heightIn(size).semantics {
                                        stateDescription = if (checked) "Expanded" else "Collapsed"
                                        contentDescription = description
                                    },
                                    elevation = ButtonDefaults.elevatedButtonElevation(),
                                    shapes = SplitButtonDefaults.trailingButtonShapesFor(size),
                                    contentPadding = SplitButtonDefaults.trailingButtonContentPaddingFor(
                                        size
                                    ),
                                ) {
                                    val rotation: Float by
                                    animateFloatAsState(
                                        targetValue = if (checked) 180f else 0f,
                                        label = "Trailing Icon Rotation",
                                    )
                                    Icon(
                                        Icons.Filled.KeyboardArrowDown,
                                        modifier =
                                            Modifier.size(
                                                SplitButtonDefaults.trailingButtonIconSizeFor(
                                                    size
                                                )
                                            )
                                                .graphicsLayer {
                                                    this.rotationZ = rotation
                                                },
                                        contentDescription = "Localized description",
                                    )
                                }
                            }
                        }
                    )
                    DropdownMenu(
                        expanded = checked,
                        onDismissRequest = { checked = false }
                    ) {
                        val typeList = CopyMode.entries
                        typeList.forEach { mode ->
                            DropdownMenuItem(
                                text = { Text(stringResource(mode.title)) },
                                onClick = {
                                    viewModel.saveCopyMode(mode)
                                },
                                leadingIcon = {
                                    AnimatedVisibility(copyMode == mode) {
                                        Icon(
                                            imageVector = Icons.Rounded.Done,
                                            contentDescription = "Done"
                                        )
                                    }
                                    AnimatedVisibility(copyMode != mode) {
                                        Icon(
                                            imageVector = Icons.Rounded.Stars,
                                            contentDescription = "Stars"
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 签名信息列表
 */
@Composable
private fun SignatureList(
    viewModel: MainViewModel
) {
    val uiState = viewModel.verifierState
    AnimatedVisibility(
        visible = uiState is UIState.Success, enter = fadeIn(), exit = fadeOut()
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(end = 14.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (uiState is UIState.Success) {
                item { Spacer(Modifier.size(6.dp)) }
                items((uiState.result as VerifierResult).data) { verifier ->
                    Column(Modifier.padding(vertical = 8.dp)) {
                        Card(
                            border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
                            modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
                        ) {
                            if (uiState.result.isApk) {
                                Text(
                                    "Valid APK signature V${verifier.version} found",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            } else {
                                Text(
                                    "Valid KeyStore Signature V${verifier.version} found",
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 16.dp)
                                        .fillMaxWidth(),
                                    textAlign = TextAlign.Center,
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                        SignatureListTop(verifier, viewModel)
                        SignatureListCenter(verifier, viewModel)
                        SignatureListBottom(verifier, viewModel)
                    }
                }
                item { Spacer(Modifier.size(40.dp)) }
            }
        }
    }
}

/**
 * 签名验证弹窗
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SignatureDialog(
    viewModel: MainViewModel, signaturePath: MutableState<String>
) {
    if (signaturePath.value.isNotBlank()) {
        val password = remember { mutableStateOf("") }
        var options by remember { mutableStateOf<ArrayList<String>?>(null) }
        var alisa by remember { mutableStateOf(options?.getOrNull(0) ?: "") }
        AlertDialog(icon = {
            Icon(Icons.Rounded.Password, contentDescription = "Password")
        }, title = {
            Text(text = stringResource(Res.string.password_verification))
        }, text = {
            var expanded by remember { mutableStateOf(false) }
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    modifier = Modifier.padding(vertical = 4.dp),
                    value = password.value,
                    onValueChange = { value ->
                        password.value = value
                        options = viewModel.verifyAlisa(signaturePath.value, value)
                        alisa = if (!options.isNullOrEmpty()) {
                            options?.getOrNull(0) ?: ""
                        } else {
                            ""
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(Res.string.key_store_password),
                            style = MaterialTheme.typography.labelLarge
                        )
                    },
                    isError = alisa.isBlank(),
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )
                ExposedDropdownMenuBox(
                    modifier = Modifier.padding(vertical = 6.dp),
                    expanded = expanded,
                    onExpandedChange = { expanded = it }) {
                    OutlinedTextField(
                        modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
                        value = alisa,
                        readOnly = true,
                        onValueChange = { },
                        label = {
                            Text(
                                text = stringResource(Res.string.key_alias),
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        singleLine = true,
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        colors = ExposedDropdownMenuDefaults.textFieldColors()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        options?.forEach { selectionOption ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = selectionOption,
                                        style = MaterialTheme.typography.labelLarge
                                    )
                                },
                                onClick = {
                                    alisa = selectionOption
                                    expanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                            )
                        }
                    }
                }
            }
        }, onDismissRequest = {
            signaturePath.value = ""
        }, confirmButton = {
            TextButton(onClick = {
                if (alisa.isNotBlank()) {
                    viewModel.signerVerifier(
                        signaturePath.value, password.value, alisa
                    )
                    signaturePath.value = ""
                } else {
                    viewModel.updateSnackbarVisuals(Res.string.wrong_key_store_password)
                }
            }) {
                Text(text = stringResource(Res.string.confirm))
            }
        }, dismissButton = {
            TextButton(onClick = {
                signaturePath.value = ""
            }) {
                Text(text = stringResource(Res.string.cancel))
            }
        })
    }
}

/**
 * 签名信息 - { Subject，Valid from，Valid until}
 */
@Composable
private fun SignatureListTop(
    verifier: Verifier, viewModel: MainViewModel
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.subject, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Subject,
                        contentDescription = "Subject",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Subject", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.subject, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.validFrom, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.QueryBuilder,
                        contentDescription = "Valid from",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Valid from", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.validFrom, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.validUntil, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Restore,
                        contentDescription = "Valid until",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Valid until", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.validUntil, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}


/**
 * 签名信息 - { Public Key Type，Modulus，Signature Type}
 */
@Composable
private fun SignatureListCenter(
    verifier: Verifier, viewModel: MainViewModel
) {
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(vertical = 4.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.publicKeyType, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Public,
                        contentDescription = "Public Key Type",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Public Key Type", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.publicKeyType, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.modulus, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.LibraryBooks,
                        contentDescription = "Modulus",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Modulus", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.modulus, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.signatureType, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Lock,
                        contentDescription = "Signature Type",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("Signature Type", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.signatureType, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

/**
 * 签名信息 - { MD5，SHA-1，SHA-256}
 */
@Composable
private fun SignatureListBottom(
    verifier: Verifier, viewModel: MainViewModel
) {
    val copyMode by viewModel.copyMode.collectAsState()
    Card(
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.outline),
        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp).fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp)
        ) {
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.md5, copyMode, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.SentimentSatisfied,
                        contentDescription = "MD5",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("MD5", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.md5, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.sha1, copyMode, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentDissatisfied,
                        contentDescription = "SHA-1",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("SHA-1", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.sha1, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Spacer(Modifier.size(16.dp))
            Card(modifier = Modifier.fillMaxWidth(), onClick = {
                copy(verifier.sha256, copyMode, viewModel)
            }) {
                Row(
                    modifier = Modifier.padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Outlined.SentimentVeryDissatisfied,
                        contentDescription = "SHA-256",
                        modifier = Modifier.padding(end = 14.dp)
                    )
                    Column {
                        Text("SHA-256", style = MaterialTheme.typography.titleMedium)
                        Text(verifier.sha256, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}