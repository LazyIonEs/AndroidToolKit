package org.tool.kit.utils

object JunkSizePredictor {

    // 基于 AndroidJunkGenerator 的常量定义
    private const val ID_PROBABILITY = 0.03
    private const val DRAWABLE_PROBABILITY = 0.3
    private const val STRING_PROBABILITY = 0.3

    // 布局 View 数量: Random.nextInt(2, 18) -> 平均 9.5
    private const val AVG_VIEWS_PER_LAYOUT = 9.5

    // 额外类数量:
    // 1. 内部循环 repeat(Random.nextInt(1, 3)) -> 平均 1.5 个
    // 2. onCreate 中 new 一个 -> 1 个
    // 总计 2.5 个
    private const val AVG_OTHER_CLASSES = 2.5

    /**
     * 估算生成的 AAR 大小
     * @param packageCount 包数量
     * @param activityCountPerPackage 每个包 Activity 数量
     * @return 估算的字节数 (Bytes)
     */
    fun estimateAarSize(packageCount: Int, activityCountPerPackage: Int): Long {
        val totalActivities = calculateTotalActivities(packageCount, activityCountPerPackage)
        val bytesPerActivityUnit = calculateUnitSize()

        // 基础开销：Jar 头, Manifest 头, R.txt 头, Proguard 文件, 少量 keep xml
        // 估算为 4KB (压缩后)
        val baseOverhead = 4096L

        return baseOverhead + (totalActivities * bytesPerActivityUnit).toLong()
    }

    private fun calculateTotalActivities(packageCount: Int, activityCountPerPackage: Int): Long {
        // 源码逻辑：rootClassCount = Random.nextInt(cnt) + (cnt / 2)
        // 范围 [cnt/2, 1.5*cnt]. 平均 ≈ cnt (即 activityCountPerPackage)
        val avgRootActivities = activityCountPerPackage
        return (packageCount * activityCountPerPackage).toLong() + avgRootActivities
    }

    private fun calculateUnitSize(): Double {
        // --- 详细拆解 (基于压缩后的体积估算) ---
        // 基于实验数据校准：Average unit size ≈ 3157 Bytes (针对性能优化后的版本校准)

        // 1. Class 文件 (Bytes)
        // Activity Class (主类) - 包含较多逻辑 (onCreate, initViews, random calls)
        // 包含大量字符串常量 (generateBigValue) 用于字段赋值，显著增加体积
        val activityClassSize = 1090.0

        // Other Class - 字段 + getter + static methods
        // 平均 2.5 个类
        val otherClassSize = 550.0

        // Listener Class - 仅在 view 有 ID 时生成 (根据 idProbability)
        val avgListeners = AVG_VIEWS_PER_LAYOUT * ID_PROBABILITY // 9.5 * 0.03 = 0.285
        val listenerClassSize = 200.0 // 匿名内部类/实现类通常很小

        val totalClassSize = activityClassSize +
                (AVG_OTHER_CLASSES * otherClassSize) +
                (avgListeners * listenerClassSize)

        // 2. 资源文件 (Bytes)
        // Layout XML: ~10 Views + Attributes. 文本压缩率高.
        val layoutXmlSize = 445.0

        // Vector Drawable (0.3 概率)
        val drawableSize = 250.0

        // String (0.3 概率, entries in strings.xml)
        val stringEntrySize = 100.0

        val totalResSize = layoutXmlSize +
                (drawableSize * DRAWABLE_PROBABILITY) +
                (stringEntrySize * STRING_PROBABILITY)

        // 3. 配置与元数据增量
        // Manifest entry (<activity.../>) + R.txt entries (layout, string, drawable, ids)
        val manifestEntry = 65.0
        val rTextEntries = 20.0

        val configOverhead = manifestEntry + rTextEntries

        return totalClassSize + totalResSize + configOverhead
    }
}