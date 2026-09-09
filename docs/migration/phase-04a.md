# Phase 4A — KeyStore 生成页面

状态：实施中，基于 Phase 3 `3b27fa86`。本阶段只迁移 KeyStore 生成；沿用既有本机基线范围。

## 4A.1 能力提取

- 增加纯 Kotlin GenerateKeyStoreRequest/Outcome、GenerateKeyStoreUseCase，扩展已有 KeyStoreRepository。
- JvmKeyStoreDataSource 在 IO 调用原 KeystoreHelper.createNewStore，保留格式名、位数、DN（包含 `, C=`）、字符串转有效期及既有文件处理。互斥锁等阻塞调用真正返回后才释放；取消不转为失败通知。
- 旧 MainViewModel 暂时调用新用例，保留原显示与完成动作。日志不再输出含密码的表单。
- `./gradlew --offline --no-daemon :shared:compileKotlinJvm :composeApp:compileKotlinJvm --console=plain`：成功，39 秒。原 Skiko/生成代码等警告保留。

后续：纯表单与拥有者切换、行为及真实产物测试、视觉/原生交互验收。
