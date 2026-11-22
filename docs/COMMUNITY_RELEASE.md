# 社区版正式发布与后续更新

更新源固定为 `awesome-sean/scriptable-android` 的 GitHub Releases。更新页查询最新正式版，忽略草稿和预发布，不需要给 App 配置 GitHub Token。

## 一次性准备

为社区版准备并备份自己的正式签名密钥。不要使用上游密钥；不要提交 keystore、密码或 signing.properties。保护密钥，因为后续所有覆盖更新都需要相同签名，合法的签名轮换也需要正确的 Android 证书链。

Gradle 沿用上游的 `SCRIPTABLE_SIGNING_PROPERTIES` 环境变量，指向本机未提交的属性文件：

```properties
storeFile=/absolute/private/path/community.jks
storePassword=你的存储密码
keyAlias=community
keyPassword=你的密钥密码
```

属性文件必须位于已授权的私有位置，不放进仓库。只有本地或受控 CI 能读取密钥。

## 每次发布

1. 修改 `app/build.gradle`：versionCode 严格递增；versionName 使用 `主.次.修订`，例如 `0.2.3`。同步更新关于页的版本文字。
2. 完成运行时、浏览器、原生回归测试和 lint，在真机验证自动化、桌面组件与上一正式版覆盖安装。
3. 设置 `SCRIPTABLE_SIGNING_PROPERTIES` 后执行 `./gradlew assembleRelease`。没有签名配置时生成的是未签名包，不能作为用户更新包。
4. 使用 Android SDK 的 `apksigner verify --print-certs` 验证签名与上一正式包一致，确认包名仍是 `dev.scriptable.android.community`。
5. 将签名 APK 命名为 **scriptable-community-release.apk**。
6. 创建对应 GitHub Release，tag 使用 `v0.2.3` 这样的格式，上传 APK，填写更新说明，并发布为正式版。
7. 用上一正式版进入设置→检查更新，验收下载、安装权限、系统安装确认与配置保留。

普通 Git 标签、源码 ZIP、Actions 调试产物和预发布不会触发正式更新。不要把上游包、调试包或不同签名的 APK 改名冒充正式包。

## 用户侧行为

显示新版本、说明与包大小；用户点“下载并安装”后由系统下载管理器处理下载。正在查看更新页时，下载完成会继续校验并打开系统安装；离开或进程被清理后，可回到该页继续安装。允许移动网络下载，不自动使用漫游网络。

安装前校验下载大小、GitHub 提供的 SHA-256（有提供时）、应用包名、递增 versionCode 和签名。只分享内部目录中校验后的单个 APK，安装器只有读取权限。安卓系统要求允许此来源安装并最终确认安装，普通应用不能静默绕过。

应用覆盖安装后数据保留；应用之间不同包名的数据不自动迁移。GitHub 连不上、接口限流、下载失败和签名错误会显示原因，用户可重试或打开发布页。
