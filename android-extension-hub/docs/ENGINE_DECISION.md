# Engine decision for Flux WebOS

## Goal

Flux WebOS is not intended to be a conventional browser fork. The product goal is a lightweight Android Web OS shell: one host APK, then PWA/WebExtension/UserScript capabilities are installed and updated as small packages.

## Decision: GeckoView first, Chromium compatibility second

### Why not Android WebView

Android WebView is small because the engine already exists on the device, but it does not provide a general browser-extension runtime. It is suitable for rendering pages, not for our primary goal of hosting WebExtensions.

### Why not fork Chromium first

Chromium itself is open source and suitable for derivative browsers, and projects such as Kiwi Browser prove that desktop-style Chrome extensions can be made to work on Android. However, this route means carrying a large browser fork and continuously rebasing extension-related patches on top of Chromium. That is a much larger maintenance burden than an Android app shell.

### Why GeckoView for v0.x

GeckoView is an embeddable full web engine. Its public Android API supports WebExtension installation, lifecycle management and built-in WebExtensions, and provides a native-messaging path between extensions and Android code. This maps directly to Flux WebOS's host + extension + Android Bridge design.

The first prototype therefore uses:

```text
Flux WebOS Android shell
        |
        +-- GeckoView web engine
        |
        +-- WebExtension runtime
        |     +-- content scripts
        |     +-- background logic
        |     +-- extension storage
        |     +-- native messaging
        |
        +-- Flux Android Bridge
        |     +-- share target
        |     +-- clipboard (planned)
        |     +-- downloads (planned)
        |     +-- notifications (planned)
        |     +-- files/intents (planned)
        |
        +-- Web App / Extension launcher
```

A full browser UI is not required. GeckoView supplies the engine; Flux supplies only the surfaces needed by the Web OS: navigation, app launcher, extension manager, permissions and Android integration.

## Chromium track

Keep a separate experimental Chromium track after the GeckoView MVP is stable. Study the architecture of Kiwi Browser and current Chromium extension support, but do not copy branding, project-specific services or proprietary assets.

The Chromium track is useful for extensions that depend tightly on Chrome-specific APIs. It should remain an optional compatibility engine rather than the initial foundation unless compatibility testing proves GeckoView insufficient for the target extension catalog.

## Open-source / licensing policy

1. Keep Flux-owned code separately licensed and documented.
2. Preserve notices and attribution for third-party components.
3. Do not ship Google Chrome proprietary branding, services, API keys or assets merely because Chromium code is open source.
4. Track each included dependency in a THIRD_PARTY_NOTICES file before release.
5. Prefer standards-based WebExtension APIs and a small Flux-specific Android capability bridge.

## Product direction

The long-term UI should look less like a browser and more like a Web OS home screen:

- Web Apps: installed PWAs and saved web apps.
- Extensions: small capability packages.
- Actions: translate, summarize, download, extract, convert, send, save, automate.
- Share-to-Flux: run actions on a URL received from any Android app.
- Workspace: tabs/tasks rather than desktop browser chrome.
- Permission Center: clearly show what each extension can access.

The browser surface becomes one system component, not the whole product.
