# 修改记录

## 0.2.3

- 长脚本改用轻量纯文本编辑，保留完整代码、自动换行与字号设置，避免全量高亮和逐行测量阻塞粘贴。
- 缓存普通脚本排版结果与光标行位置，减少键盘、视口变化和移动光标时的重复计算。
- 粘贴超过原有 1 MB 保存上限时明确提示并保留原内容。
- 小组件固定入口不受支持时使用中文说明；桌面拒绝添加请求时不再显示成功提示。
- 本地 125 项运行时检查和全部浏览器检查通过，新增长文本输入、保存重开、撤销、执行及深色模式检查；云端原生回归、Debug/Release 构建及 lint 已通过；尚未进行鸿蒙真机验证。

### 0.2.3 发布验证

- [云端完整构建](https://github.com/awesome-sean/scriptable-android/actions/runs/45053064202) 通过；APK 所有受控界面资源与源码逐项一致。
- 使用原有本地密钥签名，证书与 0.2.2 一致，APK Signature Scheme v2/v3 验证通过。
- APK SHA-256：`91d9dc82b93cfb3fbbfd349d0e5343f6a217e2ec378d61a80711b757cc96ec0a`。
- [下载 0.2.3](https://github.com/awesome-sean/scriptable-android/releases/tag/v0.2.3)。

## 0.2.2

- 应用名称统一为 Scriptable Android；图标裁剪留白并去除紫色背景。
- 统一明亮卡片、圆角菜单和中性的深色界面。
- 编辑器自动换行、就地字号调整，运行结果区支持拖动缩放。
- 每日多时段自动化、权限说明前置，桌面小组件默认申请 2×2。
- 打开应用自动检查正式更新并提醒，也可从设置手动检查。
- 保留上游版权、MIT 与第三方许可；移除私人业务脚本。

## 0.2.2 发布验证

- [云端构建与检查](https://github.com/awesome-sean/scriptable-android/actions/runs/45037603617)：125 项运行时检查、全部浏览器检查、原生回归测试、Debug/Release 构建及 lint 通过。
- APK 界面资源与源码逐项核对；正式包在本地使用固定密钥签名，APK Signature Scheme v2/v3 验证通过。
- 私钥仅在本地保管，GitHub Actions 只生成未签名包。
- APK SHA-256：`0c0876ec6bcd4b05dea920a5c96827a1bf079f144ead249f04371bda8d5fea82`。
