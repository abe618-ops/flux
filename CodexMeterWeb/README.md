# CodexMeter Web

一个简单的 Codex 浏览器用量面板。

## 使用方式

1. Chrome / Edge 打开“扩展程序”。
2. 开启“开发者模式”。
3. 点击“加载已解压的扩展程序”。
4. 选择 CodexMeterWeb 文件夹。
5. 打开并登录 https://chatgpt.com/ 。
6. 首次安装后刷新一次 ChatGPT 页面。
7. 点击浏览器右上角 CodexMeter 图标。

## 显示内容

- 5 小时额度剩余 / 已用 / 重置倒计时
- Weekly 剩余 / 已用 / 重置倒计时
- 今日、7天、30天 Token
- uncached input / cached input / output
- 模型 Token 分布
- Credits（账号返回时）
- 数据源诊断

读不到的字段显示 -- / N/A，不使用 Demo 数据。

## 数据来源

当前浏览器已登录的 ChatGPT 页面：
- /backend-api/wham/usage
- /backend-api/wham/analytics/daily-workspace-usage-counts
- /backend-api/wham/usage/daily-workspace-user-token-usage-breakdown
- /backend-api/wham/usage/daily-token-usage-breakdown

这些属于 ChatGPT 网页内部接口，不是稳定公开 API，因此网页改版后可能需要更新。

## 隐私

扩展不保存 ChatGPT 密码、Cookie 或 access token，也不把 usage 数据上传第三方服务器。
