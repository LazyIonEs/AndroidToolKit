# Phase 0B — Rust 构建依赖修复

状态：本机干净生成与 native 装载验证通过；不代表 Phase 0 或全平台发行验收通过。

基线：`b32698ff2f1f2acce56eb3b472a00b6f2c895923`。实现日期：2026-09-09。

## 复现

原代码的增量 G0 编译成功（9s，18 tasks，14 up-to-date），但下面命令失败：

```sh
./gradlew --offline --no-daemon :shared:clean :composeApp:clean :shared:compileKotlinJvm :composeApp:compileKotlinJvm --no-build-cache --console=plain
```

配置阶段先运行 `runBuildRust()`，随后 `:shared:clean` 删除已生成的 Kotlin 绑定。
编译任务没有生成源码的前置依赖，导致 `uniffi` / `ToolKitRustException` 未解析。
原始错误见 [original-clean-failure.txt](evidence/original-clean-failure.txt)。

## 改动

- `buildRustLibrary`：在执行阶段调用原 Cargo/cross 命令，声明 Rust 输入、平台参数和动态库输出。每次交由 Cargo 判断工具链、环境及原生编译缓存，避免 Gradle 因未声明环境变量而复用过期 native 产物。
- `generateRustBindings`：显式生成原包名的绑定；`jvmMain.kotlin.srcDir(TaskProvider)` 自动建立编译前依赖。
- `copyRustLibrary`：Sync 到独立生成资源目录，作为 `jvmMain` resources；JAR 内仍是原 `libuniffi_toolkit.*` / `uniffi_toolkit.dll` 根路径。
- 保留 `rustTasks` 聚合入口，composeApp 使用明确的 `:shared:rustTasks` 路径。
- 删除配置阶段副作用及编译后的补救性 `doLast`。不改变 Rust crate、UDL、依赖版本和算法。

`src/lut.inc` 由 build.rs 生成，不作为 Rust 源输入触发自身失效。`Cargo.lock` 若存在则参与输入；没有删除 `rust/target`，本记录不声称 Rust 从零下载/编译通过。

## 验证

| 验证 | 结果 |
| --- | --- |
| 同一条 clean + compile 命令，禁用 Gradle build cache | 成功，52s，27 tasks，22 executed / 5 up-to-date |
| 绑定恢复 | clean 后由 `generateRustBindings` 在 Kotlin 编译前生成 |
| 动态库装包 | shared JVM JAR 根部唯一 `libuniffi_toolkit.dylib`；SHA-256 与 Cargo 输出相同 |
| 配置阶段副作用 | `:shared:rustTasks --dry-run` 全部 SKIPPED，无 Cargo 执行及文件复制 |
| 真实 native 调用 | Phase 0 测试调用 resizePng、resizeFir、oxipng、quantize、mozJpeg，输出可解码 |
| macOS 隔离测试包 | jpackage app-image 可启动并显示原九页面 |
| 正式发行包 / Windows / Linux / 其他架构 | 未执行，不作为本机证据推断 |

编译日志见 [phase0b-clean-success.txt](evidence/phase0b-clean-success.txt)，dry-run 见 [phase0b-dry-run.txt](evidence/phase0b-dry-run.txt)。

保留的旧警告：ZoomImage/Skiko 版本冲突、UniFFI 两处 unused expression、`rememberModalBottomSheetState` 废弃。

## 所有权和回滚

本阶段没有页面状态、VM owner、偏好或 UI-session 变更；`MainViewModel` 仍是原唯一写入者。
回滚本阶段提交即可恢复旧构建脚本。用户原有 `composeApp/output/` 发行包未清理或覆盖。
