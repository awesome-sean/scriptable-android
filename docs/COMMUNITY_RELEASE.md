# Scriptable Android 发布与更新

## 签名保管

本项目采用本地保存密钥、本地签名。GitHub Actions 只负责测试和生成未签名 release APK，不读取发布密钥。密钥、密码与属性文件不得提交到仓库、上传到 Actions 或随安装包分发。

首次准备固定签名后，应离线备份，并在以后的所有正式版本沿用。当前为 PKCS12 格式；Gradle 的 SCRIPTABLE_SIGNING_PROPERTIES 可指向本地属性文件：

```properties
storeFile=/absolute/private/path/release.p12
storePassword=你的存储密码
keyAlias=scriptable-android
keyPassword=你的密钥密码
```

## 发布步骤

1. 递增 app/build.gradle 的 versionCode 和 versionName，并同步关于页。
2. 完成运行时、浏览器、原生回归测试与 lint；验证实际设备上的关键操作。
3. 获取云端构建的未签名 release APK，并核对源码与资源。
4. 在本地使用固定密钥签名，运行 apksigner verify --verbose --print-certs，确认签名证书与包名。
5. 将安装包命名为 Scriptable-Android-版本号.apk，例如 Scriptable-Android-0.2.2.apk；生成 SHA256 校验文件。
6. 创建对应 GitHub Release，例如 v0.2.2，上传 APK 与校验文件，发布为正式版。

## 用户更新

打开应用时自动检查正式发布版本。发现更新后弹窗提醒；编辑脚本时延后到退出编辑再提醒。相同版本在本次应用会话内只提醒一次，短时间重复切回应用不重复请求。网络失败不打断使用。

用户也可在设置中手动检查。点查看更新后进入下载页面，安装前校验大小、摘要、包名、递增版本及签名，最后由系统确认安装。普通应用不能静默安装。

更新源为本仓库 GitHub Releases；源码 ZIP、普通标签、Actions 产物和预发布不会作为正式更新。
