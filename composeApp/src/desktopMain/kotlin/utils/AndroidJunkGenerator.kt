package utils

import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import java.io.File
import java.io.FileWriter
import java.text.DecimalFormat
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
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

private const val ANDROID_SCHEMA = "http://schemas.android.com/apk/res/android"

private val KEYWORDS = arrayOf( /*基本数据类型*/
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

private val XML_KEYWORDS = arrayOf("null")

private val CHARACTER = "abcdefghijklmnopqrstuvwxyz".toCharArray()

private val COLORS = "0123456789abcdef".toCharArray()

private val VIEWS = arrayOf(
    "FrameLayout",
    "LinearLayout",
    "RelativeLayout",
    "GridLayout",
    "Chronometer",
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

    private val mCheckActivityNames = HashSet<String>(1024)
    private val mCheckClassName = mutableSetOf<String>()

    private val mDrawableIds = HashSet<String>(4098)
    private val mLayoutIds = HashSet<String>(max(packageCount * activityCountPerPackage, 1024))
    private val mStringIds = HashSet<String>(4098)
    private val mIds = HashSet<String>(4098)

    private val mActivities = HashSet<String>(max(packageCount * activityCountPerPackage, 1024))

    private val mRClassType = getTypedName(appPackageName, "R")


    fun startGenerate(): File {
        // 清理原工作目录中的文件
        println("正在清理工作空间...")

        workspace.deleteRecursively()
        println("工作空间已就绪...")

        val start = System.nanoTime()
        generateClasses()
        generateManifest()
        println("class 文件生成完成...")

        generateStringsFile()
        generateKeepProguard()

        writeRFile()
        println("资源文件生成完成，开始打包...")

        // 正在打包
        val outPath = assembleAar()
        println("打包完成：\n\r\t$outPath \n\r\t${fileSize(outPath.length())}")

        val end = System.nanoTime()

        val timeMills = (end - start) / 1_000_000
        val s = timeMills / 1000
        val ms = timeMills % 1000

        println("用时：${s}.${ms} 秒")

        workspace.deleteRecursively()

        return outPath
    }

    private fun generateClasses() {
        for (i in 0 until packageCount) {
            val packageName = generatePackageName()
            // 生成Activity
            for (j in 0 until activityCountPerPackage) {
                val packageName1 = "$appPackageName.$packageName"
                val activityName = generateClassName(packageName1)
                generateActivity(packageName1, activityName)
            }
        }

        val rootClassCount: Int = Random.nextInt(activityCountPerPackage) + (activityCountPerPackage shr 1)

        for (j in 0 until rootClassCount) {
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

                println("nextMethod exclude：$name")
            }
        }

        val layoutName = resPrefix + "layout_" + activityPreName.lowercase()
        mLayoutIds.add(layoutName)

        val selfType = getTypedName(packageName, className)

        val strRes = resPrefix + generateResName()
        mStringIds.add(strRes)

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
                mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, that, m[it], "()Ljava/lang/String;", false)
                mv.visitInsn(Opcodes.POP)
            }

            mv.visitInsn(Opcodes.RETURN)
            mv.visitMaxs(1, 1)
            mv.visitEnd()

        }

        val otherClassName = generateClassName(packageName)
        val (otherFields, otherMethods) = generateOtherClass(packageName, otherClassName)

        // 生成onCreate 方法
        val mv = cw.visitMethod(Opcodes.ACC_PROTECTED, "onCreate", "(Landroid/os/Bundle;)V", null, null)
        mv.visitCode()
        mv.visitVarInsn(Opcodes.ALOAD, 0)
        mv.visitVarInsn(Opcodes.ALOAD, 1)
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "android/app/Activity", "onCreate", "(Landroid/os/Bundle;)V", false)

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

            val listener = "${className}${viewId.substring(0, 1).uppercase()}${viewId.substring(1)}OnClickListener"

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

            val onClick = cwi.visitMethod(Opcodes.ACC_PUBLIC, "onClick", "(Landroid/view/View;)V", null, null)
            onClick.visitCode()

            onClick.visitIntInsn(Opcodes.ALOAD, 1)

            when (Random.nextInt(3)) {
                0 -> onClick.visitInsn(Opcodes.ICONST_0)
                1 -> onClick.visitInsn(Opcodes.ICONST_4)
                else -> onClick.visitIntInsn(Opcodes.BIPUSH, 8)
            }

            onClick.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/view/View", "setVisibility", "(I)V", true)
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

            val listener = "${className}${viewId.substring(0, 1).uppercase()}${viewId.substring(1)}OnClickListener"
            val type = getTypedName(packageName, listener)

            // 一个方法初始化一个view 方便生成
            val method = cw.visitMethod(Opcodes.ACC_PRIVATE, name, "()V", null, null)
            method.visitVarInsn(Opcodes.ALOAD, 0)
//            // R.id.xx
            method.visitFieldInsn(Opcodes.GETSTATIC, "$mRClassType\$id", viewId, "I")

            method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, selfType, "findViewById", "(I)Landroid/view/View;", false)
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

                method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "android/widget/Toast", "show", "()V", false)
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

        cw.visitEnd()

        val bytes = cw.toByteArray()

        writeClassToFile(packageName, className, bytes)
    }

    private fun generateLayout(layoutName: String): List<String> {
        val drawableName = resPrefix + generateResName()
        if (mDrawableIds.add(drawableName)) {
            generateDrawable(drawableName)
        }

        val ids = mutableSetOf<String>()
        val rnd = Random.nextInt(2, 18)
        // 生成布局

        val root = """<?xml version="1.0" encoding="utf-8"?>
            <LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
                android:layout_width="match_parent"
                android:layout_height="match_parent"
                android:orientation="vertical">
                
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

        for (i in 0 until rnd) {
            val viewId = generateResName()
            val hasId = ids.add(viewId)

            val widget = VIEWS[Random.nextInt(VIEWS.size)]

            // 宽高
            val tpl = """
                <$widget
                    ${if (hasId) "android:id=\"@+id/$viewId\"" else ""}   
                    android:layout_width="${getDimens()}"
                    ${if (widget == "LinearLayout") linear() else ""}
                    android:layout_height="${getDimens()}" />
                """.trimIndent()

            xml.append(tpl).append("\n")

        }

        xml.append("</LinearLayout>")

        val file = File(workspace, "res/layout/$layoutName.xml")
        writeStringToFile(file, xml.toString())

        return ids.toList()
    }

    private fun generateDrawable(drawableName: String) {
        val content = StringBuilder(
            """<vector xmlns:android="$ANDROID_SCHEMA"
                    android:width="${Random.nextInt(100)}dp"
                    android:height="${Random.nextInt(100)}dp"
                    android:viewportWidth="${Random.nextInt(100)}"
                    android:viewportHeight="${Random.nextInt(100)}">
                    <path  android:fillColor="${generateColor()}"   android:pathData="M"""
        )
        val t: Int = Random.nextInt(10, 40)
        for (i in 0 until t) {
            if (i != t - 1) {
                content.append(Random.nextInt(100)).append(",")
            } else {
                content.append(Random.nextInt(100))
            }
        }
        content.append("z\" />\n").append("</vector>\n").append("\n")
        val drawableFile = File(workspace, "res/drawable/$drawableName.xml")
        writeStringToFile(drawableFile, content.toString())
    }

    private fun generateOtherClass(packageName: String, className: String): Pair<Set<String>, List<String>> {
        val cw = ClassWriter(ClassWriter.COMPUTE_FRAMES)
        val owner = getTypedName(packageName, className)

        val acc = if (className.hashCode() and 1 == 0) Opcodes.ACC_PUBLIC else Opcodes.ACC_MODULE

        cw.visit(Opcodes.V1_6, acc, owner, null, "java/lang/Object", null)


        val fields = HashSet<String>(16)

        val cnt = Random.nextInt(2, 16)

        repeat(cnt) {
            val field = generateFieldName()
            fields.add(field)
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
            val name = generateMethodName()
            if (!methods.add(name)) {
                return@repeat
            }

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
                        Opcodes.INVOKESTATIC, "android/util/Log", "d", "(Ljava/lang/String;Ljava/lang/String;)I", false
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
                    method.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/String", "hashCode", "()I", false)
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
            val name = "get" + field.substring(0, 1).uppercase() + field.substring(1)

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
            println("generatePackageName exclude：$name")
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
            println("generateClassName exclude：$packageName.$name")
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
        val sb = java.lang.StringBuilder()
        sb.append("#")
        for (i in 0..5) {
            sb.append(COLORS[Random.nextInt(COLORS.size)])
        }
        return sb.toString()
    }

    private fun generateBigValue(): String {
        val abc1 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKMNLOPQRSTUVWZYZ0123456789".toCharArray()
        val sb = java.lang.StringBuilder()
        for (i in 0 until Random.nextInt(10, 100)) {
            sb.append(abc1[Random.nextInt(abc1.size)])
        }
        return sb.toString()
    }

    private fun getTypedName(packageName: String?, className: String): String {
        val fullName = if (packageName.isNullOrEmpty()) className else "$packageName/$className"
        return fullName.replace(".", "/")
    }


    private fun generateManifest() {
        val manifestFile = File(workspace, "AndroidManifest.xml")

        val xml =
            """<manifest xmlns:android="http://schemas.android.com/apk/res/android" xmlns:tools="http://schemas.android.com/tools" package="$appPackageName"> 
        <application>
        
        ${
                mActivities.joinToString("\n\n") { activity ->
                    "<activity android:name=\"$activity\" />"
                }
            }
        
       </application>
    </manifest>
        """.trimIndent()

        writeStringToFile(manifestFile, xml)

    }

    private fun generateStringsFile() {
        val res = File(workspace, "res/values/strings.xml")

        val xml = """<?xml version="1.0" encoding="utf-8"?>
            <resources>
                ${
            mStringIds.joinToString("\n") {
                "<string name=\"$it\">${generateBigValue()}</string>"
            }
        }
            </resources>
        """.trimIndent()

        writeStringToFile(res, xml)
    }

    private fun generateKeepProguard() {

        // 生成混淆保持文件
        val proguard = "-keep class ${appPackageName}.**{*;}"
        writeStringToFile(File(workspace, "consumer-rules.pro"), proguard)

        val prefix: String = resPrefix

        if (prefix.isEmpty()) {
            return
        }

        val keep = "@layout/$prefix*,@drawable/$prefix*,@string/$prefix*"

        val content = """<?xml version="1.0" encoding="utf-8"?>
        <resources xmlns:tools="http://schemas.android.com/tools"
        tools:keep="$keep"
        tools:shrinkMode="strict"/>""".trimIndent()
        val rnd = generateMethodName()
        writeStringToFile(File(workspace, "res/raw/" + prefix + rnd + "_keep.xml"), content)
    }

    private fun writeRFile() {
        val strings = mStringIds.joinToString("\n") { "int string $it 0x0" }
        val layouts = mLayoutIds.joinToString("\n") { "int layout $it 0x0" }
        val drawables = mLayoutIds.joinToString("\n") { "int drawable $it 0x0" }
        val ids = mIds.joinToString("\n") { "int id $it 0x0" }

        val txt = strings + "\n" + layouts + "\n" + drawables + "\n" + ids
        writeStringToFile(File(workspace, "R.txt"), txt)
    }

    private fun assembleAar(): File {
        // 将 class 打包成jar
        val classJar = File(workspace, "classes.jar")
        val dir = File(workspace, classesDir)

        classJar.outputStream().use { fos ->
            val jos = JarOutputStream(fos)

            dir.listFiles()?.forEach { file ->
                addFileToJar(file, "", jos)
            }

            jos.finish()
        }

        // 删除 class 目录
        // dir.deleteRecursively()

        // 打包aar
        val out = File(output, "junk_" + appPackageName.replace(".", "_") + "_TT2.0.0.aar")
        val parent = out.parentFile
        if (!parent.exists()) {
            parent.mkdirs()
        }

        out.outputStream().use { fos ->
            val zos = ZipOutputStream(fos)
            workspace.listFiles { _, name -> name != classesDir }?.forEach { file -> addFileToZip(file, "", zos) }

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

        file.inputStream().use { fos ->
            val bytes = ByteArray(10240)
            var len: Int
            while (true) {
                len = fos.read(bytes)
                if (len == -1) break
                zos.write(bytes, 0, len)
            }

            zos.closeEntry()
        }
    }

    private fun writeStringToFile(file: File, content: String): Boolean {
        if (!file.parentFile.exists()) {
            file.parentFile.mkdirs()
        }

        return FileWriter(file).runCatching {
            use { write(content) }
        }.isSuccess
    }


    private fun writeClassToFile(packageName: String, className: String, bytes: ByteArray) {
        val dir = File(workspace, classesDir)
        val parent = File(dir, packageName.replace(".", "/"))
        val file = File(parent, "$className.class")

        if (!parent.exists()) {
            parent.mkdirs()
        }

        if (file.exists()) {
            file.createNewFile()
        }

        file.writeBytes(bytes)
    }


    private fun fileSize(size: Long): String {
        val gb = 1024 * 1024 * 1024 //定义GB的计算常量

        val mb = 1024 * 1024 //定义MB的计算常量

        val kb = 1024 //定义KB的计算常量

        // 格式化小数
        // 格式化小数
        val df = DecimalFormat("0.00")

        return if (size / gb >= 1) {
            //如果当前Byte的值大于等于1GB
            df.format(size / gb.toFloat()) + "GB"
        } else if (size / mb >= 1) {
            //如果当前Byte的值大于等于1MB
            df.format(size / mb.toFloat()) + "MB"
        } else if (size / kb >= 1) {
            //如果当前Byte的值大于等于1KB
            df.format(size / kb.toFloat()) + "KB"
        } else {
            size.toString() + "B"
        }
    }
}