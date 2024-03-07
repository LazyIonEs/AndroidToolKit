## 常见问题
## 1.无法打开 "AndroidToolsKit。app"，因为Apple无法检查其是否包含恶意软件。
出现此问题，按照图片依次点击解决
<div align="center">
  <img src=https://github.com/LazyIonEs/AndroidToolsKit/blob/main/screenshots/unopen_1.png width=30% />
  <img src=https://github.com/LazyIonEs/AndroidToolsKit/blob/main/screenshots/unopen_2.png width=30% />
  <img src=https://github.com/LazyIonEs/AndroidToolsKit/blob/main/screenshots/unopen_3.png width=30% />
</div>

## 2.使用内置(aapt/keytool)出现报错 Cannot run program...Permission denied
<div align="center">
  <img src=https://github.com/LazyIonEs/AndroidToolsKit/blob/main/screenshots/cannot_run_program.png width=100% />
</div>
出现此问题，到设置页复制内置(aapt/keytool)路径，打开终端输入以下命令

```
chmod +x (aapt/keytool)路径
```
