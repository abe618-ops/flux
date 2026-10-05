# 微信文章文件导入 AI 的实现与兼容性

## 项目来源

本版直接修改用户原 Web-Archive-v1.2-source.zip。已搜索 GitHub 账户 abe618-ops 可见仓库，并查看 flux/main 的完整目录树；本次检索未在该树中定位到 Web-Archive / wechat-export 对应目录，因此采用已找回的完整原源码进行修改。

## 文件比链接更适合导入

微信分享的链接仍依赖接收端访问原网站。将可公开读取的正文和图片下载后生成文件，AI 可按附件读取，从而减少链接验证、动态页面、图片防盗链等因素的影响。本版未接入 AI 的付费 API，也不需要 API 密钥；通过 Android 系统文件分享交给用户选择的接收应用。

## Markdown 的图文关系

Markdown 使用相对路径引用 JPEG：`![图 1](image_001.jpg)`。只发送 MD 本身无法带走图片。本版以 ZIP 保存同目录正文和图片，支持用户解压后搬移，但不会假定 AI 可以直接解压 ZIP。没有将图片的 Base64 数据直接塞进 MD，因为这既不保证接收端渲染，也会把很大的二进制文字送入文本处理链。

单文件图文格式采用 PDF 和内嵌图片的 HTML。对于 AI 的看图能力，依照接收平台的官方文档设置说明：ChatGPT 普通套餐可额外单独添加原图，Claude 超过 100 页的 PDF 宜拆分后再导入。

## 原生文件分享

只读内容提供者限制为本应用生成目录中的固定文件名。Intent 携带真实 EXTRA_STREAM 附件和 ClipData，同时设置 FLAG_GRANT_READ_URI_PERMISSION。PDF、MD/TXT、ZIP 和原图分别发送，避免把多个 MIME 格式混在同一个 ACTION_SEND_MULTIPLE 中造成接收歧义。

原图统一静态 JPEG。先发送正文，再按最多 8 张一组发送图片。接收应用是否支持外部文件入口由接收方决定；未出现于系统菜单时，下载后在 AI 中手动添加附件。

## 可靠性

- 网络只发起公开 HTTPS GET；校验每次跳转，阻止本机、局域网和私有地址。
- HTML 在本地 DOM 中解析，移除脚本、表单和交互控件，不加载原网站脚本。
- 最多 3 个并发图片请求，通过滑动窗口限制预下载的内存，限制单图 8 MB、全次图片最多 12 MB（小内存设备按可用堆内存进一步限制）、120 张图片、50 篇文章。
- 图片失败保留来源并出提示；整个转换未完成时不进入“最近生成”。
- 生成过程中需要保持应用页面打开；离开导致活动销毁时任务中止，重试即可，已完成文件仍可重复分享。
- 只申请 INTERNET，不申请全盘存储或无障碍权限。

## 官方资料

核对日期：2026-10-05。

- https://developer.android.com/develop/ui/compose/sharing/send
- https://help.openai.com/en/articles/8555545-file-uploads-faq
- https://support.claude.com/en/articles/8241126-upload-files-to-claude
