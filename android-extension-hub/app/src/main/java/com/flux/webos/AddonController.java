package com.flux.webos;

import org.mozilla.geckoview.GeckoRuntime;
import org.mozilla.geckoview.WebExtension;
import org.mozilla.geckoview.WebExtensionController;

import java.util.List;

/**
 * Thin wrapper around GeckoView's persistent add-on manager.
 * Normal installs must be Mozilla-signed XPI/WebExtensions.
 */
public final class AddonController {
    private AddonController() {}

    public interface ListCallback {
        void onSuccess(List<WebExtension> extensions);
        void onError(Throwable error);
    }

    public interface ExtensionCallback {
        void onSuccess(WebExtension extension);
        void onError(Throwable error);
    }

    public interface VoidCallback {
        void onSuccess();
        void onError(Throwable error);
    }

    private static WebExtensionController controller(GeckoRuntime runtime) {
        return runtime.getWebExtensionController();
    }

    public static void list(GeckoRuntime runtime, ListCallback callback) {
        controller(runtime).list().accept(callback::onSuccess, callback::onError);
    }

    public static void installSigned(GeckoRuntime runtime, String uri, ExtensionCallback callback) {
        controller(runtime).install(uri).accept(callback::onSuccess, callback::onError);
    }

    public static void enable(GeckoRuntime runtime, WebExtension extension, ExtensionCallback callback) {
        controller(runtime)
                .enable(extension, WebExtensionController.EnableSource.USER)
                .accept(callback::onSuccess, callback::onError);
    }

    public static void disable(GeckoRuntime runtime, WebExtension extension, ExtensionCallback callback) {
        controller(runtime)
                .disable(extension, WebExtensionController.EnableSource.USER)
                .accept(callback::onSuccess, callback::onError);
    }

    public static void uninstall(GeckoRuntime runtime, WebExtension extension, VoidCallback callback) {
        controller(runtime).uninstall(extension).accept(
                ignored -> callback.onSuccess(),
                callback::onError
        );
    }
}
