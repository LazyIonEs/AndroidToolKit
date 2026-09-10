# Koin Compiler Plugin 接入

2026-09-10，按最终确认采用 `io.insert-koin.compiler.plugin` **1.2.0**、Kotlin **2.4.10**，Koin BOM/运行库保持 **4.2.2**，应用版本保持 **1.6.10**。

版本及插件别名集中在 `gradle/libs.versions.toml`；根项目以 `apply false` 声明，`shared` 和 `composeApp` 分别应用。沿用官方仓库，不加入本地 Maven 路径、源码依赖或 KSP。现有 lambda DSL、手动参数映射和生命周期释放回调保持原样，没有关闭插件安全检查。

Kotlin 版本调整是必要的配套变更：1.2.0 在本项目原 Kotlin 2.4.20 下实测触发 `DslHintGenerator` 调用 `IrFactory.createSimpleFunction` 的 `NoSuchMethodError`。最终 2.4.10 组合正常完成编译。插件的 [1.2.0 发布说明](https://github.com/InsertKoinIO/koin-compiler-plugin/releases/tag/1.2.0) 介绍了现有 DSL 检查能力及其边界；实际兼容结论以本项目构建结果为准。

验证命令：

```sh
./gradlew --offline --no-daemon -Dorg.gradle.java.home="$JBR21_HOME" -Pkotlin.incremental=false :shared:check :composeApp:check :shared:baselineClasspath --console=plain
```

最终构建用时 2 分钟，38 项任务，**203 项测试、55 个套件，0 失败、错误或跳过**。架构约束、Domain 独立编译、Koin 实例/owner 生命周期、全部功能和真实 fixture 测试通过。[完整日志](evidence/koin-compiler-plugin/check.txt)、[测试汇总](evidence/koin-compiler-plugin/tests.json)。

编译日志确认 `composeApp` 的实际 `startKoin` 入口自动启用 strictSafety。`shared` 生产编译没有启动入口，插件提示在该编译单元跳过完整依赖图验证；应用入口负责汇总检查。测试中的 `ApkInformationUiTest.kt:45` 动态拼装模块，产生 `KOIN-W003`，不能静态证明该测试入口的完整依赖图；该 UI 测试及独立 Koin 运行时测试均通过，未压制警告。插件对手写 lambda 的循环依赖检测仍有官方说明的限制，不声称证明所有运行时路径安全。

与接入前导出的 JVM 测试运行类路径相比，仅 `kotlin-test` / `kotlin-test-junit` 从 2.4.20 变为 2.4.10；应用运行依赖未新增，许可清单无需变化。此前已有的 Skiko 版本提示、UniFFI 未使用表达式、Sheet 弃用等警告仍保留。本轮未重新打包安装器或执行全平台原生验收。
