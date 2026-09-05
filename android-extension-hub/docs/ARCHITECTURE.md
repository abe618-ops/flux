# Architecture

## 1. Design goal

Android Extension Hub uses a single Android host application to execute lightweight extension packages. It is intentionally not a Chrome-extension-to-APK converter.

## 2. Runtime layers

### Host App
Owns Android lifecycle, navigation, settings, permissions and package management.

### Web Runtime
GeckoView is the preferred MVP runtime. It provides a maintained Android embedding layer and WebExtension integration.

### Extension Runtime
Responsible for:
- manifest discovery
- install/uninstall
- enable/disable
- content script execution
- extension storage
- compatibility checks

### Android Bridge
A capability-gated message bridge between extension code and Android APIs.

Initial capabilities:
- clipboard.read / clipboard.write
- share.open / share.receive
- download.enqueue
- notification.show
- file.pick

No capability is exposed implicitly. Extensions request capabilities and the host records user grants.

### Store / Sources
Initial sources:
- local ZIP
- GitHub release/repository URL
- raw HTTPS manifest/feed
- bundled sample extensions

Chrome Web Store support is not an MVP requirement because store delivery, package formats and extension APIs can change independently of the host runtime.

## 3. Compatibility levels

### A — Web-native
Pure content scripts, DOM manipulation, styling, page extraction and similar features.

### B — Bridged
Needs a supported Android Bridge capability such as downloading, sharing or notifications.

### C — Unsupported / experimental
Depends on desktop-only windowing, arbitrary native executables, unrestricted Native Messaging, USB/HID, background daemons or APIs unavailable in the runtime.

## 4. Mobile interaction mappings

Desktop extension patterns should map to mobile-native interactions:

| Desktop extension UX | Android mapping |
|---|---|
| toolbar popup | bottom sheet / panel |
| context menu | long-press action |
| keyboard shortcut | configurable action / Quick Settings later |
| download API | Android DownloadManager |
| browser notification | Android notification |
| file picker | Storage Access Framework |
| current tab URL | active GeckoView session or Android Share Target |

## 5. Security model

- explicit permissions before installation/enabling
- capability allowlist for native APIs
- extension origin/source shown to the user
- per-extension storage namespace
- no arbitrary shell execution
- no silent native binary loading
- source/update integrity metadata where available
- compatibility and risk status displayed in UI

## 6. MVP flow

```text
Install host APK
      ↓
Open Extension Hub
      ↓
Import ZIP / GitHub URL
      ↓
Parse manifest + compatibility check
      ↓
Show permissions/risk
      ↓
Install extension
      ↓
Enable extension
      ↓
Browse internally OR share a URL from another browser
      ↓
Run extension logic
```

## 7. Future directions

- Chromium-based experimental runtime
- signed extension catalogs
- remote extension feeds
- PWA catalog
- UserScript manager
- extension update channels
- optional sync of extension metadata/configuration
