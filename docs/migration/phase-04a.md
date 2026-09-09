# Phase 4A — KeyStore 生成页面

状态：**完成本阶段实现及本机验证**。基于 Phase 3 `3b27fa86`，提取提交 `4598f480`，拥有者切换提交 `12b1ee6a`。本阶段只迁移 KeyStore 生成；Phase 4B 尚未开始。

## 4A.1 能力提取

- 增加纯 Kotlin GenerateKeyStoreRequest/Outcome、GenerateKeyStoreUseCase，扩展已有 KeyStoreRepository。
- JvmKeyStoreDataSource 在 IO 调用原 KeystoreHelper.createNewStore，保留格式名、位数、DN（包含 `, C=`）、字符串转有效期及既有文件处理。互斥锁等阻塞调用真正返回后才释放；取消不转为失败通知。
- 旧 MainViewModel 暂时调用新用例，保留原显示与完成动作。日志不再输出含密码的表单。
- `./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm --console=plain`：成功，39 秒。原 Skiko/生成代码等警告保留。

## 4A.2–4A.3 纯表单与拥有者切换

- KeyStoreForm 全字段为 val；确认密码仅留 UI。每字段对应独立 Intent，GenerationBox/密码行/创建按钮接不可变切片和回调；绘制与组件未替换。
- KeyStoreGenerationViewModel 在窗口 owner 创建，唯一持有表单、路径校验和 busy；根只订阅 distinct busy 投影。Entry 转入 Route，旧 KeyStoreInfo、旧生成状态/方法及 KEYSTORE_OUTPUT 槽全部删除。
- 新 VM 自己消费偏好 outputPathVersion；自选目录只在默认路径实际变化时覆盖。五表单跨页测试已换接新拥有者，其余四分支继续作为旧功能兼容桥。
- Submit 同步捕获表单和格式/位数，busy 在调度前置位；重新探测该请求的输出目录，避免使用过期/未完成的路径结果。重复 Submit 不重复生成。finally 释放 busy；取消透传，非协作迟到结果不发成功或失败通知。
- 原成功文案、jump 按钮、Short 时长、关闭按钮及完成时捕获的输出文件路径保持一致。原错误消息和所有必填项、.jks/.keystore 区分大小写规则保留。

## 已完成验证

- 两模块编译、全部 JVM 测试：66 项通过，0 失败/错误/跳过（32 秒）。新增 14 项，原 52 项继续通过。
- 新 VM 测试覆盖全部字段、原必填/确认边界、捕获快照、重复提交、失败/重试、同文案独立效果、异步路径乱序、磁盘状态变化、窗口清理和不可中断结果返回。
- 真实 JKS/PKCS12 × 1024/2048 四组合，含中文/空格路径和不同 store/alias 密码。Java KeyStore 打开验证实际格式、alias、RSA 公私钥位数、私钥可读、自签名证书和精确有效期；另以原 helper 直接生成结果对照算法、DN、serial/version/期限。原 helper 经 BouncyCastle 反转 RDN 顺序的行为保留，不按预期字符串顺序擅改实现。
- 已有文件保留其他 alias；缺失父目录不自动创建；错误密码、有效期和 DN 失败消息与原 helper 一致。取消 IO 入队任务不创建文件，后续重试成功。
- 实际 App/Compose UI 输入所有字段后点击生成，核对用例请求和原 Snackbar；切主题/导航返回保持同一窗口 VM 与草稿。
- 五页 × 两主题软件静态 PNG 共 10 张，与 Phase 0 零像素差异，无遮罩、裁切或容差；冻结 22 项资源校验通过。

## 官方 MCP 与原生验收

- 使用 JBR 21 和官方 Hot Reload 1.2.0 MCP，复用前阶段脚本：18 次明暗导航无 UI 异常，10 对静态重复 PNG 零像素差异；设置输入保留空白、四个可见表单接收默认目录、无关设置不覆盖自选目录。五个拥有者的完整联动继续由 VM 回归覆盖。
- 首两次 MCP 采集分别在 `light/apk-tool` 和 `light/apk-signature` 的右下圆角发现 2、32 个像素波动，全部位于 x≥789、y≥555。原图及坐标保留在 `mcp-initial-corner-differences`。随后只把测试的最短稳定等待延长至 2 秒，原零差异断言不变，全部静态重复对比通过。
- 最终 MCP 静态图跨 Phase 0 对比：亮色各 108 像素（设置 110），暗色各 114 像素不同，全部在两侧底部圆角 y≥554。没有编辑、裁切、遮罩或容差；不声称 MCP 全窗口跨阶段零差异。软件静态图十张均为 0。
- 最终常规 classpath 的隔离原生 `.app` 实测：FileKit 选择临时目录回填正确，取消保持原路径；实际键盘 Cmd+A 替换文件名、Tab 转到密钥密码后输入并显示掩码。
- 使用合成凭据完整填写原生表单并生成真实 JKS，观察原加载 Lottie、结束后原成功通知和 jump 按钮；jump 在 Finder 定位到该临时文件。Java KeyStore 再次验证 native 产物：alias 正确、不同 store/alias 密码可读私钥、RSA 2048、SHA256withRSA、自签名验证成功、730 天有效期。未提交私钥文件。
- 原生证据仅保存主窗口截图/AX，以及 Finder 中与 fixture 有关的文本行；没有保存系统选择器的目录列表。验收后的隔离原生窗口已关闭。

| 门禁 | 结果 |
| --- | --- |
| G0 编译 | 两模块编译、66 项 JVM 测试通过 |
| G1 行为 | 四种真实产物、原校验/消息/已有文件规则通过 |
| G2 UDF | 新 Screen 无 VM/DI/Repository/可写状态参数；字段事件与单一拥有者通过 |
| G3 视觉 | 10 张软件图跨基线零差异，MCP 10 对静态重复图零差异；圆角差异逐项记录 |
| G4 交互 | MCP 导航/草稿/联动，原生 picker/取消/键盘/生成/跳转通过 |
| G5 异步 | 请求快照、乱序、重复提交、失败/重试、取消与 busy 释放通过 |

沿用此前已确认的基线范围：没有补录固定动画帧、真实 OS 拖拽或性能基线，也不声称完成 Windows/Linux 验收。本阶段不要求 G6；本次原生 `.app` 是测试入口的本机打包，不是 ProGuard 发行包。

## 回放与回滚

```sh
./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm :shared:jvmTest :shared:baselineClasspath --console=plain
python3 scripts/migration/check_visual_assets.py
# JBR21_HOME 指向本机 JetBrains JDK 21 的 Contents/Home。
# 启动命令及隔离属性见 phase-00-mcp.md；创建脚本使用的 fixture 目录。
mkdir -p shared/build/migration/fixtures/phase3-output
python3 - "$JBR21_HOME" <<'PYREPLAY'
import sys, time
sys.path.insert(0, 'scripts/migration')
original_sleep = time.sleep
time.sleep = lambda seconds: original_sleep(max(seconds, 2.0))
import hot_mcp_client
sys.argv = ['hot_mcp_client.py', sys.argv[1], '--phase3-smoke']
hot_mcp_client.main()
PYREPLAY
# MCP 完成后，恢复常规 classpath 再打包原生测试入口。
./gradlew --offline --no-daemon :shared:baselineClasspath --console=plain
MIGRATION_JAVA_HOME="$JBR21_HOME" python3 scripts/migration/package_baseline.py
```

完整记录见 [evidence/phase-04a](evidence/phase-04a)：测试清单、原始图片、比较结果、原生步骤、产物检查和 SHA-256 清单。

回滚时一起撤销 Entry/App busy 接线与新拥有者，恢复旧状态/生成方法，避免两个页面拥有者同时订阅默认目录或接收 Submit。能力提取提交可独立保留。代码回滚不会撤销已经生成或替换的 KeyStore；本次所有写盘仅发生在临时 fixture。KeyStore 本身不再有旧桥，其余四个表单的兼容订阅留待各自阶段删除。
