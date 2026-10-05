# 微信图文转AI v2.0 验证说明

核对日期：2026-10-05。

## 构建与独立安装

- Android Java 源码通过正常 javac + D8 编译，无手工 DEX 壳。
- 包名：`com.local.wechataiarchive`；显示名：微信图文转AI。
- versionCode 20000 / versionName 2.0.0；minSdk 23 / targetSdk 35。
- zipalign 校验通过；APK v1、v2、v3 签名校验通过。
- 新签名 SHA-256：`1b6bcf6a5b9544a70236114d5466cbd884fafd99353e4850ecc2c4ef7d1ef430`。
- 旧 Web-Archive v1.2 签名 SHA-256：`82e3d21c04c3801becb3aa894e86b1731cfc283035a7af5f21b289d902f3391a`。
- Android 15 / API 35 模拟器同时安装新旧两包成功，系统包列表包含两者。
- 启动记录进入 resumed 状态，检查对应 AndroidRuntime 日志未发现应用崩溃。

## 转换与文件

- 链接识别与发现测试：7 项通过。
- Chromium 执行完整页面脚本：微信分享链接 → 读取正文 → 导出接口 → PDF 分享；通过。
- DOM 语义检查：行内图片、粗体、链接、段落、列表、引用、Markdown 表格均保留，脚本不保留。
- HTML 分享中的图片 URL 不再被当作文章链接抓取。
- 页面 PDF、MD、TXT、原图、保存按钮实际触发正确接口；无 JavaScript 页面异常。
- Java 合集图片引用测试通过，验证两个不同占位符不会因替换顺序合并成同一张图。
- Android 原生 PdfDocument、位图处理、ZIP／EPUB 与文件写入测试通过；最终版本以 4 张图片验证最多 3 个请求的滑动窗口也通过。
- 原生测试生成带中文正文及 900×3600 长图的 4 页 A4 PDF，646947 字节；Poppler 渲染检查及文字提取通过。
- 提取中文字含少量兼容汉字部件，可通过 NFKC 归一化；MD／TXT 保持原始 UTF-8 正文。
- 长图第 1—12 段保留，按页面宽度分页，图片后的正文保留。
- MD 引用同目录 `image_001.jpg`；ZIP 完整性检查通过。
- HTML 包含 JPEG Base64 data URI，不依赖远程图片。
- EPUB 的 container.xml / content.opf / chapter.xhtml / nav.xhtml 均通过 XML 解析，mimetype 为首个且未压缩的条目。

## 附件权限

- 本应用内容提供者可以读出实际 PDF 头 `%PDF`。
- 写方式打开附件被拒绝。
- 在独立测试包进程中：获授 PDF 只读 URI 权限后成功读出 `%PDF`；没有获得授权的 MD 返回 SecurityException。
- 生产分享代码使用真实文件的 EXTRA_STREAM、ClipData 和 FLAG_GRANT_READ_URI_PERMISSION。

## 适用边界

- 没有连接用户真实 Android 16 手机，未验证用户已安装的微信、ChatGPT、Claude 版本的分享菜单／最终附件导入。
- 页面展示已通过 Chromium 功能及视觉检查；软件模拟器的显示输出存在限制，不将模拟器黑屏截图作为 UI 正常显示的证据。
- PDF 为简化排版，保留正文与静态图片，不复刻原网站全部 CSS、动画或视频。
- 受限微信页面需在原应用中验证后复制可见内容；本应用不带微信登录态。
- 图较多时会按设备内存限制压缩并提示未下载图片，建议分批导出。
- 转换期间保持页面打开；没有实现系统级持续后台下载。
