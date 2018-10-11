package com.kin.reactnative;

import android.app.Application;
import android.util.Log;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.WritableMap;
import com.facebook.react.modules.core.DeviceEventManagerModule;
import com.kin.ecosystem.Environment;
import com.kin.ecosystem.common.KinEnvironment;

import java.util.HashMap;
import java.util.Map;

// import com.kin.ecosystem.Environment;
// import com.kin.ecosystem.Kin;
// import com.kin.ecosystem.common.KinEnvironment;
// import com.kin.ecosystem.common.exception.BlockchainException;
// import com.kin.ecosystem.common.exception.ClientException;
// import com.kin.ecosystem.common.model.WhitelistData;

public class RNKinModuleOld extends ReactContextBaseJavaModule {

    private final ReactApplicationContext reactContext;
    private Application application;
    private int count = 0;

    private String apiKey;
    private String appId;
    private String privateKey;
    private String keyPairIdentifier;
    private Boolean useJWT;
    private String jwtServiceUrl;

    private String loggedInUserId;
    private String loggedInUsername;
    private Boolean isOnboarded_;


    public RNKinModuleOld(ReactApplicationContext reactContext, Application application) {
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
        constants.put("ENVIRONMENT_PLAYGROUND", "playground");
        constants.put("ENVIRONMENT_PRODUCTION", "production");
        return constants;
    }

    private void sendEvent(ReactContext reactContext,
                           String eventName,
                           WritableMap params) {
        reactContext
                .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter.class)
                .emit(eventName, params);
    }

    @ReactMethod
    public void increment(
            final Promise promise) {
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
            final Promise promise
    ) {
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

    private void printCredentials() {
        HashMap<String, Object> credentials = new HashMap<String, Object>();
        credentials.put("apiKey", this.apiKey);
        credentials.put("appId", this.appId);
        credentials.put("privateKey", this.privateKey);
        credentials.put("keyPairIdentifier", this.keyPairIdentifier);
        credentials.put("useJWT", this.useJWT);
        credentials.put("jwtServiceUrl", this.jwtServiceUrl);

        Log.d(getName(), "credentials = " + credentials);
    }

    /**
     * check if credentials are correct
     * <p>
     * - Returns: false if not correct; true if correct
     */
    private void checkCredentials() {
        if (this.apiKey == null || this.appId == null) {
            throw new Error("apiKey and appId must not be empty");
        }
        if (this.useJWT && ((this.privateKey == null || this.keyPairIdentifier == null) && this.jwtServiceUrl == null)) {
            throw new Error("privateKey and keyPairIdentifier must not be empty when useJWT is true OR set jwtServiceUrl");
        }
    }

    /**
     * Set credentials and initialize Object
     * <p>
     * - Parameters: options {
     * apiKey: String
     * appId: String
     * privateKey: String?
     * keyPairIdentifier: String?
     * useJWT: Bool?
     * jwtServiceUrl: String?
     * }
     * <p>
     * - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    public void setCredentials(
            final ReadableMap options,
            final Promise promise
    ) {
        try {
            if (options.hasKey("apiKey")) {
                this.apiKey = options.getString("apiKey");
            }
            if (options.hasKey("appId")) {
                this.appId = options.getString("appId");
            }
            if (options.hasKey("privateKey")) {
                this.privateKey = options.getString("privateKey");
            }
            if (options.hasKey("keyPairIdentifier")) {
                this.keyPairIdentifier = options.getString("keyPairIdentifier");
            }
            if (options.hasKey("useJWT")) {
                this.useJWT = options.getBoolean("useJWT");
            }
            if (options.hasKey("jwtServiceUrl")) {
                this.jwtServiceUrl = options.getString("jwtServiceUrl");
            }

            this.printCredentials();

            this.checkCredentials();

            promise.resolve(true);
        } catch (Exception e) {
            promise.reject(e);
        }
    }

    private KinEnvironment getEnvironment(String environment) {
        switch (environment) {
            case "playground":
                return Environment.getPlayground();
            case "production":
                return Environment.getProduction();
            default:
                return Environment.getPlayground();
        }
    }

    //private signJWT(
    //        final ReadableMap options,
    //        final Promise promise
    //    ) {
    //    if (this.jwtServiceUrl == null) {
    //        throw
    //    }
//
    //    if self.jwtServiceUrl == nil {
    //        guard let encoded = JWTUtil.encode(header: ["alg": "RS512",
    //                "typ": "jwt",
    //                "kid" : self.keyPairIdentifier!],
    //        body: parameters["payload"] as! [AnyHashable : Any],
    //        subject: parameters["subject"] as! String,
    //                id: self.appId!,
    //                privateKey: self.privateKey!
    //            ) else {
    //            print("encode went wrong")
    //            completion(NSError(domain: "JWT encode went wrong", code: 500, userInfo: nil), nil)
    //            return
    //        }
    //        completion(nil, encoded)
    //        return;
    //    }
//
    //    // use servive url and sign JWT on server
    //    Alamofire.request(
    //            "\(self.jwtServiceUrl!)/sign",
    //            method: .post,
    //            parameters: parameters,
    //            encoding: JSONEncoding.default
    //        )
    //        .validate()
    //                .responseJSON { response in
    //        guard response.result.isSuccess else {
    //            print("Error while signing JWT: \(String(describing: response.result.error))")
    //            if response.data != nil {
    //                do {
    //                    var json = try JSONSerialization.jsonObject(with: response.data!, options: []) as? [String: Any]
    //                    json?["alamofireError"] = String(describing: response.result.error)
    //                    completion(NSError(domain: "Error while signing JWT", code: response.response?.statusCode ?? 500, userInfo: json), nil)
    //                    return
    //                } catch {
    //                    // do nothing
    //                    print(error)
    //                }
    //            }
//
    //            completion(NSError(domain: "Error while signing JWT: \(String(describing: response.result.error))", code: response.response?.statusCode ?? 500, userInfo: nil), nil)
    //            return
    //        }
//
    //        guard let value = response.result.value as? [String: Any],
    //        let jwt = value["jwt"] as? String else {
    //            print("JWT not received from sign service")
    //            completion(NSError(domain: "JWT not received from sign service", code: 500, userInfo: nil), nil)
    //            return
    //        }
//
    //        completion(nil, jwt)
    //    }
    //}
//
    //private void loginWithJWT(
    //        String userId,
    //        KinEnvironment: environment,
    //        completion: @escaping (Error?)
    //    ) {
//
    //    let parameters: Parameters = [
    //    "subject": "register",
    //            "payload": [
    //    "user_id": userId
    //        ]
    //    ]
//
    //    self.signJWT(parameters) { (error, jwt) in
    //        if error != nil {
    //            completion(error) // there was an error fetching JWT
    //            return
    //        }
    //        do {
    //            try Kin.shared.start(userId: userId, jwt: jwt, environment: environment)
    //            completion(nil)
    //        } catch {
    //            print("Kin.start: \(error)")
    //            completion(error)
    //        }
    //    }
    //}

    /**
     * - Returns: resolve(Bool) if Kin SDK is started and onboarded; never rejects
     */
    @ReactMethod
    public void isOnboarded(
            final Promise promise
    ) {
        promise.resolve(this.isOnboarded_);
    }
}


