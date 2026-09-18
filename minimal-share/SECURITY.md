# Security Policy / 安全说明

## Supported version

| Version | Supported |
|---|---|
| 0.1.x | ✅ Current test series |

## Security model

JShare is designed for direct transfers between devices on the same trusted local network.

Current protections:

- no cloud upload or account service,
- SHA-256 integrity verification after transfer,
- Android system confirmation for APK/APKS installation,
- receiving service is tied to the app's active lifecycle.

## Known limitation / 已知限制

**JShare v0.1.0 does not yet encrypt file contents with TLS.**

Therefore:

- use it on a trusted Wi-Fi network or your own mobile hotspot,
- do not transfer confidential files over an untrusted public Wi-Fi network,
- do not expose TCP port 53319 to the public Internet.

Future versions plan to add device pairing and encrypted transport.

## Reporting a vulnerability / 报告安全问题

Please open a GitHub issue without publishing sensitive exploit details. If the issue requires confidential handling, first create a minimal issue asking for a private contact channel.
