package com.kin.reactnative;

import android.app.Application;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.bridge.ReadableMap;

import java.util.HashMap;
import java.util.Map;

public class RNKinModule extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private Application application;
    private int count = 0;

    public RNKinModule(ReactApplicationContext reactContext, Application application) {
        super(reactContext);
        this.reactContext = reactContext;
        this.application = application;
    }

    @Override
    public String getName() {
        return "RNKin";
    }

    @Override
    public Map<String, Object> getConstants() {
        HashMap<String, Object> constants = new HashMap<String, Object>();
        constants.put("TEST", "Test const");
        return constants;
    }

    @ReactMethod
    public void increment(
            Promise promise) {
        try {
            this.count++;
            Log.d("KinModule", "count = " + this.count);
            WritableMap map = Arguments.createMap();
            map.putInt("count", this.count);

            promise.resolve(map);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    @ReactMethod
    public void decrement(
            final ReadableMap options,
            Promise promise) {
        try {
            this.count--;
            Log.d("KinModule", "count = " + this.count);
            Log.d("KinModule", "options = " + options);
            if (options.hasKey("ny")) {
                Log.d("KinModule", "ny = " + options.getString("ny"));
            }

            WritableMap map = Arguments.createMap();
            map.putInt("count", this.count);

            promise.resolve(map);
        } catch (Exception e) {
            promise.reject(e);
        }
    }
}


