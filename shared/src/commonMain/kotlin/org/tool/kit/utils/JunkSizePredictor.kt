package org.tool.kit.utils

import org.tool.kit.utils.AndroidJunkGenerator.Companion.ANIM_PROBABILITY
import org.tool.kit.utils.AndroidJunkGenerator.Companion.ASSET_PROBABILITY
import org.tool.kit.utils.AndroidJunkGenerator.Companion.DRAWABLE_PROBABILITY
import org.tool.kit.utils.AndroidJunkGenerator.Companion.ID_PROBABILITY
import org.tool.kit.utils.AndroidJunkGenerator.Companion.MIPMAP_PROBABILITY
import org.tool.kit.utils.AndroidJunkGenerator.Companion.STRING_PROBABILITY

object JunkSizePredictor {

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

        // generateOtherResources 会为每个 Package 额外生成大量各种各样的资源
        // 数量: Random.nextInt(packageCount * 5, packageCount * 15) -> 平均 packageCount * 10
        // 这些资源包括 Asset, Anim, Mipmap, Drawable
        val extraResourcesOverhead = packageCount * 10 * (
                (ASSET_PROBABILITY * 750.0) +
                        (ANIM_PROBABILITY * 260.0) +
                        (MIPMAP_PROBABILITY * 260.0) +
                        (DRAWABLE_PROBABILITY * 260.0)
        )

        // 基础开销：Jar 头, Manifest 头, R.txt 头, Proguard 文件, 少量 keep xml
        // 估算为 5KB (压缩后)
        val baseOverhead = 5120L

        return baseOverhead + (totalActivities * bytesPerActivityUnit).toLong() + extraResourcesOverhead.toLong()
    }

    private fun calculateTotalActivities(packageCount: Int, activityCountPerPackage: Int): Long {
        // 源码逻辑：rootClassCount = Random.nextInt(cnt) + (cnt / 2)
        // 范围 [cnt/2, 1.5*cnt]. 平均 ≈ cnt (即 activityCountPerPackage)
        val avgRootActivities = activityCountPerPackage
        return (packageCount * activityCountPerPackage).toLong() + avgRootActivities
    }

    private fun calculateUnitSize(): Double {
        // --- 详细拆解 (基于压缩后的体积估算) ---
        // 1. Class 文件 (Bytes)
        // Activity Class (主类) - 包含 5 大生命周期方法，随机注入近 300 种庞大的字节码 Snippets
        val activityClassSize = 2650.0

        // Other Class - 字段 + getter + static methods
        // 平均 2.5 个类
        val otherClassSize = 600.0

        // Listener Class - 仅在 view 有 ID 时生成 (根据 idProbability)
        val avgListeners = AVG_VIEWS_PER_LAYOUT * ID_PROBABILITY // 9.5 * 0.03 = 0.285
        val listenerClassSize = 200.0 // 匿名内部类/实现类通常很小

        val totalClassSize = activityClassSize +
                (AVG_OTHER_CLASSES * otherClassSize) +
                (avgListeners * listenerClassSize)

        // 2. 资源文件 (Bytes)
        // Layout XML: 包含各种 layoutAnimation, mipmap, drawable 等复杂注入属性
        val layoutXmlSize = 780.0

        // Vector Drawable / Mipmap (0.3 概率) -> 包含多 path 和 贝塞尔曲线
        val drawableSize = 300.0

        // String (0.3 概率, entries in strings.xml)
        val stringEntrySize = 50.0

        val totalResSize = layoutXmlSize +
                (drawableSize * DRAWABLE_PROBABILITY) +
                (stringEntrySize * STRING_PROBABILITY)

        // 3. 配置与元数据增量
        // Manifest entry (<activity.../>) + R.txt entries (layout, string, drawable, ids)
        val manifestEntry = 65.0
        val rTextEntries = 30.0

        val configOverhead = manifestEntry + rTextEntries

        return totalClassSize + totalResSize + configOverhead
    }
}