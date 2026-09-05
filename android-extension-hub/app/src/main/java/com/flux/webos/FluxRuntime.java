package com.flux.webos;

import android.content.Context;

import org.mozilla.geckoview.GeckoRuntime;

public final class FluxRuntime {
    private static GeckoRuntime runtime;

    private FluxRuntime() {}

    public static synchronized GeckoRuntime get(Context context) {
        if (runtime == null) {
            runtime = GeckoRuntime.create(context.getApplicationContext());
        }
        return runtime;
    }
}
