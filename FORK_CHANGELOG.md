# 修改记录

## 0.2.2

- 应用名称统一为 Scriptable Android；图标裁剪留白并去除紫色背景。
- 统一明亮卡片、圆角菜单和中性的深色界面。
- 编辑器自动换行、就地字号调整，运行结果区支持拖动缩放。
- 每日多时段自动化、权限说明前置，桌面小组件默认申请 2×2。
- 打开应用自动检查正式更新并提醒，也可从设置手动检查。
- 保留上游版权、MIT 与第三方许可；移除私人业务脚本。

## 发布验证

- [云端构建与检查](https://github.com/awesome-sean/scriptable-android/actions/runs/45037603617)：125 项运行时检查、全部浏览器检查、原生回归测试、Debug/Release 构建及 lint 通过。
- APK 界面资源与源码逐项核对；正式包在本地使用固定密钥签名，APK Signature Scheme v2/v3 验证通过。
- 私钥仅在本地保管，GitHub Actions 只生成未签名包。
- APK SHA-256：`0c0876ec6bcd4b05dea920a5c96827a1bf079f144ead249f04371bda8d5fea82`。
