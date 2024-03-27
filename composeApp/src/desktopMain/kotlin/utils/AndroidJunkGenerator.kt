package utils

import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.util.Locale
import java.util.Random

/**
 * @Author      : LazyIonEs
 * @CreateDate  : 2024/3/19 16:21
 * @Description : 垃圾代码生成
 * @Version     : 1.0
 */
object AndroidJunkGenerator {

    private const val ANDROID_SCHEMA = "http://schemas.android.com/apk/res/android"

    private val KEYWORDS = arrayListOf(/*用于定义访问权限修饰符的关键字*/
        "private", "protected", "public",/*用于定义类、函数、变量修饰符的关键字*/
        "abstract", "final", "static", "synchronized",/*用于定义类与类之间关系的关键字*/
        "extends", "implements",/*用于定义建立实例及引用实例、判断实例的关键字*/
        "new", "this", "super", "instanceof",/*用于异常处理的关键字*/
        "try", "catch", "finally", "throw", "throws",/*基本数据类型*/
        "int", "long", "double", "float", "boolean", "byte", "short", "String",/*用于包的关键字*/
        "package", "import",/*其他修饰符关键字*/
        "native", "strictfp", "transient", "volatile", "assert", "do", "while"
    )

    private val VIEWS = arrayListOf(
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

    private val XML_KEYWORDS = arrayListOf("null")

    // 生成垃圾代码module的名称 - 根据自己项目修改名称即可
    private const val MODULE_NAME = "junk"

    private val mainPathName = "$MODULE_NAME/src/main"

    private val javaPackageName = "$mainPathName/java/"

    private var random = Random()
    private val abc = "abcdefghijklmnopqrstuvwxyz".toCharArray()
    private val abc123 =
        "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKMNLOPQRSTUVWZYZ0123456789".toCharArray()
    private val color = "0123456789abcdef".toCharArray()
    private val activityList = ArrayList<String>(12048)
    private val stringList = HashSet<String>(4096)

    // 生成垃圾代码的包名- 根据自己项目修改名称
    var appPackageName = "com.dev.junk.plugin"

    // 资源文件前缀名
    var resPrefix = "junk_"

    // 生成包数量
    var packageCount = 5

    // 每个包下生成Activity类数量
    var activityCountPerPackage = 3

    // 项目路径
    var projectDir: String? = null

    fun generator() {
        activityList.clear()
        stringList.clear()
        initFile()
        //生成类
        generateClasses()
        println("Activity文件生成完成")
        //生成资源
        generateStringsFile()
        println("资源文件生成完成")
        // 生成build文件
        generateBuildFile()
        // 生成 keep 资源文件，防止开启混淆优化掉
        generateKeepProguard()
        println("源码生成完成")
    }

    private fun initFile() {
        val fileDir = File(MODULE_NAME)
        deleteFile(fileDir)
    }

    // 删除文件
    private fun deleteFile(file: File) {
        if (file.isFile()) {
            file.delete()
        } else {
            val childFilePath = file.list() ?: return
            for (path in childFilePath) {
                val childFile = File(file.getAbsoluteFile().toString() + "/" + path)
                deleteFile(childFile)
            }
            file.delete()
        }
    }

    private fun generateClasses() {
        for (i in 0 until packageCount) {
            val packageName = generatePackageName()
            //生成Activity
            for (j in 0 until activityCountPerPackage) {
                val activityPreName = generateName()
                generateActivity("$appPackageName.$packageName", activityPreName)
            }
        }

        val rootClassCount =
            random.nextInt(activityCountPerPackage) + (activityCountPerPackage shr 1)

        for (j in 0 until rootClassCount) {
            val activityPreName = generateName()
            generateActivity(appPackageName, activityPreName)
        }

        //所有Activity生成完了
        generateManifest()
    }

    private fun generatePackageName(): String {
        val sb = StringBuilder()
        val len = random.nextInt(8) + 2
        for (i in 0 until len) {
            sb.append(abc[random.nextInt(abc.size)])
        }
        // 排除关键字
        val name = sb.toString()
        return if (KEYWORDS.contains(name)) {
            // 重新生成
            generatePackageName()
        } else name
    }

    private fun generateName(): String {
        val sb = StringBuilder()
        val len = random.nextInt(8) + 4
        for (i in 0 until len) {
            sb.append(abc[random.nextInt(abc.size)])
        }
        val name = sb.toString()
        return if (KEYWORDS.contains(name) || XML_KEYWORDS.contains(name)) {
            generateName()
        } else name
    }

    private fun generateActivity(packageName: String, activityPreName: String) {
        val className =
            abc[random.nextInt(abc.size)].uppercase(Locale.getDefault()) + activityPreName + "Activity"
        val layoutName = resPrefix + "layout_" + activityPreName
        val textIds = generateLayout(layoutName) //生成layout
        val stringsXml = resPrefix + generateName().lowercase(Locale.getDefault()) //生成strings字符串
        stringList.add(stringsXml)

        val otherClassName =
            abc[random.nextInt(abc.size)].uppercase(Locale.getDefault()) + generateName()
        val fieldList = generateClass(packageName, otherClassName)
        val widget: String = VIEWS[random.nextInt(VIEWS.size)]
        val content = java.lang.StringBuilder(
            """
                         package  $packageName;
                         
                         import android.app.Activity;
                         import android.os.Bundle;
                         import $appPackageName.R;
                         import java.lang.Exception;
                         import java.lang.Override;
                         import java.lang.RuntimeException;
                         import java.lang.String;
                         
                         """.trimIndent()
        )
        content.append("import android.widget.").append(widget).append(";\n")
        content.append("import android.view.View;\n").append("import android.widget.TextView;;\n")
            .append("import System;\n").append("import android.widget.Toast;\n")
            .append("import java.util.Date;\n").append("\n").append("public class ")
            .append(className).append(" extends Activity {\n").append("    @Override\n")
            .append("    protected void onCreate(Bundle savedInstanceState) {\n")
            .append("        super.onCreate(savedInstanceState);\n")
            .append("        setContentView(R.layout.").append(layoutName).append(");\n")

        for (textId in textIds) {
            val name = generateName()
            content.append("   final  View  ").append(name).append(" = findViewById(R.id.")
                .append(textId).append(");\n").append("         ").append(name)
                .append(".setOnClickListener(new View.OnClickListener() {\n")
                .append("            @Override\n")
                .append("            public void onClick(View v) {\n").append(name)
                .append(".setVisibility(View.INVISIBLE);\n").append("            }\n")
                .append("        });\n")
        }

        val otherClassNameField = generateName()
        var methodName = generateName()

        content.append("\n").append(methodName).append("();\n").append(otherClassName).append("   ")
            .append(otherClassNameField).append(" =     new ").append(otherClassName)
            .append("();\n")

        for (s in fieldList) {
            content.append(otherClassNameField).append(".").append(s).append(" =\"")
                .append(generateBigValue()).append("\";\n")
        }
        content.append(" Toast.makeText(").append(className).append(".this,getString(R.string.")
            .append(stringsXml).append("),Toast.LENGTH_SHORT).show();\n").append("    }")

        val bwe = random.nextInt(20) + 3
        for (j in 0 until bwe) {
            val methodNameNext = generateName()
            if (j != bwe - 1) {
                content.append("\n").append(" void ").append(methodName).append("() {")
                    .append("\n         ").append(methodNameNext).append("();\n").append("}")
            } else {
                val name = generateName()
                content.append("\n").append(" void ").append(methodName).append("() {\n")
                    .append(widget).append(" ").append(name).append("   = new ").append(widget)
                    .append("(").append(className).append(".this);\n").append(name)
                    .append(".setVisibility(View.VISIBLE);\n").append("}")
            }
            methodName = methodNameNext
        }
        content.append("}")

        val javaFile = File(
            javaPackageName,
            packageName.replace(".", File.separator) + File.separator + className + ".java"
        )
        writeStringToFile(javaFile, content.toString())

        val actPath = "$packageName.$className"
        activityList.add(actPath)
    }

    @Throws(IOException::class)
    private fun generateClass(packageName: String, className: String): List<String> {
        val fields: MutableSet<String> = java.util.HashSet(16)
        val content = StringBuilder(
            """package  $packageName;

import java.lang.Exception;
import java.lang.RuntimeException;
import java.lang.String;
import java.lang.System;
import java.util.Date;

public class $className  {
"""
        )
        val t = random.nextInt(20)
        for (i in 0 until t) {
            val name = generateName()
            if (fields.add(name)) {
                content.append("\npublic String ").append(name).append(";")
            }
        }
        content.append("\n      public ").append(className).append("() {\n")
            .append("        }\n}\n")
        val drawableFile = File(
            javaPackageName,
            packageName.replace(".", File.separator) + File.separator + className + ".java"
        )
        writeStringToFile(drawableFile, content.toString())
        return ArrayList(fields)
    }

    /**
     * 生成layout
     */
    @Throws(IOException::class)
    private fun generateLayout(layoutName: String): List<String> {
        val textIds = ArrayList<String>()
        val drawableName = resPrefix + generateName().lowercase(Locale.getDefault())
        generateDrawable(drawableName)
        val content = StringBuilder(
            "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n<LinearLayout xmlns:android=\"$ANDROID_SCHEMA\"\n    android:layout_width=\"" + random.nextInt(
                1000
            ) + "dp\"\n" + "    android:layout_height=\"" + random.nextInt(1000) + "dp\"\n" + "    android:orientation=\"vertical\">\n"
        )
        val t = random.nextInt(20)
        for (i in 0 until t) {
            val id = generateName()
            textIds.add(id)
            val widget = VIEWS[random.nextInt(VIEWS.size)]
            content.append("   <").append(widget).append("\n").append("        android:id=\"@+id/")
                .append(id).append("\"\n").append("        android:layout_width=\"")
                .append(random.nextInt(1000)).append("dp\"\n")
                .append("        android:layout_height=\"").append(random.nextInt(1000))
                .append("dp\"\n").append("        android:text=\"").append(generateName())
                .append("\" \n").append("        android:background=\"@drawable/")
                .append(drawableName).append("\" \n")
            if ((widget == "LinearLayout")) {
                if (random.nextBoolean()) {
                    content.append("android:orientation=\"horizontal\"")
                } else {
                    content.append("android:orientation=\"vertical\"")
                }
            }
            content.append("/>\n")
        }
        content.append("   </LinearLayout>\n")
        val layoutFile = File("$mainPathName/res/layout/$layoutName.xml")
        writeStringToFile(layoutFile, content.toString())
        return textIds
    }

    @Throws(IOException::class)
    private fun generateDrawable(drawableName: String) {
        val content = StringBuilder(
            """<vector xmlns:android="$ANDROID_SCHEMA"
   android:width="${random.nextInt(100)}dp"
 android:height="${random.nextInt(100)}dp"
 android:viewportWidth="${random.nextInt(100)}"
 android:viewportHeight="${random.nextInt(100)}"
>
     <path
  android:fillColor="${generateColor()}"
   android:pathData="M"""
        )
        val t = random.nextInt(40)
        for (i in 0 until t) {
            if (i != t - 1) {
                content.append(random.nextInt(100)).append(",")
            } else {
                content.append(random.nextInt(100))
            }
        }
        content.append("z\" />\n").append("</vector>\n").append("\n")
        val drawableFile = File("$mainPathName/res/drawable/$drawableName.xml")
        writeStringToFile(drawableFile, content.toString())
    }

    @Throws(IOException::class)
    private fun generateManifest() {
        val manifestFile = File("$mainPathName/AndroidManifest.xml")
        val sb = StringBuilder()
        sb.append("<manifest xmlns:android=\"$ANDROID_SCHEMA\"")
        sb.append(
            """   
 package="$appPackageName">"""
        )
        sb.append("\n <application>")
        for (s in activityList) {
            sb.append("<activity android:name=\"").append(s).append("\"/>\n")
        }
        sb.append("\n </application>")
        sb.append("\n </manifest>")
        writeStringToFile(manifestFile, sb.toString())
    }

    //    生成strings.xml
    @Throws(IOException::class)
    private fun generateStringsFile() {
        val sb = StringBuilder()
        sb.append("<resources>\n")
        for (s in stringList) {
            sb.append("<string name=\"").append(s).append("\">").append(generateBigValue())
                .append("</string>\n")
        }
        sb.append("</resources>")
        val stringFile = File("$mainPathName/res/values/strings.xml")
        writeStringToFile(stringFile, sb.toString())
    }

    @Throws(IOException::class)
    private fun generateBuildFile() {
        val content = """apply plugin: 'com.android.library'

android {
    compileSdkVersion 30
    
    defaultConfig {
        minSdkVersion 21
        targetSdkVersion 29
        versionCode 1
        versionName "1.0"
        testInstrumentationRunner "android.support.test.runner.AndroidJUnitRunner"
        consumerProguardFiles "consumer-rules.pro"
    }
}

dependencies {
    compileOnly 'androidx.appcompat:appcompat:1.3.1'
}"""
        val buildFile = File("$MODULE_NAME/build.gradle")
        writeStringToFile(buildFile, content)
    }

    @Throws(IOException::class)
    private fun generateKeepProguard() {
        // 生成混淆保持文件
        val proguard = """
                # 垃圾代码保护
                -keep class $appPackageName.**{*;}
                """.trimIndent()
        val proFile = File(MODULE_NAME, "consumer-rules.pro")
        writeStringToFile(proFile, proguard)
        val prefix = resPrefix
        if (prefix.isEmpty()) {
            return
        }
        val keep = "@layout/$prefix*,@drawable/$prefix*,@string/$prefix*"
        val content = """<?xml version="1.0" encoding="utf-8"?>
<resources xmlns:tools="http://schemas.android.com/tools"
tools:keep="$keep"
    tools:shrinkMode="strict"/>"""
        val file = File(mainPathName + "/res/raw/" + prefix + "keep.xml")
        writeStringToFile(file, content)
    }

    private fun generateColor(): String {
        val sb = StringBuilder()
        sb.append("#")
        for (i in 0..5) {
            sb.append(color[random.nextInt(color.size)])
        }
        return sb.toString()
    }

    private fun generateBigValue(): String {
        val sb = StringBuilder()
        for (i in 0 until random.nextInt(1000)) {
            sb.append(abc123[random.nextInt(abc123.size)])
        }
        return sb.toString()
    }

    @Throws(IOException::class)
    private fun writeStringToFile(file: File, data: String) {
        if (!file.getParentFile().exists()) {
            file.getParentFile().mkdirs()
        }
        try {
            FileWriter(file).use { writer -> writer.write(data) }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}