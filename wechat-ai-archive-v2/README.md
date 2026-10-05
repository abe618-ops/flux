# 微信图文转AI v2.0

由旧版 Web-Archive v1.2 源码改造的原生 Android 应用。包名 `com.local.wechataiarchive`，使用新的固定签名，可与 `com.local.webarchive` 旧版并存。Android 6.0 及以上可安装。

## 下载

- [下载 Android APK](https://github.com/abe618-ops/flux/raw/refs/heads/main/dist/Wechat-AI-Archive-v2.0.apk)
- [下载公开源码 ZIP](https://github.com/abe618-ops/flux/raw/refs/heads/main/dist/Wechat-AI-Archive-v2.0-source-public.zip)
- [查看验证说明](Verification-CN.md)

这是独立安装版本，使用新包名和新签名，可与旧版并存。

## 用法

1. 微信打开文章，选择分享或复制链接。若微信分享菜单支持本应用，直接选“微信图文转AI”；否则打开本应用粘贴链接。
2. 应用读取公开正文并在手机上生成文件。默认从分享入口进入时完成后弹出 PDF 分享菜单。
3. 分享菜单中选 ChatGPT、Claude 或其他支持附件的应用。接收方不出现在菜单中时，保存到下载，再在 AI 对话里用附件按钮添加文件。

转换和网络图片下载需保持页面打开，进度会显示；退出或系统回收时任务会中止，可重新操作。已经生成的文件保存在本应用中，“最近生成”可重新分享。文件不会自动上传到任何 AI；用户在分享菜单中选择接收方后，由接收应用完成导入。

## 输出

| 文件 | 图片处理 | 使用方法 |
| --- | --- | --- |
| article.pdf | 成功下载的图片内嵌，长图分页 | 单文件图文归档／发送给 Claude 或支持 PDF 的 AI |
| article.md | 本地相对图片引用 | 分析正文；看图时一起导入原图 |
| article.txt | 与 MD 对应的正文和图号 | MD 不被接收应用识别时的兼容附件 |
| article.zip | MD、原图、PDF、HTML、EPUB、元数据 | 搬移／解压，不保证 AI 可直接解压读取 |
| article.html | 图片以 data URI 内嵌 | 离线打开单个图文文件 |
| article.epub | 图片打包进书内 | 阅读器打开 |

图片统一转为 JPEG，保留静态内容并做尺寸压缩，透明背景转为白色；GIF 使用首帧。失败的图片会注明，MD 保留原链接以便重新核查；视频和需要登录的页面不能从公开网页恢复。

“正文＋图片”分两步：先将 TXT/MD 正文发送到 AI，再回到应用按组发送原图（每组最多 8 张），在同一个 AI 对话内添加。按钮记录的是已选择的组，分享取消时可点“原图从第一组重新发送”。

## 下载与存储

Android 10+ 点“保存到下载”后通过 MediaStore 写入 `Download/微信图文转AI/导出编号/`，其中包含全部格式和原图。Android 6—9 使用系统文件选择器保存 ZIP，不申请全盘文件权限。最近 30 次完整生成记录可在首页显示。

## 转换修复

- 修复旧核心的行内图片、粗体、链接丢失。
- 保留 div/section 的段落边界，输出 Markdown 表格。
- 通过 XHTML 序列化生成合法 EPUB 正文。
- 修复合集图片索引逐次替换造成的冲突。
- PDF 保留文章前后文、来源地址、图片编号与页码，长图按幅面宽度分页。
- 自定义只读 ContentProvider 提供临时 `content://` 附件读取权限，禁止写入和任意路径访问。
- 实际附件分享使用 EXTRA_STREAM、ClipData 及临时只读 URI 授权，原图使用 ACTION_SEND_MULTIPLE。

## 构建

需要 JDK（包含 jdk.compiler）、Android SDK platform 35 与 build-tools 35.0.0、zip。设置 `ANDROID_HOME` 后，在本项目目录运行：

```bash
npm test
npm run build
```

也可设置 `AI_ARCHIVE_BUILD_TOOLS` 与 `AI_ARCHIVE_ANDROID_JAR` 指向对应目录和 android.jar。输出 `Wechat-AI-Archive-v2.0.apk`。

公开仓库和公开源码 ZIP 均不包含签名密钥。发布的 APK 使用本版独立固定签名，与旧 Web-Archive 不同。构建脚本在 `signing/wechat-ai-archive-v2.keystore` 不存在时生成本地密钥；自行编译的 APK 会使用自己的签名，不能直接覆盖这里发布的 APK。后续正式更新需在私人构建环境中使用原密钥；请勿把 `signing/` 上传到公开仓库。

## 已核对的官方参考（2026-10-05）

- Android 文件分享：https://developer.android.com/develop/ui/compose/sharing/send
- ChatGPT 文件上传：https://help.openai.com/en/articles/8555545-file-uploads-faq
- Claude 文件上传：https://support.claude.com/en/articles/8241126-upload-files-to-claude

官方说明：ChatGPT Enterprise 支持 PDF 视觉检索，其他套餐可能只提取文档文字而忽略内嵌图；请用单独图像附件。Claude 对不超过 100 页的 PDF 支持图文处理，较长 PDF 主要处理文字。接收应用的外部分享支持、上传数量和文件大小限制由对应应用决定。
