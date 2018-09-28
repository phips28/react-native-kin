package com.kin.reactnative;

import android.app.Application;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.WritableMap;

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
            WritableMap map = Arguments.createMap();

//            map.putDouble("relativeX", PixelUtil.toDIPFromPixel(mMeasureBuffer[0]));
//            map.putDouble("relativeY", PixelUtil.toDIPFromPixel(mMeasureBuffer[1]));
//            map.putDouble("width", PixelUtil.toDIPFromPixel(mMeasureBuffer[2]));
//            map.putDouble("height", PixelUtil.toDIPFromPixel(mMeasureBuffer[3]));

            promise.resolve(map);
        } catch (Exception e) {
            promise.reject(e);
        }
    }
    @ReactMethod
    public void decrement(
            WritableMap options,
            Promise promise) {
        Log.d('print WritableMap options:');
        Log.d(options);
        try {
            WritableMap map = Arguments.createMap();

//            map.putDouble("relativeX", PixelUtil.toDIPFromPixel(mMeasureBuffer[0]));
//            map.putDouble("relativeY", PixelUtil.toDIPFromPixel(mMeasureBuffer[1]));
//            map.putDouble("width", PixelUtil.toDIPFromPixel(mMeasureBuffer[2]));
//            map.putDouble("height", PixelUtil.toDIPFromPixel(mMeasureBuffer[3]));

            promise.resolve(map);
        } catch (Exception e) {
            promise.reject(e);
        }
    }
}


