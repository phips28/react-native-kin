package com.kin.reactnative;

import android.widget.Toast
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise

class RNKinModule(reactContext: ReactApplicationContext, application: android.app.Application) : ReactContextBaseJavaModule(reactContext) {

    override fun getName(): String {
        return "RNKin"
    }

    override fun getConstants(): kotlin.collections.Map<String, Any> {
        val constants: Map<String, Any> = mapOf(
                Pair("ENVIRONMENT_PLAYGROUND", "playground"),
                Pair("ENVIRONMENT_PRODUCTION", "production")
        );
        return constants
    }

    @ReactMethod
    fun show(message: String, duration: Int, promise: Promise) {
        Toast.makeText(reactApplicationContext, message, duration).show()
        promise.resolve(true)
    }
}
