# DocDrop / 文档快投

DocDrop is a macOS menu-bar utility that identifies the document in the active Word, WPS, Excel, PowerPoint, Pages, or similar document window, then lets you copy or drag that file into another app such as WeChat or an AI client.

文档快投是一个 macOS 菜单栏工具。它尝试识别当前正在查看的 Word、WPS、Excel、PowerPoint、Pages 等文档，并提供复制文件、复制路径、Finder 定位和拖拽到微信或 AI 客户端的功能。

## First release / 首版

This first public release is experimental. The active-window path is preferred. When an application does not expose the active tab's file URL, DocDrop may report that it cannot identify the document instead of choosing another tab's file.

首版为实验版本。程序优先读取当前窗口和焦点元素的文件路径。如果 WPS 等应用不提供当前标签的文件 URL，程序会提示无法识别，避免误选其他标签中的同名文件。

## Installation / 安装

1. Download DocDrop_Active_Window.dmg from the repository's Releases or project files.
2. Open the DMG and drag DocDrop.app to Applications.
3. Launch DocDrop.
4. Grant Accessibility permission in System Settings > Privacy & Security > Accessibility.
5. When macOS asks whether DocDrop may control WPS/Word, allow it if you want document-path detection.

1. 下载项目中的 DocDrop_Active_Window.dmg。
2. 打开 DMG，将 DocDrop.app 拖到“应用程序”。
3. 启动文档快投。
4. 在“系统设置 → 隐私与安全性 → 辅助功能”中允许文档快投。
5. 若 macOS 询问是否允许文档快投控制 WPS/Word，请允许，以便读取文档路径。

## Use / 使用

Click the document window first, then click or drag the floating document icon. Move it into the WeChat conversation input area or another application that accepts file drops. If multiple same-named documents are open, close the extra copies before retrying.

先点击正在查看的文档窗口，再点击或拖动悬浮文档图标，将图标拖到微信聊天输入区域或其他支持文件拖放的应用中。如果打开了多个同名文档，请先关闭多余标签后重试。

## Build from source / 从源码构建

Requirements: macOS 12 or later and Apple's Swift command-line tools.

要求：macOS 12 或更高版本，以及 Apple Swift 命令行工具。

    ./build.sh

The build script compiles the native Swift app, signs it ad hoc, and installs it under /Applications or ~/Applications.

## Privacy and permissions / 隐私与权限

DocDrop reads local window metadata and document paths only to locate a file selected by the user. It does not upload document contents. Accessibility and Automation permissions are controlled by macOS and can be revoked at any time.

文档快投仅为定位用户选择的本地文件读取窗口元数据和文档路径，不上传文档内容。辅助功能和自动化权限由 macOS 管理，用户可随时撤销。

## Known limitations / 已知限制

- Some WPS versions expose the process's open files but not the active tab's file URL.
- Unsaved documents and cloud-only documents may not have a local path.
- The app is ad hoc signed; macOS may show a security warning on first launch.
- Drag-and-drop support depends on the receiving application's accepted pasteboard formats.

## Disclaimer / 声明

This software is provided for personal productivity and testing. It is not affiliated with Apple, Microsoft, Kingsoft/WPS, Tencent/WeChat, or any AI service. Use it at your own risk. The author is not responsible for data loss, incorrect file selection, permission changes, or third-party application behavior.

本软件用于个人效率和测试，不隶属于 Apple、Microsoft、金山/WPS、腾讯/微信或任何 AI 服务。使用者应自行承担使用风险。作者不对数据丢失、文件误选、系统权限变化或第三方应用行为承担责任。

## License / 许可

MIT License. See LICENSE.
