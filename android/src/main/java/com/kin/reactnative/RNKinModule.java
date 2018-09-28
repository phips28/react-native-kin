
package com.kin.reactnative;

import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

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
            measureLayout(tag, ancestorTag, mMeasureBuffer);

            WritableMap map = Arguments.createMap();

            map.putDouble("relativeX", PixelUtil.toDIPFromPixel(mMeasureBuffer[0]));
            map.putDouble("relativeY", PixelUtil.toDIPFromPixel(mMeasureBuffer[1]));
            map.putDouble("width", PixelUtil.toDIPFromPixel(mMeasureBuffer[2]));
            map.putDouble("height", PixelUtil.toDIPFromPixel(mMeasureBuffer[3]));

            promise.resolve(map);
          } catch (IllegalViewOperationException e) {
            promise.reject(E_LAYOUT_ERROR, e);
          }
  }
  @ReactMethod
  public void decrement(
            @Nullable WritableMap options
            Promise promise) {
          try {
            WritableMap map = Arguments.createMap();

            map.putDouble("relativeX", PixelUtil.toDIPFromPixel(mMeasureBuffer[0]));
            map.putDouble("relativeY", PixelUtil.toDIPFromPixel(mMeasureBuffer[1]));
            map.putDouble("width", PixelUtil.toDIPFromPixel(mMeasureBuffer[2]));
            map.putDouble("height", PixelUtil.toDIPFromPixel(mMeasureBuffer[3]));

            promise.resolve(map);
          } catch (IllegalViewOperationException e) {
            promise.reject(E_LAYOUT_ERROR, e);
          }
  }
}


