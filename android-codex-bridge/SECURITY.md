# Security Policy

Android Codex Bridge is a device-control and diagnostic project, so security boundaries are part of the design rather than optional hardening.

## Supported use

Use the software only on Android devices you own or are explicitly authorized to test or administer.

## Default restrictions

- No lock-screen bypass.
- No credential/password extraction.
- No private application database extraction.
- No arbitrary root/shell command execution exposed to the model by default.
- No hidden background screen capture.
- No silent Accessibility enablement.
- No attempt to bypass Android sandboxing.

ADB, Accessibility, MediaProjection and Shizuku remain subject to Android/user authorization.

## Reporting issues

Please report security-sensitive problems privately to the repository owner before public disclosure when practical.

## Agent execution policy

Future MCP tools must validate inputs and expose narrow operations. Shell-style diagnostic tools should use explicit allow-lists and redact sensitive output from logs/reports where feasible.
