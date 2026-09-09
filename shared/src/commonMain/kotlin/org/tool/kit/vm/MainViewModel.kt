package org.tool.kit.vm

import androidx.compose.material3.SnackbarDuration
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import brut.androlib.ApkBuilder
import brut.androlib.ApkDecoder
import brut.androlib.Config
import brut.androlib.res.xml.ResXmlUtils
import brut.directory.ExtFile
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted.Companion.Eagerly
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import org.tool.kit.domain.preferences.PreferenceChange
import org.tool.kit.domain.preferences.JunkPreference
import org.tool.kit.feature.app.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getString
import org.tool.kit.constant.ConfigConstant
import org.tool.kit.core.validation.LatestRequest
import org.tool.kit.domain.repository.KeyStoreRepository
import org.tool.kit.domain.repository.StorageCapacity
import org.tool.kit.domain.repository.StorageRepository
import org.tool.kit.model.ApkToolInfo
import org.tool.kit.model.DarkThemeConfig
import org.tool.kit.model.IconFactoryData
import org.tool.kit.model.IconFactoryInfo
import org.tool.kit.model.JunkCodeInfo
import org.tool.kit.model.JunkMode
import org.tool.kit.model.PendingDeletionFile
import org.tool.kit.model.Sequence
import org.tool.kit.model.Sign
import org.tool.kit.platform.RustException
import org.tool.kit.platform.mozJpeg
import org.tool.kit.platform.oxipng
import org.tool.kit.platform.quantize
import org.tool.kit.platform.resizeFir
import org.tool.kit.platform.resizePng
import org.tool.kit.shared.generated.resources.Res
import org.tool.kit.shared.generated.resources.build_end
import org.tool.kit.shared.generated.resources.build_failure
import org.tool.kit.shared.generated.resources.cleanup_complete
import org.tool.kit.shared.generated.resources.file_deletion_exception
import org.tool.kit.shared.generated.resources.icon_creation_failed
import org.tool.kit.shared.generated.resources.icon_generation_completed
import org.tool.kit.shared.generated.resources.jump
import org.tool.kit.shared.generated.resources.scanning_anomalies
import org.tool.kit.utils.AndroidJunkGenerator
import org.tool.kit.utils.MultiAarGenerator
import org.tool.kit.utils.formatFileSize
import org.tool.kit.utils.getFileLength
import org.tool.kit.utils.isJPEG
import org.tool.kit.utils.isJPG
import org.tool.kit.utils.isPng
import org.tool.kit.utils.renameManifestPackage
import org.tool.kit.utils.renameValueAppName
import org.tool.kit.utils.resourcesDir
import org.tool.kit.utils.update
import java.io.File

private val logger = KotlinLogging.logger("MainViewModel")

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/1/31 14:45
 * @Description : MainViewModel
 * @Version     : 1.0
 */
class MainViewModel(
    private val preferences: org.tool.kit.domain.preferences.PreferencesRepository,
    private val storage: StorageRepository,
    keyStores: KeyStoreRepository,
    private val effects: org.tool.kit.feature.app.AppEffectSink,
    initialCapacity: StorageCapacity = StorageCapacity(0, 0),
    private val signApk: org.tool.kit.domain.usecase.SignApkUseCase,
) :
    ViewModel() {

    // Read-only legacy projections; remove each when its last feature migrates.
    val themeConfig = preferences.state.map { DarkThemeConfig.valueOf(it.themeConfig.name) }.stateIn(viewModelScope, Eagerly, DarkThemeConfig.valueOf(preferences.state.value.themeConfig.name))
    val userData = preferences.state.map { it.userData }.stateIn(viewModelScope, Eagerly, preferences.state.value.userData)
    val iconFactoryData = preferences.state.map { it.iconFactoryData }.stateIn(viewModelScope, Eagerly, preferences.state.value.iconFactoryData)
    val isHuaweiAlignFileSize = preferences.state.map { it.isHuaweiAlignFileSize }.stateIn(viewModelScope, Eagerly, preferences.state.value.isHuaweiAlignFileSize)

    // 垃圾代码生成信息
    private val _junkCodeInfoState = mutableStateOf(JunkCodeInfo())
    val junkCodeInfoState by _junkCodeInfoState

    // 垃圾代码生成UI状态
    private val _junkCodeUIState = mutableStateOf<UIState>(UIState.WAIT)
    val junkCodeUIState by _junkCodeUIState

    val junkMode = preferences.state.map { JunkMode.valueOf(it.junkMode.name) }.stateIn(viewModelScope, Eagerly, JunkMode.valueOf(preferences.state.value.junkMode.name))

    // 图标工厂信息
    private val _iconFactoryInfoState = mutableStateOf(IconFactoryInfo())
    val iconFactoryInfoState by _iconFactoryInfoState

    // 图标工厂UI状态
    private val _iconFactoryUIState = mutableStateOf<UIState>(UIState.WAIT)
    val iconFactoryUIState by _iconFactoryUIState

    // 扫描的文件列表
    private val _pendingDeletionFileList = mutableStateListOf<PendingDeletionFile>()
    val pendingDeletionFileList: List<PendingDeletionFile> = _pendingDeletionFileList

    // 文件清理UI状态
    private val _fileClearUIState = mutableStateOf<UIState>(UIState.WAIT)
    val fileClearUIState by _fileClearUIState

    // 是否在清理中
    var isClearing = false

    // 文件列表排序
    private var _currentFileSequence = mutableStateOf<Sequence>(Sequence.SIZE_LARGE_TO_SMALL)
    val currentFileSequence by _currentFileSequence

    // 空包生成信息
    private val _apkToolInfoState = mutableStateOf(ApkToolInfo())
    val apkToolInfoState by _apkToolInfoState

    // 空包生成UI状态
    private val _apkToolInfoUIState = mutableStateOf<UIState>(UIState.WAIT)
    val apkToolInfoUIState by _apkToolInfoUIState

    private val pathChecks = LegacyPathChecks(viewModelScope, storage)
    val pathValidation = pathChecks.state
    private val capacityRequest = LatestRequest(viewModelScope)
    private val _storageCapacity = MutableStateFlow(initialCapacity)
    val storageCapacity = _storageCapacity.asStateFlow()

    private val apkToolChecks = LegacySignValidation(viewModelScope, storage, keyStores) { aliases ->
        updateApkToolInfo(apkToolInfoState.copy(keyStoreAlisaList = aliases?.let(::ArrayList)))
    }
    val apkToolValidation = apkToolChecks.state

    init {
        // Compatibility bridge: remove one branch per Phase 4A/5/6/7/8 cutover.
        // Three forms still belong to MainViewModel; migrated features own their subscriptions.
        viewModelScope.launch {
            preferences.state.filter { it.ready }
                .map { it.outputPathVersion to it.userData.defaultOutputPath }
                .distinctUntilChanged().collect { (_, path) ->
                    updateJunkCodeInfo(junkCodeInfoState.copy(outputPath = path))
                    updateIconFactoryInfo(iconFactoryInfoState.copy(outputPath = path))
                    updateApkToolInfo(apkToolInfoState.copy(outputPath = path))
                }
        }
    }

    fun refreshStorageCapacity() {
        capacityRequest.launch(block = { storage.readCapacity() }) { _storageCapacity.value = it }
    }

    fun updateApkToolStorePassword(password: String) {
        updateApkToolInfo(apkToolInfoState.copy(keyStorePassword = password))
        apkToolChecks.passwordChanged(apkToolInfoState)
    }


    fun hasPendingPathChecks(vararg fields: LegacyPathField): Boolean =
        fields.any { pathValidation.value[it]?.pending == true }

    fun refreshPathChecks(vararg fields: LegacyPathField) = pathChecks.refresh(*fields)

    fun refreshApkToolChecks() {
        refreshPathChecks(LegacyPathField.APK_TOOL_OUTPUT, LegacyPathField.APK_TOOL_ICON, LegacyPathField.APK_TOOL_KEYSTORE)
        apkToolChecks.refreshAliasPassword(apkToolInfoState)
    }

    /** Legacy icon editor commits only on the original release callbacks; remove in Phase 8. */
    fun saveIconFactoryData(iconFactoryData: IconFactoryData) {
        preferences.change(PreferenceChange.IconSettings(iconFactoryData))
    }

    private fun updateSnackbarVisuals(value: SnackbarMessage) {
        viewModelScope.launch {
            if (!effects.send("legacy", value)) logger.debug { "Window effect sink closed" }
        }
    }
    fun updateSnackbarVisuals(value: String) = updateSnackbarVisuals(SnackbarMessage(UiMessage.Text(value)))
    fun updateSnackbarVisuals(resource: StringResource) = updateSnackbarVisuals(SnackbarMessage(UiMessage.Resource(resource)))

    /**
     * 修改JunkCodeInfo
     * @param junkCodeInfo JunkCodeInfo
     * @see JunkCodeInfo
     */
    fun updateJunkCodeInfo(junkCodeInfo: JunkCodeInfo) {
        _junkCodeInfoState.update { junkCodeInfo }
        pathChecks.validate(LegacyPathField.JUNK_OUTPUT, junkCodeInfo.outputPath, PathKind.DIRECTORY)
    }

    /**
     * 修改IconFactoryInfo
     * @param iconFactoryInfo IconFactoryInfo
     * @see IconFactoryInfo
     */
    fun updateIconFactoryInfo(iconFactoryInfo: IconFactoryInfo) {
        _iconFactoryInfoState.update { iconFactoryInfo }
    }

    /**
     * 修改ApkToolInfo
     * @param apkToolInfo ApkToolInfo
     * @see ApkToolInfo
     */
    fun updateApkToolInfo(apkToolInfo: ApkToolInfo) {
        _apkToolInfoState.update { apkToolInfo }
        pathChecks.validate(LegacyPathField.APK_TOOL_OUTPUT, apkToolInfo.outputPath, PathKind.DIRECTORY)
        pathChecks.validate(LegacyPathField.APK_TOOL_ICON, apkToolInfo.icon, PathKind.FILE)
        pathChecks.validate(LegacyPathField.APK_TOOL_KEYSTORE, apkToolInfo.keyStorePath, PathKind.FILE)
        apkToolChecks.formChanged(apkToolInfo)
    }

    /**
     * 更新垃圾代码模式
     */
    fun saveJunkMode(junkMode: JunkMode) {
        preferences.change(PreferenceChange.JunkModeChanged(JunkPreference.valueOf(junkMode.name)))
    }

    private fun signingRequest(outputPath: String, apkPath: String, sign: Sign): org.tool.kit.domain.signing.SignApkRequest {
        val snapshot = preferences.state.value
        return org.tool.kit.domain.signing.SignApkRequest(apkPath, outputPath, "",
            snapshot.userData.defaultSignerSuffix, snapshot.userData.duplicateFileRemoval,
            snapshot.userData.alignFileSize, snapshot.isHuaweiAlignFileSize, ConfigConstant.APK.Huawei.path,
            org.tool.kit.domain.signing.ApkSigningPolicy.valueOf(sign.keyStorePolicy.name),
            sign.v4SignatureOutputFileName,
            org.tool.kit.domain.signing.SigningCredentials(sign.keyStorePath, sign.keyStorePassword,
                sign.keyStoreAlisaList?.getOrNull(sign.keyStoreAlisaIndex), sign.keyStoreAlisaPassword))
    }

    /** Temporary ApkTool adapter, removed when BuildApkUseCase takes ownership in Phase 6. */
    private suspend fun legacySignForApkTool(outputPath: String, apkPath: String, sign: Sign): File? =
        (signApk(signingRequest(outputPath, apkPath, sign)) as? org.tool.kit.domain.signing.SignApkOutcome.Success)
            ?.takeIf { it.outputExists }?.let { File(it.outputPath) }

    /**
     * 生成垃圾代码 aar
     */
    fun generateJunkCode() = viewModelScope.launch(Dispatchers.IO) {
        _junkCodeUIState.update { UIState.Loading }
        val start = System.currentTimeMillis()
        logger.info { "generateJunkCode 生成垃圾代码开始, 模式: ${junkMode.value.title}, 垃圾代码生成信息: $junkCodeInfoState" }
        try {
            val dir = resourcesDir
            val output = junkCodeInfoState.outputPath
            
            val resultFile = if (junkMode.value == JunkMode.MULTI) {
                val outputDir = junkCodeInfoState.outputDir
                val aarCount = junkCodeInfoState.aarCount.toIntOrNull() ?: 0
                val leastPackageCount = junkCodeInfoState.leastPackageCount.toIntOrNull() ?: 0
                val maximumPackageCount = junkCodeInfoState.maximumPackageCount.toIntOrNull() ?: 0
                val leastActivityCount = junkCodeInfoState.leastActivityCountPerPackage.toIntOrNull() ?: 0
                val maximumActivityCount = junkCodeInfoState.maximumActivityCountPerPackage.toIntOrNull() ?: 0

                MultiAarGenerator.generate(
                    resourcesDir = dir,
                    outputPath = output,
                    outputDir = outputDir,
                    aarCount = aarCount,
                    leastPackageCount = leastPackageCount,
                    maximumPackageCount = maximumPackageCount,
                    leastActivityCount = leastActivityCount,
                    maximumActivityCount = maximumActivityCount
                )
                File(output, outputDir)
            } else {
                val appPackageName = junkCodeInfoState.packageName + "." + junkCodeInfoState.suffix
                val packageCount = junkCodeInfoState.packageCount.toIntOrNull() ?: 0
                val activityCountPerPackage = junkCodeInfoState.activityCountPerPackage.toIntOrNull() ?: 0
                val resPrefix = junkCodeInfoState.resPrefix
                val androidJunkGenerator =
                    AndroidJunkGenerator(
                        dir,
                        output,
                        appPackageName,
                        packageCount,
                        activityCountPerPackage,
                        resPrefix
                    )
                androidJunkGenerator.startGenerate()
            }
            
            val totalSize = if (resultFile.isDirectory) {
                resultFile.walkBottomUp().filter { it.isFile }.sumOf { it.length() }
            } else {
                resultFile.length()
            }
            
            logger.info { "generateJunkCode 生成垃圾代码结束, 耗时: ${System.currentTimeMillis() - start}ms, aar大小: ${totalSize.formatFileSize()}, 输出路径: ${resultFile.absolutePath}" }
            val snackbarVisualsData = SnackbarMessage(
                message = UiMessage.Text(getString(Res.string.build_end, totalSize.formatFileSize())),
                actionLabel = getString(Res.string.jump),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = SnackbarAction.OpenDirectory(resultFile.path))
            updateSnackbarVisuals(snackbarVisualsData)
        } catch (e: Exception) {
            logger.error(e) { "generateJunkCode 生成垃圾代码异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.build_failure))
        } finally {
            _junkCodeUIState.update { UIState.WAIT }
        }
    }

    /**
     * 生成自定义空包
     */
    fun generateApktool() = viewModelScope.launch(Dispatchers.IO) {
        _apkToolInfoUIState.update { UIState.Loading }
        val outApktoolCacheDir = File(resourcesDir, "apktool")
        try {
            logger.info { "generateApktool 生成空包开始, 空包信息: $apkToolInfoState" }
            val apkIcon = apkToolInfoState.icon
            val packageName = apkToolInfoState.packageName
            val targetSdkVersion = apkToolInfoState.targetSdkVersion
            val minSdkVersion = apkToolInfoState.minSdkVersion
            val versionCode = apkToolInfoState.versionCode.toInt()
            val versionName = apkToolInfoState.versionName
            val appName = apkToolInfoState.appName
            val outApktoolFile = File(apkToolInfoState.outputPath, "$appName.apk")
            val apkFile = ExtFile(ConfigConstant.APKTOOL_FILE)
            val config = Config(versionName)
            config.isAnalysisMode = true
            config.isForced = true
            config.isDebuggable = true
            logger.info { "generateApktool 开始解包" }
            // 开始解包
            val apkDecoder = ApkDecoder(apkFile, config)
            apkDecoder.decode(outApktoolCacheDir)
            val apkInfo = apkDecoder.apkInfo
            // 解包完成
            logger.info { "generateApktool 解包完成, ApkInfo: $apkInfo" }
            val androidManifestXmlFile = File(outApktoolCacheDir, "AndroidManifest.xml")
            // 删除Manifest中versionCode和versionName
            ResXmlUtils.removeManifestVersions(androidManifestXmlFile)
            logger.info { "generateApktool 删除Manifest中versionCode和versionName" }
            // 替换Manifest中minSdkVersion和targetSdkVersion
            renameManifestPackage(androidManifestXmlFile, packageName, minSdkVersion, targetSdkVersion)
            logger.info { "generateApktool 替换Manifest中package、minSdkVersion和targetSdkVersion" }
            // 替换strings中app_name的值
            val stringsFile = File(outApktoolCacheDir, "res/values/strings.xml")
            renameValueAppName(stringsFile, appName)
            logger.info { "generateApktool 替换strings中app_name的值" }
            // 替换icon
            if (apkIcon.isNotBlank()) {
                val apkIconFile = File(apkIcon)
                val suffix = apkIconFile.extension
                val densities = ConfigConstant.ICON_FILE_LIST
                for (density in densities.withIndex()) {
                    val targetFolderFile = File(outApktoolCacheDir, "res/mipmap-${density.value}")
                    targetFolderFile.deleteRecursively()
                    val targetFile = File(targetFolderFile, "ic_launcher.${suffix}")
                    apkIconFile.copyTo(targetFile, overwrite = true)
                }
                logger.info { "generateApktool 替换icon" }
            }
            // 替换部分信息到apktool.yml
            apkInfo.versionInfo.versionCode = versionCode
            apkInfo.versionInfo.versionName = versionName
            apkInfo.sdkInfo.minSdkVersion = minSdkVersion
            apkInfo.sdkInfo.targetSdkVersion = targetSdkVersion
            apkInfo.save(outApktoolCacheDir)
            logger.info { "generateApktool 替换部分信息到apktool.yml" }
            // 开始打包
            logger.info { "generateApktool 开始打包" }
            val outApktoolCacheDirExt = ExtFile(outApktoolCacheDir)
            val apkBuilder = ApkBuilder(outApktoolCacheDirExt, config)
            apkBuilder.build(outApktoolFile)
            logger.info { "generateApktool 打包完成" }
            if (apkToolInfoState.enableSign) {
                logger.info { "generateApktool 开始签名" }
                legacySignForApkTool(
                    outputPath = apkToolInfoState.outputPath,
                    apkPath = outApktoolFile.path,
                    sign = apkToolInfoState
                )
            }
            val snackbarVisualsData = SnackbarMessage(
                message = UiMessage.Text(getString(Res.string.build_end, outApktoolFile.length().formatFileSize())),
                actionLabel = getString(Res.string.jump),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = SnackbarAction.OpenDirectory(outApktoolFile.path))
            updateSnackbarVisuals(snackbarVisualsData)
        } catch (e: Exception) {
            logger.error(e) { "generateApktool 生成空包异常, 异常信息: ${e.message}" }
            updateSnackbarVisuals(e.message ?: getString(Res.string.build_failure))
        } finally {
            outApktoolCacheDir.deleteRecursively()
            _apkToolInfoUIState.update { UIState.WAIT }
        }
    }

    /**
     * 图标生成
     * @param path 图标路径
     */
    fun iconGeneration(path: String) = viewModelScope.launch(Dispatchers.IO) {
        _iconFactoryUIState.update { UIState.Loading }
        val iconFactory = iconFactoryData.value
        val densities = ConfigConstant.ICON_FILE_LIST
        val sizes = ConfigConstant.ICON_SIZE_LIST
        val inputFile = File(path)
        val outputDir = File(iconFactoryInfoState.outputPath, iconFactoryInfoState.fileDir)

        updateIconFactoryInfo(iconFactoryInfoState.copy(result = null))

        val suffix = if (path.isPng) {
            ".png"
        } else if (path.isJPG) {
            ".jpg"
        } else if (path.isJPEG) {
            ".jpeg"
        } else {
            return@launch
        }

        logger.info { "iconGeneration 图标生成开始, 图标文件路径: $path" }

        var isSuccess = true
        var error = ""
        val result = mutableListOf<File>()
        for ((index, density) in densities.withIndex()) {
            val size = sizes[index]
            val outputFile =
                File(
                    outputDir,
                    "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}${suffix}"
                )
            val outputSizeFile = File(
                outputDir,
                "${iconFactoryInfoState.iconDir}-${density}/${iconFactoryInfoState.iconName}_resize${suffix}"
            )
            outputFile.parentFile.mkdirs()
            outputFile.delete()
            outputSizeFile.delete()
            try {
                if (path.isPng) {
                    resizePng(
                        inputPath = inputFile.absolutePath,
                        outputPath = outputSizeFile.absolutePath,
                        width = size,
                        height = size,
                        typIdx = iconFactory.pngTypIdx.typIdx.toUByte()
                    )
                    if (iconFactory.lossless) {
                        oxipng(
                            inputPath = outputSizeFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            preset = iconFactory.preset
                        )
                    } else {
                        quantize(
                            inputPath = outputSizeFile.absolutePath,
                            outputPath = outputFile.absolutePath,
                            minimum = iconFactory.minimum,
                            target = iconFactory.target,
                            speed = iconFactory.speed,
                            preset = iconFactory.preset
                        )
                    }
                } else if (path.isJPG || path.isJPEG) {
                    resizeFir(
                        inputPath = inputFile.absolutePath,
                        outputPath = outputSizeFile.absolutePath,
                        width = size,
                        height = size,
                        typIdx = iconFactory.jpegTypIdx.typIdx.toUByte()
                    )
                    mozJpeg(
                        inputPath = outputSizeFile.absolutePath,
                        outputPath = outputFile.absolutePath,
                        quality = if (iconFactory.lossless) 100f else iconFactory.quality
                    )
                }
                logger.info { "iconGeneration 图标生成完成, 任务索引: $index, 图标大小: $size, 输出文件路径: ${outputFile.absolutePath}" }
                result.add(outputFile)
            } catch (e: RustException) {
                logger.error(e) { "iconGeneration 图标生成异常, 异常信息: ${e.message}" }
                isSuccess = false
                error = e.message ?: getString(Res.string.icon_creation_failed)
                break
            } catch (e: Exception) {
                logger.error(e) { "iconGeneration 图标生成异常, 异常信息: ${e.message}" }
                isSuccess = false
                error = e.message ?: getString(Res.string.icon_creation_failed)
                break
            }
            outputSizeFile.delete()
        }
        updateIconFactoryInfo(iconFactoryInfoState.copy(result = result))
        _iconFactoryUIState.update { UIState.WAIT }
        if (isSuccess) {
            val snackbarVisualsData = SnackbarMessage(
                message = UiMessage.Text(getString(Res.string.icon_generation_completed)),
                actionLabel = getString(Res.string.jump),
                withDismissAction = true,
                duration = SnackbarDuration.Short,
                action = SnackbarAction.OpenDirectory(outputDir.path))
            updateSnackbarVisuals(snackbarVisualsData)
        } else {
            updateSnackbarVisuals(error)
        }
    }

    /**
     * 扫描自定义文件夹
     */
    fun scanPendingDeletionFileList(directory: File) {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            logger.info { "scanPendingDeletionFileList 扫描自定义文件夹开始" }
            withContext(Dispatchers.Main) {
                _fileClearUIState.update { UIState.Loading }
            }
            _pendingDeletionFileList.clear()
            // 文件总大小
            var totalLength = 0L
            directory.walk()
                .maxDepth(10)
                // 如果父目录是缓存目录，不再继续遍历此目录下的文件
                .onEnter { file -> file.parentFile?.nameWithoutExtension != "build" }
                .filter { file -> file.isDirectory && file.nameWithoutExtension == "build" }
                .forEach { file ->
                    val length = file.getFileLength()
                    withContext(Dispatchers.Main) {
                        _pendingDeletionFileList.add(
                            PendingDeletionFile(
                                directoryPath = directory.absolutePath,
                                file = file,
                                filePath = file.absolutePath,
                                fileLastModified = file.lastModified(),
                                fileLength = length
                            )
                        )
                        totalLength += length
                        updateFileSort()
                    }
                }
            logger.info { "scanPendingDeletionFileList 扫描自定义文件夹结束, 耗时: ${System.currentTimeMillis() - start}ms, 扫描目录数: ${_pendingDeletionFileList.size}, 扫描文件总大小: ${totalLength.formatFileSize()}" }
            withContext(Dispatchers.Main) {
                _fileClearUIState.update { UIState.WAIT }
                refreshStorageCapacity()
                if (_pendingDeletionFileList.isEmpty()) {
                    updateSnackbarVisuals(Res.string.scanning_anomalies)
                }
            }
        }
    }

    /**
     * 更改文件排序方式
     */
    fun updateFileSort(sequence: Sequence = currentFileSequence) {
        _currentFileSequence.update { sequence }
        when (currentFileSequence) {
            Sequence.DATE_NEW_TO_OLD -> {
                _pendingDeletionFileList.sortByDescending { it.fileLastModified }
            }

            Sequence.DATE_OLD_TO_NEW -> {
                _pendingDeletionFileList.sortBy { it.fileLastModified }
            }

            Sequence.SIZE_LARGE_TO_SMALL -> {
                _pendingDeletionFileList.sortByDescending { it.fileLength }
            }

            Sequence.SIZE_SMALL_TO_LARGE -> {
                _pendingDeletionFileList.sortBy { it.fileLength }
            }

            Sequence.NAME_A_TO_Z -> {
                _pendingDeletionFileList.sortBy { it.filePath }
            }

            Sequence.NAME_Z_TO_A -> {
                _pendingDeletionFileList.sortByDescending { it.filePath }
            }
        }
    }

    /**
     * 改变文件选中状态
     */
    fun changeFileChecked(pendingDeletionFile: PendingDeletionFile, check: Boolean) {
        _pendingDeletionFileList.find { file -> file.file == pendingDeletionFile.file }?.checked =
            check
    }

    /**
     * 关闭文件选择
     */
    fun closeFileCheck() {
        _pendingDeletionFileList.clear()
    }

    /**
     * 全选或取消全选
     */
    fun changeFileAllChecked() {
        val isAllCheck = _pendingDeletionFileList.none { file -> !file.checked }
        _pendingDeletionFileList.forEach { file ->
            if (file.checked == !isAllCheck) return@forEach
            changeFileChecked(file, !isAllCheck)
        }
    }

    /**
     * 移除选中的文件
     */
    fun removeFileChecked() {
        viewModelScope.launch(Dispatchers.IO) {
            val start = System.currentTimeMillis()
            logger.info { "removeFileChecked 删除文件夹开始" }
            withContext(Dispatchers.Main) {
                isClearing = true
                _fileClearUIState.update { UIState.Loading }
            }
            val resultList = mutableListOf<Boolean>()
            val fileIterator = _pendingDeletionFileList.iterator()
            var clearLength = 0L
            while (fileIterator.hasNext()) {
                val pendingDeletionFile = fileIterator.next()
                if (pendingDeletionFile.checked) {
                    val result = pendingDeletionFile.file.deleteRecursively()
                    if (result) {
                        clearLength += pendingDeletionFile.fileLength
                        withContext(Dispatchers.Main) {
                            fileIterator.remove()
                        }
                    } else {
                        pendingDeletionFile.exception = true
                    }
                    resultList.add(result)
                }
            }
            val successCount = resultList.filter { it }.size
            val errorCount = resultList.size - successCount
            logger.info { "removeFileChecked 删除文件夹结束, 耗时: ${System.currentTimeMillis() - start}ms, 删除文件数: ${resultList.size}, 删除成功数: $successCount, 删除失败数: $errorCount, 删除文件总大小: ${clearLength.formatFileSize()}" }
            withContext(Dispatchers.Main) {
                isClearing = false
                _fileClearUIState.update { UIState.WAIT }
                refreshStorageCapacity()
                val message = if (errorCount == 0) {
                    // 全部删除成功
                    getString(Res.string.cleanup_complete, clearLength.formatFileSize())
                } else {
                    getString(Res.string.file_deletion_exception, errorCount)
                }
                updateSnackbarVisuals(message)
            }
        }
    }

    /**
     * 当前是否没有选中文件
     */
    fun isAllFileUnchecked(): Boolean = _pendingDeletionFileList.none { file -> file.checked }


}

sealed interface UIState {
    data object WAIT : UIState
    data object Loading : UIState
    data class Success(val result: Any) : UIState
}
