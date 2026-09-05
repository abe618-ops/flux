# Third-party notices

Flux WebOS is designed to keep its own Android shell code separate from third-party browser engines and libraries.

## GeckoView / Mozilla Gecko

The current prototype embeds Mozilla GeckoView as a Gradle dependency. Mozilla code is generally distributed under the Mozilla Public License 2.0 (MPL-2.0); individual bundled third-party components can have their own compatible licenses. Before a production release, generate and ship the complete notices corresponding to the exact GeckoView binary version used.

Project: https://mozilla.github.io/geckoview/
License information: https://www.mozilla.org/MPL/2.0/

## Chromium / Kiwi Browser references

Chromium and Kiwi Browser are architecture/reference research targets for a future optional Chromium compatibility engine. Their source code is not copied into the current GeckoView prototype.

Chromium: https://www.chromium.org/
Kiwi Browser source: https://github.com/kiwibrowser/src

If a future branch incorporates Chromium/Kiwi source, preserve all applicable BSD notices, third-party licenses and required attribution, and do not reuse Google Chrome or Kiwi trademarks/branding as Flux branding.
