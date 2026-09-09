# Phase 4A — KeyStore 生成页面

状态：实施中，基于 Phase 3 `3b27fa86`。本阶段只迁移 KeyStore 生成；沿用既有本机基线范围。

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

后续：官方 MCP 与原生 FileKit/键盘交互验收、最终记录。
