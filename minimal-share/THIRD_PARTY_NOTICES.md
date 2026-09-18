# Third-Party Notices / 第三方说明

## 中文

JShare / 极简分享 v0.1.0 的 Android 核心实现为独立重新编写。

当前仓库中的 JShare 代码 **未直接复制、修改、嵌入或链接** 下列项目的源代码或二进制代码，因此下列项目的许可证并不改变 JShare 自身的 MIT License。

开发过程中参考了这些公开项目的产品设计、公开文档以及局域网文件分享领域的通用实现思路：

| Project | Repository | License | How it relates to JShare |
|---|---|---|---|
| LocalSend | https://github.com/localsend/localsend | Apache-2.0 | 产品设计、局域网发现与直传思路参考；未复制其源码 |
| Sharik | https://github.com/marchellodev/sharik | MIT | 极简交互与本地分享体验参考；未复制其源码 |
| PairDrop | https://github.com/schlagmichdoch/PairDrop | GPL-3.0 | 分享入口和跨设备体验参考；未复制其源码 |
| KDE Connect Android | https://github.com/KDE/kdeconnect-android | GPL-2.0 / GPL-3.0 | 设备发现、配对和跨设备通信产品思路参考；未复制其源码 |

JShare 当前使用的是自己的端口、自己的发现消息格式和自己的文件传输格式，不声明与上述项目协议兼容。

如果未来版本实际引入第三方源码、库、协议实现或其他需要归属声明的内容，应在合并代码的同时更新本文件，并按照相应开源许可证履行保留版权、NOTICE、源码开放或其他义务。

## English

The Android core of JShare v0.1.0 was implemented independently.

The current JShare source tree **does not copy, modify, embed, or link source/binary code** from the projects listed below. Their licenses therefore do not replace or modify JShare's own MIT License.

The projects were studied only as public references for product design, documentation, and common local file-sharing concepts:

| Project | Repository | License | Relationship to JShare |
|---|---|---|---|
| LocalSend | https://github.com/localsend/localsend | Apache-2.0 | Product UX and LAN-discovery/direct-transfer concepts; no source copied |
| Sharik | https://github.com/marchellodev/sharik | MIT | Minimal sharing UX reference; no source copied |
| PairDrop | https://github.com/schlagmichdoch/PairDrop | GPL-3.0 | Share-entry and cross-device UX reference; no source copied |
| KDE Connect Android | https://github.com/KDE/kdeconnect-android | GPL-2.0 / GPL-3.0 | Device discovery/pairing product concepts; no source copied |

The current JShare release uses its own ports, discovery message format, and transfer format, and does not claim protocol compatibility with those projects.

If a future version incorporates third-party source code, libraries, protocol implementations, or other materials requiring attribution, this file must be updated at the same time and all corresponding license obligations must be followed.
