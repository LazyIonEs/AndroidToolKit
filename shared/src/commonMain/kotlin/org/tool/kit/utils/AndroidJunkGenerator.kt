package org.tool.kit.utils

import io.github.oshai.kotlinlogging.KotlinLogging
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Label
import org.objectweb.asm.Opcodes
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.stream.IntStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.random.Random

/**
 * @Author      : Shihwan
 * @CreateDate  : 2024/4/2 19:46
 * @Description : 垃圾代码生成
 * @Version     : 1.0
 */

private val logger = KotlinLogging.logger("AndroidJunkGenerator")

private const val ANDROID_SCHEMA = "http://schemas.android.com/apk/res/android"

private val KEYWORDS = setOf( /*基本数据类型*/
    "boolean",
    "byte",
    "char",
    "short",
    "int",
    "long",
    "float",
    "double",
    "String",  /*用于定义访问权限修饰符的关键字*/
    "private",
    "protected",
    "public",  /*用于定义类、函数、变量修饰符的关键字*/
    "abstract",
    "final",
    "static",
    "synchronized",  /*用于定义类与类之间关系的关键字*/
    "extends",
    "implements",  /*用于定义类的类型*/
    "class",
    "interface",  /*用于定义建立实例及引用实例、判断实例的关键字*/
    "new",
    "this",
    "super",
    "instanceof",  /*用于异常处理的关键字*/
    "try",
    "catch",
    "finally",
    "throw",
    "throws",  /*用于包的关键字*/
    "package",
    "import",  /*其他修饰符关键字*/
    "native",
    "strictfp",
    "transient",
    "volatile",
    "assert",
    "null",
    "goto",
    "void",
    "const",
    "continue",
    "default",
    "false",
    "true",
    "case",
    "enum",
    "for",
    "else",
    "do",
    "if",
    "while",
    "return",
    "break",
    "switch"
)

private val XML_KEYWORDS = setOf("null")

private val CHARACTER = "abcdefghijklmnopqrstuvwxyz".toCharArray()

private val COLORS = "0123456789abcdef".toCharArray()

private val VIEW_GROUPS = arrayOf(
    "FrameLayout",
    "LinearLayout",
    "RelativeLayout",
    "GridLayout"
)

private val VIEWS = arrayOf(
    "Button",
    "ImageButton",
    "ImageView",
    "ProgressBar",
    "TextView",
    "ViewFlipper",
    "ListView",
    "GridView",
    "StackView",
    "AdapterViewFlipper"
)

private val DRAWABLE_DIRS = arrayOf("drawable", "drawable-hdpi", "drawable-mdpi", "drawable-xhdpi", "drawable-xxhdpi", "drawable-xxxhdpi")
private val MIPMAP_DIRS = arrayOf("mipmap", "mipmap-hdpi", "mipmap-mdpi", "mipmap-xhdpi", "mipmap-xxhdpi", "mipmap-xxxhdpi")

class AndroidJunkGenerator(
    // 工作目录
    dir: String,
    // 输出保存的目录
    private val output: String,
    // 包名
    private val appPackageName: String,
    // 包数量
    private val packageCount: Int,
    // 每个包里 activity 的数量
    private val activityCountPerPackage: Int,
    // 资源前缀
    private val resPrefix: String
) {
    private val workspace = File(dir, appPackageName.replace(".", ""))

    private val classesDir = "classes"

    private val mCheckActivityNames = ConcurrentHashMap.newKeySet<String>(1024)
    private val mCheckClassName = ConcurrentHashMap.newKeySet<String>()

    private val mDrawableIds = ConcurrentHashMap.newKeySet<String>(4098)
    private val mAnimIds = ConcurrentHashMap.newKeySet<String>(4098)
    private val mMipmapIds = ConcurrentHashMap.newKeySet<String>(4098)
    private val mLayoutIds = ConcurrentHashMap.newKeySet<String>(max(packageCount * activityCountPerPackage, 1024))
    private val mStringIds = ConcurrentHashMap.newKeySet<String>(4098)
    private val mIds = ConcurrentHashMap.newKeySet<String>(4098)

    private val mActivities = ConcurrentHashMap.newKeySet<String>(max(packageCount * activityCountPerPackage, 1024))

    private val mRClassType = getTypedName(appPackageName, "R")

    companion object {
        const val ID_PROBABILITY = 0.03  // id 生成概率 3%
        const val DRAWABLE_PROBABILITY = 0.2
        const val STRING_PROBABILITY = 0.1
        const val MIPMAP_PROBABILITY = 0.05
        const val ANIM_PROBABILITY = 0.03
        const val ASSET_PROBABILITY = 0.01
        const val LIFECYCLE_PROBABILITY = 0.2
    }

    fun startGenerate(): File {
        // 清理原工作目录中的文件
        logger.info { "startGenerate 准备生成, 正在清理工作空间 工作空间目录: ${workspace.absolutePath}" }

        workspace.deleteRecursively()
        logger.info { "startGenerate 工作空间已就绪, 开始生成" }

        val start = System.nanoTime()
        generateClasses()
        generateManifest()
        logger.info { "startGenerate class 文件生成完成" }

        generateStringsFile()
        generateOtherResources()
        generateKeepProguard()

        writeRFile()
        logger.info { "startGenerate 资源文件生成完成, 开始打包" }

        // 正在打包
        val outPath = assembleAar()
        logger.info {
            "startGenerate 打包完成, 输出文件路径: $outPath , 文件大小: ${
                outPath.length().formatFileSize()
            }"
        }

        val end = System.nanoTime()

        val timeMills = (end - start) / 1_000_000
        val s = timeMills / 1000
        val ms = timeMills % 1000

        logger.info { "startGenerate 生成结束, 用时: ${s}.${ms} 秒" }

        workspace.deleteRecursively()

        return outPath
    }

    private fun generateClasses() {
        IntStream.range(0, packageCount).parallel().forEach { _ ->
            val packageName = generatePackageName()
            // 生成Activity
            (0 until activityCountPerPackage).forEach { _ ->
                val packageName1 = "$appPackageName.$packageName"
                val activityName = generateClassName(packageName1)
                generateActivity(packageName1, activityName)
            }
        }

        val rootClassCount: Int =
            Random.nextInt(activityCountPerPackage) + (activityCountPerPackage shr 1)

        IntStream.range(0, rootClassCount).parallel().forEach { _ ->
            val activityPreName: String = generateClassName(appPackageName)
            generateActivity(appPackageName, activityPreName)
        }
    }

    private fun generateActivity(packageName: String, activityPreName: String) {
        val className = activityPreName + "Activity"
        mActivities.add("$packageName.$className")

        // 保存当前类里面的所有方法名，防止重名

        val methods = hashSetOf<String>()
        methods.add("onCreate")

        // 需要排除 activity 自带的方法名
        fun nextMethod(): String {
            while (true) {
                val name = generateMethodName()
                if (methods.add(name)) {
                    return name
                }

                logger.info { "nextMethod exclude：$name" }
            }
        }

        val layoutName = resPrefix + "layout_" + activityPreName.lowercase()
        mLayoutIds.add(layoutName)

        val selfType = getTypedName(packageName, className)

        val strRes = resPrefix + generateResName()
        if (Random.nextDouble() < STRING_PROBABILITY) {
            mStringIds.add(strRes)
        }

        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES or ClassWriter.COMPUTE_MAXS)
        cw.visit(Opcodes.V1_6, Opcodes.ACC_PUBLIC, selfType, null, "android/app/Activity", null)

        val ccm = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
        ccm.visitVarInsn(Opcodes.ALOAD, 0)
        ccm.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/app/Activity", "<init>", "()V", false)
        ccm.visitInsn(Opcodes.RETURN)
        ccm.visitMaxs(1, 1)
        ccm.visitEnd()

        // 生成无关类
        repeat(Random.nextInt(1, 3)) {
            val name = generateClassName(packageName)
            val (_, m) = generateOtherClass(packageName, name)

            val that = getTypedName(packageName, name)
            val fieldName = name.lowercase()

            val descriptor = "L$that;"

            cw.visitField(Opcodes.ACC_PRIVATE, fieldName, descriptor, null, null).visitEnd()

            val method = nextMethod()
            val mv = cw.visitMethod(Opcodes.ACC_PUBLIC, method, "()V", null, null)
            mv.visitCode()

            // 初始化 & 随便调用一个方法
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitTypeInsn(Opcodes.NEW, that)
            mv.visitInsn(Opcodes.DUP)
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, that, "<init>", "()V", false)

            mv.visitFieldInsn(Opcodes.PUTFIELD, selfType, fieldName, descriptor)

            val mSize = m.size
            val callCnt = if (mSize <= 1) max(0, mSize) else Random.nextInt(1, mSize)

            repeat(callCnt) {
                mv.visitVarInsn(Opcodes.ALOAD, 0)
                mv.visitFieldInsn(Opcodes.GETFIELD, selfType, fieldName, descriptor)
                mv.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    that,
                    m[it],
                    "()Ljava/lang/String;",
                    false
                )
                mv.visitInsn(Opcodes.POP)
            }

            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()

        }

        val otherClassName = generateClassName(packageName)
        val (otherFields, otherMethods) = generateOtherClass(packageName, otherClassName)

        // 生成onCreate 方法
        val mv =
            cw.visitMethod(Opcodes.ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(
            Opcodes.INVOKESPECIAL,
            "android/app/Activity",
            "onCreate",
            "(Landroid/os/Bundle;)V",
            false
        )

        // new 一个对象，并给其字段赋值
        val otherType = getTypedName(packageName, otherClassName)
        mv.visitTypeInsn(Opcodes.NEW, otherType)
        mv.visitInsn(Opcodes.DUP)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, otherType, "<init>", "()V", false)

        mv.visitVarInsn(Opcodes.ASTORE, 2)
        mv.visitVarInsn(Opcodes.ALOAD, 2)

        otherFields.forEach { field ->
            mv.visitVarInsn(Opcodes.ALOAD, 2)
            mv.visitLdcInsn(generateBigValue())
            mv.visitFieldInsn(Opcodes.PUTFIELD, otherType, field, "Ljava/lang/String;")
        }

        val callCnt = if (otherMethods.isEmpty()) 0 else Random.nextInt(otherMethods.size)
        if (callCnt > 0) otherMethods.shuffled()

        val otherOwner = getTypedName(packageName, otherClassName)

        repeat(callCnt) {
            val m = otherMethods[it]
            mv.visitVarInsn(Opcodes.ALOAD, 2)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, otherOwner, m, "()Ljava/lang/String;", false)
            mv.visitInsn(Opcodes.POP)
        }

        val viewIds = generateLayout(layoutName)
        mIds.addAll(viewIds)

        // setContentView
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitFieldInsn(Opcodes.GETSTATIC, "$mRClassType\$layout", layoutName, "I")
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, selfType, "setContentView", "(I)V", false)

        // 初始化view
        val initViews = viewIds.mapIndexed { index, viewId ->

            val listener = "${className}${
                viewId.take(1).uppercase()
            }${viewId.substring(1)}OnClickListener"

            // 使用 a - z  aa-zz的方法命名
            val size = CHARACTER.size
            val name = if (index < size) {
                CHARACTER[index].toString()
            } else if (index < size * size) {
                val first = index / (size * size)
                val second = index % (size * size)
                "${CHARACTER[first]}${CHARACTER[second]}"
            } else {
                nextMethod()
            }

            // 处理点击事件
            val cwi = ClassWriter(ClassWriter.COMPUTE_FRAMES)
            cwi.visit(
                Opcodes.V1_6,
                Opcodes.ACC_MODULE,
                getTypedName(packageName, listener),
                null,
                "java/lang/Object",
                arrayOf("android/view/View\$OnClickListener")
            )

            cwi.visitField(
                Opcodes.ACC_MODULE, generateResName(), "Landroid/view/View;", null, null
            ).visitEnd()

            val cc = cwi.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null)
            cc.visitVarInsn(Opcodes.ALOAD, 0)
            cc.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/Object", "<init>", "()V", false)
            cc.visitInsn(Opcodes.RETURN)
            cc.visitMaxs(1, 1)
            cc.visitEnd()

            val onClick =
                cwi.visitMethod(Opcodes.ACC_PUBLIC, "onClick", "(Landroid/view/View;)V", null, null)
            onClick.visitCode()

            onClick.visitIntInsn(Opcodes.ALOAD, 1)

            when (Random.nextInt(3)) {
                0 -> onClick.visitInsn(Opcodes.ICONST_0)
                1 -> onClick.visitInsn(Opcodes.ICONST_4)
                else -> onClick.visitIntInsn(Opcodes.BIPUSH, 8)
            }

            onClick.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "android/view/View",
                "setVisibility",
                "(I)V",
                true
            )
            onClick.visitInsn(Opcodes.RETURN)
            onClick.visitMaxs(1, 1)
            onClick.visitEnd()

            cwi.visitEnd()

            val bytes = cwi.toByteArray()
            writeClassToFile(packageName, listener, bytes)

            return@mapIndexed name
        }

        initViews.forEachIndexed { index, name ->
            val viewId = viewIds[index]

            val listener = "${className}${
                viewId.take(1).uppercase()
            }${viewId.substring(1)}OnClickListener"
            val type = getTypedName(packageName, listener)

            // 一个方法初始化一个view 方便生成
            val method = cw.visitMethod(Opcodes.ACC_PRIVATE, name, "()V", null, null)
            method.visitVarInsn(Opcodes.ALOAD, 0)
//            // R.id.xx
            method.visitFieldInsn(Opcodes.GETSTATIC, "$mRClassType\$id", viewId, "I")

            method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                selfType,
                "findViewById",
                "(I)Landroid/view/View;",
                false
            )
            method.visitTypeInsn(Opcodes.NEW, type)
            method.visitInsn(Opcodes.DUP)

            method.visitMethodInsn(
                Opcodes.INVOKESPECIAL, type, "<init>", "()V", false
            )
//            // invokevirtual android/view/View setOnClickListener (Landroid/view/View$OnClickListener;)V
            method.visitMethodInsn(
                Opcodes.INVOKEVIRTUAL,
                "android/view/View",
                "setOnClickListener",
                "(Landroid/view/View\$OnClickListener;)V",
                false
            )

            method.visitInsn(Opcodes.RETURN)
            method.visitMaxs(1, 1)
            method.visitEnd()
        }

        // 生成其他方法
        val cnt = Random.nextInt(2, 15)
        val others = (0 until cnt).map {
            return@map nextMethod()
        }

        // 创建方法体
        others.forEachIndexed { index, s ->
            val method = cw.visitMethod(Opcodes.ACC_PRIVATE, s, "()V", null, null)
            method.visitVarInsn(Opcodes.ALOAD, 0)

            // 防止死循环
            val fn = Random.nextInt(index, others.size)
            if (fn == index) {
                // 弹Toast
                method.visitLdcInsn(generateBigValue())
                method.visitInsn(Opcodes.ICONST_0)
                method.visitMethodInsn(
                    Opcodes.INVOKESTATIC,
                    "android/widget/Toast",
                    "makeText",
                    "(Landroid/content/Context;Ljava/lang/CharSequence;I)Landroid/widget/Toast;",
                    false
                )

                method.visitMethodInsn(
                    Opcodes.INVOKEVIRTUAL,
                    "android/widget/Toast",
                    "show",
                    "()V",
                    false
                )
                method.visitInsn(Opcodes.RETURN)
            } else {
                val get = others[fn]
                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, selfType, get, "()V", false)
            }

            method.visitInsn(Opcodes.RETURN)
            method.visitMaxs(1, 1)
            method.visitEnd()
        }

        val call = initViews + others.subList(0, Random.nextInt(max(1, others.size / 3)) + 1)

        call.shuffled().forEach { name ->
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, selfType, name, "()V", false)
        }

        mv.visitInsn(Opcodes.RETURN)
        mv.visitMaxs(1, 1)
        mv.visitEnd()

        // 随机生成其他生命周期方法，增加内部逻辑多样性
        val lifecycleMethods = listOf("onStart", "onResume", "onPause", "onStop", "onDestroy")
        
        lifecycleMethods.forEach { methodName ->
            // onResume 强制生成，其他看概率
            if (Random.nextDouble() < LIFECYCLE_PROBABILITY || methodName == "onResume") {
                val methodMv = cw.visitMethod(Opcodes.ACC_PROTECTED, methodName, "()V", null, null)
                methodMv.visitCode()
                methodMv.visitVarInsn(Opcodes.ALOAD, 0)
                methodMv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/app/Activity", methodName, "()V", false)
                
                // 注入随机字节码逻辑
                val snippetsCount = Random.nextInt(1, 4)
                repeat(snippetsCount) {
                    injectRandomBytecode(methodMv, className)
                }

                // 如果是 onResume，则额外注入资源引用
                if (methodName == "onResume") {
                    if (Random.nextDouble() < ASSET_PROBABILITY) {
                        val assetFileName = resPrefix + generateResName() + ".txt"
                        generateAsset(assetFileName)
                        
                        val l0 = Label()
                        val l1 = Label()
                        val l2 = Label()
                        methodMv.visitTryCatchBlock(l0, l1, l2, "java/lang/Exception")
                        methodMv.visitLabel(l0)
                        methodMv.visitVarInsn(Opcodes.ALOAD, 0)
                        methodMv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getAssets", "()Landroid/content/res/AssetManager;", false)
                        methodMv.visitLdcInsn(assetFileName)
                        methodMv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/res/AssetManager", "open", "(Ljava/lang/String;)Ljava/io/InputStream;", false)
                        methodMv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/io/InputStream", "close", "()V", false)
                        methodMv.visitLabel(l1)
                        val l3 = Label()
                        methodMv.visitJumpInsn(Opcodes.GOTO, l3)
                        methodMv.visitLabel(l2)
                        methodMv.visitVarInsn(Opcodes.ASTORE, 1)
                        methodMv.visitLabel(l3)
                    }

                    if (Random.nextDouble() < ANIM_PROBABILITY) {
                        val animName = resPrefix + generateResName()
                        if (mAnimIds.add(animName)) {
                            generateAnim(animName)
                            methodMv.visitVarInsn(Opcodes.ALOAD, 0)
                            methodMv.visitFieldInsn(Opcodes.GETSTATIC, "$mRClassType\$anim", animName, "I")
                            methodMv.visitMethodInsn(Opcodes.INVOKESTATIC, "android/view/animation/AnimationUtils", "loadAnimation", "(Landroid/content/Context;I)Landroid/view/animation/Animation;", false)
                            methodMv.visitInsn(Opcodes.POP)
                        }
                    }

                    if (Random.nextDouble() < MIPMAP_PROBABILITY) {
                        val mipmapName = resPrefix + generateResName()
                        if (mMipmapIds.add(mipmapName)) {
                            generateMipmap(mipmapName)
                            methodMv.visitVarInsn(Opcodes.ALOAD, 0)
                            methodMv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/app/Activity", "getResources", "()Landroid/content/res/Resources;", false)
                            methodMv.visitFieldInsn(Opcodes.GETSTATIC, "$mRClassType\$mipmap", mipmapName, "I")
                            methodMv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/content/res/Resources", "getDrawable", "(I)Landroid/graphics/drawable/Drawable;", false)
                            methodMv.visitInsn(Opcodes.POP)
                        }
                    }
                }
                
                methodMv.visitInsn(Opcodes.RETURN)
                methodMv.visitMaxs(5, 3)
                methodMv.visitEnd()
            }
        }

        cw.visitEnd()

        val bytes = cw.toByteArray()

        writeClassToFile(packageName, className, bytes)
    }

    private fun injectRandomBytecode(mv: org.objectweb.asm.MethodVisitor, className: String) {
        AndroidJunkBytecodeInject.injectRandomBytecode(mv, className, ::generateResName, ::generateBigValue)
    }

    private fun generateLayout(layoutName: String): List<String> {
        val drawableName = resPrefix + generateResName()
        if (Random.nextDouble() < DRAWABLE_PROBABILITY && mDrawableIds.add(drawableName)) {
            generateDrawable(drawableName)
        }

        val ids = mutableSetOf<String>()
        val rnd = Random.nextInt(2, 18)
        val rootGroup = VIEW_GROUPS[Random.nextInt(VIEW_GROUPS.size)]
        val isLinear = rootGroup == "LinearLayout"

        val root = """<?xml version="1.0" encoding="utf-8"?>
            <$rootGroup xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                ${if (isLinear) "android:orientation=\"vertical\"" else ""}>
                
        """.trimIndent()

        val xml = StringBuilder(root)

        val dimens = arrayOf("match_parent", "wrap_content", "dp")

        fun getDimens(): String {
            val d = dimens[Random.nextInt(dimens.size)]
            val prefix = if (d == "dp") Random.nextInt(100).toString() else ""
            return prefix + d
        }

        fun linear() =
            if (Random.nextBoolean()) "android:orientation=\"vertical\"" else "android:orientation=\"horizontal\""

        (0 until rnd).forEach { _ ->
            val viewId = generateResName()
            val hasId = if (Random.nextDouble() < ID_PROBABILITY) {
                ids.add(viewId)
            } else {
                false
            }

            val widget = VIEWS[Random.nextInt(VIEWS.size)]
            val extraAttrs = StringBuilder()
            var hasBackground = false

            if (Random.nextDouble() < MIPMAP_PROBABILITY) {
                val mipmapName = resPrefix + generateResName()
                if (mMipmapIds.add(mipmapName)) {
                    generateMipmap(mipmapName)
                    extraAttrs.append("\n                    android:background=\"@mipmap/$mipmapName\"")
                    hasBackground = true
                }
            }

            if (Random.nextDouble() < ANIM_PROBABILITY) {
                val animName = resPrefix + generateResName()
                if (mAnimIds.add(animName)) {
                    generateAnim(animName)
                    extraAttrs.append("\n                    android:layoutAnimation=\"@anim/$animName\"")
                }
            }

            if (Random.nextDouble() < DRAWABLE_PROBABILITY) {
                val dName = resPrefix + generateResName()
                if (mDrawableIds.add(dName)) {
                    generateDrawable(dName)
                    if (widget.contains("Image")) {
                        extraAttrs.append("\n                    android:src=\"@drawable/$dName\"")
                    } else if (!hasBackground) {
                        extraAttrs.append("\n                    android:background=\"@drawable/$dName\"")
                        hasBackground = true
                    }
                }
            }

            if (Random.nextDouble() < STRING_PROBABILITY) {
                val strRes = resPrefix + generateResName()
                mStringIds.add(strRes)
                if (widget.contains("Text") || widget.contains("Button")) {
                    extraAttrs.append("\n                    android:text=\"@string/$strRes\"")
                }
            }

            val tpl = """
                <$widget
                    ${if (hasId) "android:id=\"@+id/$viewId\"" else ""}   
                    android:layout_width="${getDimens()}"
                    ${if (widget == "LinearLayout") linear() else ""}
                    android:layout_height="${getDimens()}"$extraAttrs />
            """.trimIndent()

            xml.append(tpl).append("\n")
        }

        xml.append("</$rootGroup>")

        val file = File(workspace, "res/layout/$layoutName.xml")
        writeStringToFile(file, xml.toString())

        return ids.toList()
    }

    private fun generateOtherResources() {
        val count = Random.nextInt(packageCount * 5, packageCount * 15)
        IntStream.range(0, count).parallel().forEach { _ ->
            if (Random.nextDouble() < ANIM_PROBABILITY) {
                val animName = resPrefix + generateResName()
                if (mAnimIds.add(animName)) {
                    generateAnim(animName)
                }
            }
            if (Random.nextDouble() < MIPMAP_PROBABILITY) {
                val mipmapName = resPrefix + generateResName()
                if (mMipmapIds.add(mipmapName)) {
                    generateMipmap(mipmapName)
                }
            }
            if (Random.nextDouble() < ASSET_PROBABILITY) {
                generateAsset()
            }
            if (Random.nextDouble() < DRAWABLE_PROBABILITY) {
                val drawableName = resPrefix + generateResName()
                if (mDrawableIds.add(drawableName)) {
                    generateDrawable(drawableName)
                }
            }
        }
    }

    private fun generateAnim(animName: String) {
        val animTypes = arrayOf("alpha", "scale", "translate", "rotate", "set")
        val root = animTypes[Random.nextInt(animTypes.size)]
        
        val content = java.lang.StringBuilder("""<?xml version="1.0" encoding="utf-8"?>""")
        content.append("\n<").append(root).append(" xmlns:android=\"").append(ANDROID_SCHEMA).append("\"")
        
        fun generateAnimAttributes(): String {
            val attrs = java.lang.StringBuilder()
            attrs.append(" android:duration=\"").append(Random.nextInt(300, 2000)).append("\"")
            if (Random.nextBoolean()) attrs.append(" android:fillAfter=\"").append(Random.nextBoolean()).append("\"")
            if (Random.nextBoolean()) attrs.append(" android:repeatCount=\"").append(Random.nextInt(1, 10)).append("\"")
            return attrs.toString()
        }

        fun generateChildAnim(type: String): String {
            val child = java.lang.StringBuilder("\n    <").append(type).append(generateAnimAttributes())
            when (type) {
                "alpha" -> {
                    child.append(" android:fromAlpha=\"").append(Random.nextDouble().toFloat()).append("\"")
                    child.append(" android:toAlpha=\"").append(Random.nextDouble().toFloat()).append("\"")
                }
                "scale" -> {
                    child.append(" android:fromXScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    child.append(" android:toXScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    child.append(" android:fromYScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    child.append(" android:toYScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    child.append(" android:pivotX=\"").append(Random.nextInt(0, 100)).append("%\"")
                    child.append(" android:pivotY=\"").append(Random.nextInt(0, 100)).append("%\"")
                }
                "translate" -> {
                    child.append(" android:fromXDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                    child.append(" android:toXDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                    child.append(" android:fromYDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                    child.append(" android:toYDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                }
                "rotate" -> {
                    child.append(" android:fromDegrees=\"").append(Random.nextInt(0, 360)).append("\"")
                    child.append(" android:toDegrees=\"").append(Random.nextInt(0, 360)).append("\"")
                    child.append(" android:pivotX=\"").append(Random.nextInt(0, 100)).append("%\"")
                    child.append(" android:pivotY=\"").append(Random.nextInt(0, 100)).append("%\"")
                }
            }
            child.append(" />")
            return child.toString()
        }

        if (root == "set") {
            content.append(generateAnimAttributes()).append(">\n")
            val childCount = Random.nextInt(1, 4)
            for (i in 0 until childCount) {
                val childType = animTypes[Random.nextInt(animTypes.size - 1)]
                content.append(generateChildAnim(childType))
            }
            content.append("\n</").append(root).append(">\n")
        } else {
            content.append(generateAnimAttributes())
            when (root) {
                "alpha" -> {
                    content.append(" android:fromAlpha=\"").append(Random.nextDouble().toFloat()).append("\"")
                    content.append(" android:toAlpha=\"").append(Random.nextDouble().toFloat()).append("\"")
                }
                "scale" -> {
                    content.append(" android:fromXScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    content.append(" android:toXScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    content.append(" android:fromYScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    content.append(" android:toYScale=\"").append(Random.nextDouble().toFloat()).append("\"")
                    content.append(" android:pivotX=\"").append(Random.nextInt(0, 100)).append("%\"")
                    content.append(" android:pivotY=\"").append(Random.nextInt(0, 100)).append("%\"")
                }
                "translate" -> {
                    content.append(" android:fromXDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                    content.append(" android:toXDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                    content.append(" android:fromYDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                    content.append(" android:toYDelta=\"").append(Random.nextInt(-100, 100)).append("%\"")
                }
                "rotate" -> {
                    content.append(" android:fromDegrees=\"").append(Random.nextInt(0, 360)).append("\"")
                    content.append(" android:toDegrees=\"").append(Random.nextInt(0, 360)).append("\"")
                    content.append(" android:pivotX=\"").append(Random.nextInt(0, 100)).append("%\"")
                    content.append(" android:pivotY=\"").append(Random.nextInt(0, 100)).append("%\"")
                }
            }
            content.append(" />\n")
        }

        val file = File(workspace, "res/anim/$animName.xml")
        writeStringToFile(file, content.toString())
    }

    private fun generateAsset(fileName: String? = null) {
        val assetTypes = arrayOf(".xml", ".yaml", ".json", ".config", ".txt")
        val ext = assetTypes[Random.nextInt(assetTypes.size)]
        val name = fileName ?: (resPrefix + generateResName() + ext)
        
        val content = java.lang.StringBuilder()
        when (ext) {
            ".json" -> {
                content.append("{\n")
                val keyCount = Random.nextInt(5, 20)
                for (i in 0 until keyCount) {
                    content.append("  \"").append(generateResName()).append("\": \"").append(generateBigValue(20)).append("\"")
                    if (i < keyCount - 1) content.append(",\n") else content.append("\n")
                }
                content.append("}")
            }
            ".xml" -> {
                content.append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<root>\n")
                val keyCount = Random.nextInt(5, 20)
                for (i in 0 until keyCount) {
                    val node = generateResName()
                    content.append("  <").append(node).append(">").append(generateBigValue(20)).append("</").append(node).append(">\n")
                }
                content.append("</root>")
            }
            ".yaml", ".config" -> {
                val keyCount = Random.nextInt(5, 20)
                for (i in 0 until keyCount) {
                    content.append(generateResName()).append(": ").append(generateBigValue(20)).append("\n")
                }
            }
            else -> {
                val lineCount = Random.nextInt(10, 50)
                for (i in 0 until lineCount) {
                    content.append(generateBigValue()).append("\n")
                }
            }
        }
        
        val file = File(workspace, "assets/$name")
        writeStringToFile(file, content.toString())
    }

    private fun generateVectorContent(): String {
        val width = Random.nextInt(24, 200)
        val height = Random.nextInt(24, 200)
        val content = java.lang.StringBuilder(
            """<vector xmlns:android="$ANDROID_SCHEMA"
                    android:width="${width}dp"
                    android:height="${height}dp"
                    android:viewportWidth="${width}"
                    android:viewportHeight="${height}">"""
        )
        val pathCount = Random.nextInt(1, 5)
        for (p in 0 until pathCount) {
            content.append("\n    <path android:fillColor=\"${generateColor()}\"")
            if (Random.nextBoolean()) {
                content.append(" android:strokeColor=\"${generateColor()}\" android:strokeWidth=\"${Random.nextInt(1, 5)}\"")
            }
            content.append(" android:pathData=\"M")
            val pointCount = Random.nextInt(5, 20)
            for (i in 0 until pointCount) {
                if (Random.nextBoolean()) {
                    content.append(" ").append(Random.nextInt(width)).append(",").append(Random.nextInt(height))
                } else {
                    content.append(" C ").append(Random.nextInt(width)).append(" ").append(Random.nextInt(height))
                        .append(", ").append(Random.nextInt(width)).append(" ").append(Random.nextInt(height))
                        .append(", ").append(Random.nextInt(width)).append(" ").append(Random.nextInt(height))
                }
                if (i != pointCount - 1) {
                    content.append(" L ")
                }
            }
            content.append(" Z\" />")
        }
        content.append("\n</vector>\n")
        return content.toString()
    }

    private fun generateMipmap(mipmapName: String) {
        val dirName = MIPMAP_DIRS[Random.nextInt(MIPMAP_DIRS.size)]
        val file = File(workspace, "res/$dirName/$mipmapName.xml")
        writeStringToFile(file, generateVectorContent())
    }

    private fun generateDrawable(drawableName: String) {
        val dirName = DRAWABLE_DIRS[Random.nextInt(DRAWABLE_DIRS.size)]
        val drawableFile = File(workspace, "res/$dirName/$drawableName.xml")
        writeStringToFile(drawableFile, generateVectorContent())
    }

    private fun generateOtherClass(
        packageName: String,
        className: String
    ): Pair<Set<String>, List<String>> {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val owner = getTypedName(packageName, className)

        val acc = if (className.hashCode() and 1 == 0) Opcodes.ACC_PUBLIC else Opcodes.ACC_MODULE

        cw.visit(Opcodes.V1_6, acc, owner, null, "java/lang/Object", null)


        val fields = HashSet<String>(16)

        val cnt = Random.nextInt(2, 16)

        repeat(cnt) {
            var field: String
            do {
                field = generateFieldName()
            } while (!fields.add(field))
            cw.visitField(Opcodes.ACC_MODULE, field, "Ljava/lang/String;", null, null).visitEnd()
        }

        val descriptor = arrayOf(
            "()V", "()Ljava/lang/String;", "()I"
        )

        // 生成随机方法
        val methods = HashSet<String>(16)

        // 静态方法 0-3
        val sMethodCnt = Random.nextInt(3)
        repeat(sMethodCnt) {
            var name: String
            do {
                name = generateMethodName()
            } while (!methods.add(name))

            val index = Random.nextInt(descriptor.size)
            val des = descriptor[index]

            val method = cw.visitMethod(
                Opcodes.ACC_PUBLIC + Opcodes.ACC_STATIC, name, des, null, null
            )

            method.visitCode()
            when (index) {
                0 -> {
                    // 调用log日志
                    method.visitLdcInsn(className)
                    method.visitLdcInsn(name)
                    method.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "android/util/Log",
                        "d",
                        "(Ljava/lang/String;Ljava/lang/String;)I",
                        false
                    )
                    method.visitInsn(Opcodes.POP)
                    method.visitInsn(Opcodes.RETURN)
                }

                1 -> {
                    // 返回一个随机字符串
                    method.visitLdcInsn(generateBigValue())
                    method.visitInsn(Opcodes.ARETURN)
                }

                2 -> {
                    // 返回一个 hash
                    method.visitLdcInsn(generateBigValue())
                    method.visitMethodInsn(
                        Opcodes.INVOKEVIRTUAL,
                        "java/lang/String",
                        "hashCode",
                        "()I",
                        false
                    )
                    method.visitInsn(Opcodes.IRETURN)
                }

                else -> {
                    method.visitInsn(Opcodes.RETURN)
                }
            }

            method.visitMaxs(1, 1)
            method.visitEnd()
        }

        val listFields = fields.toList()
        listFields.shuffled()

        val size = listFields.size

        val getMethods = arrayListOf<String>()

        // 普通方法 0-5
        val methodCnt = Random.nextInt(0, min(size, 5))
        repeat(methodCnt) {
            val field = listFields[it]
            val name = "get" + field.take(1).uppercase() + field.substring(1)

            getMethods.add(name)

            val mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC, name, "()Ljava/lang/String;", null, null
            )

            mv.visitCode()
            mv.visitVarInsn(Opcodes.ALOAD, 0)
            mv.visitFieldInsn(Opcodes.GETFIELD, owner, field, "Ljava/lang/String;")

            mv.visitInsn(Opcodes.ARETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()
        }


        cw.visitEnd()

        val bytes = cw.toByteArray()

        writeClassToFile(packageName, className, bytes)

        return (fields to getMethods)
    }

    private fun generatePackageName(): String {
        val len = Random.nextInt(3, 10)

        val chars = CharArray(len)

        for (i in 0 until len) {
            chars[i] = CHARACTER[Random.nextInt(CHARACTER.size)]
        }

        val name = chars.concatToString()
        // 排除关键字
        if (KEYWORDS.contains(name) || XML_KEYWORDS.contains(name)) {
            logger.info { "generatePackageName 排除关键字 exclude：$name" }
            return generatePackageName()
        }

        return name
    }

    private fun generateClassName(packageName: String): String {
        val len = Random.nextInt(4, 12)
        val chars = CharArray(len)
        for (i in 0 until len) {
            chars[i] = CHARACTER[Random.nextInt(CHARACTER.size)]
        }

        chars[0] = chars[0].uppercaseChar()

        val name = chars.concatToString()
        // 排除关键字和已经存在的名字
        if (KEYWORDS.contains(name) || XML_KEYWORDS.contains(name) || !mCheckActivityNames.add(name) || !mCheckClassName.add(
                "$packageName.$name"
            )
        ) {
            logger.info { "generateClassName 排除关键字和已经存在的名字 exclude：$packageName.$name" }
            return generateClassName(packageName)
        }

        return name
    }

    private fun generateResName(): String {
        return generatePackageName()
    }

    private fun generateFieldName(): String {
        return generatePackageName()
    }

    private fun generateMethodName(): String {
        return generatePackageName()
    }

    private fun generateColor(): String {
        val sb = java.lang.StringBuilder(7)
        sb.append("#")
        (0..5).forEach { _ ->
            sb.append(COLORS[Random.nextInt(COLORS.size)])
        }
        return sb.toString()
    }

    private val BIG_VALUE_CHARS = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKMNLOPQRSTUVWZYZ0123456789".toCharArray()

    private fun generateBigValue(length: Int = Random.nextInt(10, 100)): String {
        val sb = java.lang.StringBuilder(length)
        (0 until length).forEach { _ ->
            sb.append(BIG_VALUE_CHARS[Random.nextInt(BIG_VALUE_CHARS.size)])
        }
        return sb.toString()
    }

    private fun getTypedName(packageName: String?, className: String): String {
        val fullName = if (packageName.isNullOrEmpty()) className else "$packageName/$className"
        return fullName.replace(".", "/")
    }


    private fun generateManifest() {
        val manifestFile = File(workspace, "AndroidManifest.xml")
        if (!manifestFile.parentFile.exists()) {
            manifestFile.parentFile.mkdirs()
        }

        manifestFile.bufferedWriter().use { writer ->
            writer.write("""<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools" package="$appPackageName"> 
        <application>
        
        """.trimIndent())
            
            mActivities.forEach { activity ->
                writer.write("<activity android:name=\"$activity\" />\n")
            }
            
            writer.write("""
       </application>
    </manifest>
        """.trimIndent())
        }
    }

    private fun generateStringsFile() {
        val res = File(workspace, "res/values/strings.xml")
        if (!res.parentFile.exists()) {
            res.parentFile.mkdirs()
        }

        res.bufferedWriter().use { writer ->
            writer.write("""<?xml version="1.0" encoding="utf-8"?>
            <resources>
                """.trimIndent())
            writer.newLine()
            mStringIds.forEach { 
                writer.write("<string name=\"$it\">${generateBigValue()}</string>\n")
            }
            writer.write("            </resources>")
        }
    }

    private fun generateKeepProguard() {

        // 生成混淆保持文件
        val proguard = "-keep class ${appPackageName}.**{*;}"
        writeStringToFile(File(workspace, "consumer-rules.pro"), proguard)

        val prefix: String = resPrefix

        if (prefix.isEmpty()) {
            return
        }

        val keep = "@layout/$prefix*,@drawable/$prefix*,@string/$prefix*,@anim/$prefix*,@mipmap/$prefix*"

        val content = """<?xml version="1.0" encoding="utf-8"?>
        <resources xmlns:tools="http://schemas.android.com/tools"
        tools:keep="$keep"
        tools:shrinkMode="strict"/>""".trimIndent()
        val rnd = generateMethodName()
        writeStringToFile(File(workspace, "res/raw/" + prefix + rnd + "_keep.xml"), content)
    }

    private fun writeRFile() {
        val file = File(workspace, "R.txt")
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        file.bufferedWriter().use { writer ->
            mStringIds.forEach { writer.write("int string $it 0x0\n") }
            writer.newLine()
            mLayoutIds.forEach { writer.write("int layout $it 0x0\n") }
            writer.newLine()
            mDrawableIds.forEach { writer.write("int drawable $it 0x0\n") }
            writer.newLine()
            mAnimIds.forEach { writer.write("int anim $it 0x0\n") }
            writer.newLine()
            mMipmapIds.forEach { writer.write("int mipmap $it 0x0\n") }
            writer.newLine()
            mIds.forEach { writer.write("int id $it 0x0\n") }
        }

        logger.info { "strings 文件数量: ${mStringIds.size}" }
        logger.info { "layouts 文件数量: ${mLayoutIds.size}" }
        logger.info { "drawables 文件数量: ${mDrawableIds.size}" }
        logger.info { "anims 文件数量: ${mAnimIds.size}" }
        logger.info { "mipmaps 文件数量: ${mMipmapIds.size}" }
        logger.info { "ids 大小: ${mIds.size}" }
    }

    private fun assembleAar(): File {
        // 将 class 打包成jar
        val classJar = File(workspace, "classes.jar")
        val dir = File(workspace, classesDir)

        classJar.outputStream().buffered().use { fos ->
            val jos = JarOutputStream(fos)

            dir.listFiles()?.forEach { file ->
                addFileToJar(file, "", jos)
            }

            jos.finish()
        }

        // 删除 class 目录
        // dir.deleteRecursively()

        // 打包aar
        val out = File(output, "junk_" + appPackageName.replace(".", "_") + "_TT2.1.0.aar")
        val parent = out.parentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }

        out.outputStream().buffered().use { fos ->
            val zos = ZipOutputStream(fos)
            workspace.listFiles { _, name -> name != classesDir }
                ?.forEach { file -> addFileToZip(file, "", zos) }

            zos.finish()
        }

        return out
    }

    private fun addFileToJar(file: File, node: String, jos: JarOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { f ->
                addFileToJar(f, node + file.name + "/", jos)
            }
        } else {
            val entry = JarEntry(node + file.name)
            copyEntry(entry, file, jos)
        }
    }

    private fun addFileToZip(file: File, node: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            file.listFiles()?.forEach { f ->
                addFileToZip(f, node + file.name + "/", zos)
            }
        } else {
            val entry = ZipEntry(node + file.name)
            copyEntry(entry, file, zos)
        }
    }

    private fun copyEntry(entry: ZipEntry, file: File, zos: ZipOutputStream) {
        zos.putNextEntry(entry)
        file.inputStream().use { fis ->
            fis.copyTo(zos)
        }
        zos.closeEntry()
    }

    private fun writeStringToFile(file: File, content: String): Boolean {
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        return runCatching {
            file.writeText(content)
        }.isSuccess
    }

    private fun writeClassToFile(packageName: String, className: String, bytes: ByteArray) {
        val dir = File(workspace, classesDir)
        val parent = File(dir, packageName.replace(".", "/"))
        val file = File(parent, "$className.class")

        if (!parent.exists()) {
            parent.mkdirs()
        }

        file.writeBytes(bytes)
    }
}
