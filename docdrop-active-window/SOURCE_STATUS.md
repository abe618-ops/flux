# 源码状态 / Source Code Status

## 中文

当前发布材料中只有 `DocDrop_Active_Window.dmg` 成品。

对该 DMG 的检查确认其中包含可运行的 macOS 应用及其资源、元数据和编译后的 Mach-O / Swift 二进制，但**没有发现原始 Xcode 工程、Swift 源文件或可作为原始工程发布的源码树**。

因此：

1. 本仓库不把反编译结果、字符串提取结果或推测性重建代码标注成“原始源码”。
2. `src/` 目录仅作为真实源码未来补充的位置。
3. `tools/` 中的脚本属于本仓库的发布/重建辅助代码，不是 DocDrop 应用本体源码。
4. 如果后续获得真实工程，建议同时补充：
   - Xcode 工程或 Swift Package；
   - 构建所需 macOS / Xcode 版本；
   - 依赖和许可证；
   - 签名、公证与发布流程；
   - 可复现构建说明；
   - 对源码适用的明确许可证。

## English

The currently supplied release material contains only the finished `DocDrop_Active_Window.dmg`.

Inspection confirms that the DMG contains a runnable macOS application, resources, metadata, and compiled Mach-O / Swift binaries, but **does not contain the original Xcode project, Swift source files, or an authentic source tree suitable for publication as the application's original source**.

Accordingly:

1. Decompiled output, extracted strings, and inferred/reconstructed code are not presented as original source.
2. `src/` is reserved for authentic source code if it becomes available later.
3. Scripts under `tools/` are repository publishing/reconstruction utilities, not the DocDrop application source.
4. If the original project is provided later, it should ideally be accompanied by build requirements, dependency/license information, signing/notarization notes, reproducible-build instructions, and an explicit source-code license.
