package com.flux.webos;

import android.util.Log;

import org.mozilla.geckoview.GeckoRuntime;

public final class ExtensionManager {
    private static final String TAG = "FluxExtensions";
    private static final String BRIDGE_URI = "resource://android/assets/extensions/flux_bridge/";
    private static final String BRIDGE_ID = "flux-bridge@flux.local";

    private ExtensionManager() {}

    public static void ensureBuiltIns(GeckoRuntime runtime) {
        runtime.getWebExtensionController()
                .ensureBuiltIn(BRIDGE_URI, BRIDGE_ID)
                .accept(
                        extension -> Log.i(TAG, "Built-in extension ready: " + extension),
                        error -> Log.e(TAG, "Failed to load built-in extension", error)
                );
    }
}
