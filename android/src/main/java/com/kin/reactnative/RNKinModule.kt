package com.kin.reactnative;

import android.app.Application
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.kin.ecosystem.Environment
import com.kin.ecosystem.Kin
import com.kin.ecosystem.common.KinCallback
import com.kin.ecosystem.common.KinEnvironment
import com.kin.ecosystem.common.NativeOfferClickEvent
import com.kin.ecosystem.common.Observer
import com.kin.ecosystem.common.exception.KinEcosystemException
import com.kin.ecosystem.common.model.Balance
import com.kin.ecosystem.common.model.NativeSpendOffer
import com.kin.ecosystem.common.model.OrderConfirmation
import com.kin.ecosystem.common.model.WhitelistData
import khttp.post
import khttp.responses.Response
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.util.*


class RNKinModule(reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var reactContext: ReactApplicationContext = reactContext;
    private var nativeSpendOfferClickedObserver: Observer<NativeOfferClickEvent>? = null
    private var balanceObserver: Observer<Balance>? = null

    private var apiKey: String? = null
    private var appId: String? = null
    private var privateKey: String? = null
    private var keyPairIdentifier: String? = null
    private var useJWT: Boolean = false
    private var jwtServiceUrl: String? = null
    private var debug: Boolean = false

    private var loggedInUserId: String? = null
    private var loggedInUsername: String? = null
    private var isOnboarded_: Boolean = false

    override fun getName(): String {
        return "RNKin"
    }

    override fun getConstants(): kotlin.collections.Map<String, Any> {
        val constants: Map<String, Any> = mapOf(
                "ENVIRONMENT_PLAYGROUND" to "playground",
                "ENVIRONMENT_PRODUCTION" to "production"
        );
        return constants
    }

    private fun sendEvent(
            name: String,
            params: Any?
    ) {
        println("send event: $params")
        try {
            this.reactContext
                    .getJSModule(DeviceEventManagerModule.RCTDeviceEventEmitter::class.java)
                    .emit(name, params)
        } catch (exception: Exception) {
            exception.printStackTrace()
        }
    }

    private fun printCredentials() {
        if (!this.debug) {
            return
        }
        val credentials: Map<String, Any?> = mapOf(
                "apiKey" to this.apiKey,
                "appId" to this.appId,
                "privateKey" to this.privateKey,
                "keyPairIdentifier" to this.keyPairIdentifier,
                "useJWT" to this.useJWT,
                "jwtServiceUrl" to this.jwtServiceUrl,
                "debug" to this.debug
        )

        println("credentials = $credentials")
    }

    /**
     * check if credentials are correct
     *
     * - Returns: false if not correct; true if correct
     */
    private fun checkCredentials() {
        if (this.apiKey == null || this.appId == null) {
            throw Error("apiKey and appId must not be empty")
        }
        if (this.useJWT && (this.privateKey == null || this.keyPairIdentifier == null) && this.jwtServiceUrl == null) {
            throw Error("privateKey and keyPairIdentifier must not be empty when useJWT is true OR set jwtServiceUrl")
        }
    }

    /**
     * Set credentials and initialize Object
     *
     *
     * - Parameters: options {
     * apiKey: String
     * appId: String
     * privateKey: String?
     * keyPairIdentifier: String?
     * useJWT: Bool?
     * jwtServiceUrl: String?
     * }
     *
     *
     * - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun setCredentials(
            options: ReadableMap,
            promise: Promise
    ) {
        try {
            if (options.hasKey("apiKey")) {
                this.apiKey = options.getString("apiKey")
            }
            if (options.hasKey("appId")) {
                this.appId = options.getString("appId")
            }
            if (options.hasKey("privateKey")) {
                this.privateKey = options.getString("privateKey")
            }
            if (options.hasKey("keyPairIdentifier")) {
                this.keyPairIdentifier = options.getString("keyPairIdentifier")
            }
            if (options.hasKey("useJWT")) {
                this.useJWT = options.getBoolean("useJWT")
            }
            if (options.hasKey("jwtServiceUrl")) {
                this.jwtServiceUrl = options.getString("jwtServiceUrl")
            }

            if (options.hasKey("debug")) {
                this.debug = options.getBoolean("debug")
            }
            Kin.enableLogs(this.debug)

            this.printCredentials()

            this.checkCredentials()

            promise.resolve(true)
        } catch (error: Exception) {
            promise.reject(error)
        }
    }


    private fun getEnvironment(environment: String): KinEnvironment {
        when (environment) {
            "playground" -> return Environment.getPlayground()
            "production" -> return Environment.getProduction()
            else -> return Environment.getPlayground()
        }
    }

    private fun signJWT(
            parameters: Map<String, Any?>,
            completion: (Exception?, String?) -> Unit
    ) {
        if (this.jwtServiceUrl == null) {
            // TODO for now we do not support local JWT signing
            completion(Exception("local JWT signing is not supported, set a jwtServiceUrl"), null)
            return
        }

        val jwtServiceUrl = this.jwtServiceUrl

        doAsync {
            val response: Response = post(
                    url = "$jwtServiceUrl/sign",
                    json = parameters,
                    timeout = 10000.0
            )

            try {
                if (response.statusCode == 200) {
                    val value: JSONObject = response.jsonObject
                    val jwt = value["jwt"] as? String
                    if (jwt == null) {
                        print("JWT not received from sign service")
                        completion(Exception("JWT not received from sign service"), null)
                        return@doAsync
                    }
                    completion(null, jwt)
                } else {
                    val value: JSONObject = response.jsonObject
                    if (value["error"] != null) {
                        completion(Exception("JWT signing failed: ${value["error"]}"), null)
                        return@doAsync
                    }
                    completion(Exception("JWT signing failed: $value"), null)
                }
            } catch (exception: Exception) {
                completion(exception, null)
            }
        }
    }

    private fun loginWithJWT(
            userId: String,
            environment: KinEnvironment,
            completion: (Exception?) -> Unit
    ) {
        val parameters: Map<String, Any> = mapOf(
                "subject" to "register",
                "payload" to mapOf(
                        "user_id" to userId
                )
        )
        this.signJWT(parameters) { error, jwt ->
            if (error != null) {
                completion(error)
                return@signJWT
            }
            try {
                Kin.start(this.reactContext, jwt as String, environment)
                completion(null)
            } catch (exception: Exception) {
                println("Kin.start: $exception")
                completion(exception)
            }
        }
    }

    @ReactMethod
    fun isOnboarded(promise: Promise) {
        promise.resolve(this.isOnboarded_)
    }

    @ReactMethod
    fun start(
            options: ReadableMap,
            promise: Promise
    ) {
        if (!options.hasKey("userId")) {
            promise.reject(Exception("userId must not be empty"))
            return;
        }
        val userId = options.getString("userId")

        Kin.enableLogs(true)

        this.loggedInUserId = userId
        if (options.hasKey("username")) {
            this.loggedInUsername = options.getString("username")
        }
        val environment: KinEnvironment
        if (options.hasKey("environment")) {
            environment = getEnvironment(environment = options.getString("environment"))
        } else {
            environment = getEnvironment(environment = "")
        }

        if (this.useJWT) {
            this.loginWithJWT(userId, environment = environment) { error ->
                if (error != null) {
                    promise.reject(error)
                    return@loginWithJWT
                }
                println("YEAH, started 🚀")
                this.isOnboarded_ = true
                this.initEventEmitters()
                promise.resolve(true)
            }
        } else {
            try {
                /** Use {@link WhitelistData} for small scale testing */
                val whitelistData: WhitelistData = WhitelistData(userId, this.appId, this.apiKey);
                Kin.start(this.reactContext, whitelistData, environment)
                println("YEAH, started 🚀")
                this.isOnboarded_ = true
                this.initEventEmitters()
                promise.resolve(true)
            } catch (error: Exception) {
                promise.reject(error)
                return
            }
        }
    }

    @ReactMethod
    fun getWalletAddress(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        promise.resolve(Kin.getPublicAddress())
    }

    @ReactMethod
    fun getCurrentBalance(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }

        try {
            //Get Cached Balance
            try {
                val cachedBalance = Kin.getCachedBalance()
                promise.resolve(cachedBalance.amount.intValueExact())
                return
            } catch (e: Exception) {
                println(e)
                // do nothing, and getBalance()
            }

            Kin.getBalance(object : KinCallback<Balance> {
                override fun onResponse(balance: Balance) {
                    promise.resolve(balance.amount.intValueExact())
                }

                override fun onFailure(exception: KinEcosystemException) {
                    promise.reject(Error("Error fetching current balance $exception"))
                }
            })
        } catch (exception: Exception) {
            promise.reject(Error("Error fetching current balance $exception"))
        }
    }

    @ReactMethod
    fun launchMarketplace(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val currentActivity = this.reactContext.currentActivity
        println("currentActivity: " + currentActivity)
        if (currentActivity == null) {
            promise.reject(Error("currentActivity not found"))
            return
        }
        try {
            Kin.launchMarketplace(currentActivity)
        } catch (error: Error) {
            promise.reject(error)
            return
        }
        promise.resolve(true)
    }

    @ReactMethod
    fun earn(options: ReadableMap, promise: Promise) {
        val o: HashMap<String, Any?> = options.toHashMap()
        o.put("offerType", "earn")
        this.earnOrSpendOffer(o, promise = promise)
    }

    @ReactMethod
    fun spend(options: ReadableMap, promise: Promise) {
        val o: HashMap<String, Any?> = options.toHashMap()
        o.put("offerType", "spend")
        this.earnOrSpendOffer(o, promise = promise)
    }

    private fun earnOrSpendOffer(options: HashMap<String, Any?>, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }

        val offerType = options["offerType"]
        if (offerType == null) {
            promise.reject(Error("offerType must not be empty: earn or spend"))
            return
        }
        if (offerType != "earn" && offerType != "spend") {
            promise.reject(Error("offerType has invalid value '$offerType'; possible: earn or spend"))
            return
        }
        val earn = offerType == "earn"
        val offerId = options["offerId"]
        if (offerId == null) {
            promise.reject(Error("offerId must not be empty"))
            return
        }
        val offerAmount = options["offerAmount"]
        if (offerAmount == null) {
            promise.reject(Error("offerAmount must not be empty"))
            return
        }
        val offerTitle = options["offerTitle"]
        if (offerTitle == null) {
            promise.reject(Error("offerTitle must not be empty"))
            return
        }
        val offerDescription = options["offerDescription"]
        if (offerDescription == null) {
            promise.reject(Error("offerDescription must not be empty"))
            return
        }
        val recipientUserId = options["recipientUserId"]
        if (recipientUserId == null) {
            promise.reject(Error("recipientUserId must not be empty"))
            return
        }
        var recipientOrSenderKey = "sender"
        if (earn) {
            recipientOrSenderKey = "recipient"
        }
        val parameters: Map<String, Any?> = mapOf(
                "subject" to offerType,
                "payload" to mapOf(
                        "offer" to mapOf(
                                "id" to offerId,
                                "amount" to (offerAmount as Double).toInt()),
                        "$recipientOrSenderKey" to mapOf(
                                "title" to offerTitle,
                                "description" to offerDescription,
                                "user_id" to recipientUserId
                        )
                )
        )

        print(parameters)
        this.signJWT(parameters) { error, jwt ->
            if (error != null) {
                promise.reject(error)
                return@signJWT
            }

            val handler = object : KinCallback<OrderConfirmation> {
                override fun onResponse(orderConfirmation: OrderConfirmation) {
                    promise.resolve(orderConfirmation.getJwtConfirmation())
                }

                override fun onFailure(exception: KinEcosystemException) {
                    promise.reject(exception)
                }
            }

            try {
                if (earn) {
                    Kin.requestPayment(jwt, handler)
                } else {
                    Kin.purchase(jwt, handler)
                }
            } catch (exception: Exception) {
                promise.reject(exception)
            }
        }
    }

    @ReactMethod
    fun addSpendOffer(
            options: ReadableMap,
            promise: Promise
    ) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val options1: HashMap<String, Any?> = options.toHashMap()

        val offerId = options1["offerId"]
        if (offerId == null) {
            promise.reject(Error("offerId must not be empty"))
            return
        }
        val offerAmount = options1["offerAmount"]
        if (offerAmount == null) {
            promise.reject(Error("offerAmount must not be empty"))
            return
        }
        val offerTitle = options1["offerTitle"]
        if (offerTitle == null) {
            promise.reject(Error("offerTitle must not be empty"))
            return
        }
        val offerDescription = options1["offerDescription"]
        if (offerDescription == null) {
            promise.reject(Error("offerDescription must not be empty"))
            return
        }
        val offerImageURL = options1["offerImageURL"]
        if (offerImageURL == null) {
            promise.reject(Error("offerImageURL must not be empty"))
            return
        }
        val isModal = options1["isModal"]
        if (isModal == null) {
            promise.reject(Error("isModal must not be empty"))
            return
        }

        try {
            val offer: NativeSpendOffer = NativeSpendOffer(offerId as String)
                    .title(offerTitle as String)
                    .description(offerDescription as String)
                    .amount((offerAmount as Double).toInt())
                    .image(offerImageURL as String)

            if (Kin.addNativeOffer(offer, isModal as Boolean)) {
                promise.resolve(true)
            } else {
                promise.reject(Error("failed to add native offer, unknown error"))
            }
        } catch (exception: Exception) {
            println(exception)
            promise.reject(exception)
        }
    }

    private fun initEventEmitters() {
        this.initNativeOfferEventEmitter()
        this.initBalanceEventEmitter()
    }

    private fun initNativeOfferEventEmitter() {
        if (this.nativeSpendOfferClickedObserver == null) {
            this.nativeSpendOfferClickedObserver = object : Observer<NativeOfferClickEvent>() {
                override fun onChanged(nativeOfferClickEvent: NativeOfferClickEvent) {
                    val offer = nativeOfferClickEvent.getNativeOffer() as NativeSpendOffer

                    // a WritableMap is the equivalent to a JS Object:
                    // the React native bridge will convert it as is
                    val params: WritableMap = Arguments.createMap()
                    params.putString("id", offer.getId())
                    params.putString("title", offer.getTitle())
                    params.putString("description", offer.getDescription())
                    params.putInt("amount", offer.getAmount())
                    params.putString("image", offer.getImage())
                    params.putBoolean("isModal", nativeOfferClickEvent.isDismissOnTap())
                    params.putString("offerType", offer.getOfferType().toString())

                    sendEvent(name = "onNativeOfferClicked", params = params)
                }
            }
        }

        try {
            Kin.addNativeOfferClickedObserver(this.nativeSpendOfferClickedObserver!!)
        } catch (exception: Exception) {
            println(exception)
        }
    }

    private fun initBalanceEventEmitter() {
        if (this.balanceObserver == null) {
            this.balanceObserver = object : Observer<Balance>() {
                override fun onChanged(value: Balance) {
                    sendEvent(name = "onBalanceChanged", params = value.getAmount().intValueExact())
                }
            }
        }

        try {
            Kin.addBalanceObserver(this.balanceObserver!!)
        } catch (exception: Exception) {
            println("Error setting balance observer: $exception")
        }
    }

    @ReactMethod
    fun hasAccount(
            options: ReadableMap,
            promise: Promise
    ) {
        val options1: HashMap<String, Any?> = options.toHashMap()

        val userId = options1["userId"]
        if (userId == null) {
            promise.reject(Error("userId must not be empty"))
            return
        }
        this.hasAccount_(userId as String) { error, hasAccount ->
            if (error != null) {
                promise.reject(error)
                return@hasAccount_
            }
            promise.resolve(hasAccount)
        }
    }

    private fun hasAccount_(
            userId: String,
            completion: (Exception?, Boolean) -> Unit
    ) {
        try {
            Kin.hasAccount(userId, object : KinCallback<Boolean> {
                override fun onResponse(hasAccount: Boolean) {
                    completion(null, hasAccount)
                }

                override fun onFailure(exception: KinEcosystemException) {
                    completion(exception, false)
                }
            })
        } catch (exception: Exception) {
            completion(exception, false)
        }
    }

    @ReactMethod
    fun payToUser(options: ReadableMap, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val options1: HashMap<String, Any?> = options.toHashMap()

        val toUserId = options1["toUserId"] as? String
        if (toUserId == null) {
            promise.reject(Error("toUserId must not be empty"))
            return
        }
        val offerId = options1["offerId"] as? String
        if (offerId == null) {
            promise.reject(Error("offerId must not be empty"))
            return
        }
        val offerAmount = options1["offerAmount"]
        if (offerAmount == null) {
            promise.reject(Error("offerAmount must not be empty"))
            return
        }
        val toUsername: String = options1["toUsername"] as? String ?: toUserId
        val fromUsername: String = options1["fromUsername"] as? String ?: this.loggedInUsername
        ?: this.loggedInUserId!!

        this.hasAccount_(toUserId) { error, hasAccount ->
            if (error != null) {
                promise.reject(error)
                return@hasAccount_
            }
            if (!hasAccount) {
                promise.reject(Error("User $toUserId could not be found. Make sure the receiving user has activated kin."))
                return@hasAccount_
            }

            val parameters: Map<String, Any> = mapOf(
                    "subject" to "pay_to_user",
                    "payload" to mapOf(
                            "offer" to mapOf(
                                    "id" to offerId,
                                    "amount" to offerAmount
                            ),
                            "sender" to mapOf(
                                    "title" to "Pay to $toUsername",
                                    "description" to "Kin transfer to $toUsername",
                                    "user_id" to this.loggedInUserId),
                            "recipient" to mapOf(
                                    "title" to "$fromUsername paid you",
                                    "description" to "Kin transfer from $fromUsername",
                                    "user_id" to toUserId
                            )
                    )
            )

            this.signJWT(parameters) { error1, jwt ->
                if (error1 != null) {
                    promise.reject(error1)
                    return@signJWT
                }

                val handler = object : KinCallback<OrderConfirmation> {
                    override fun onResponse(orderConfirmation: OrderConfirmation) {
                        promise.resolve(orderConfirmation.getJwtConfirmation())
                    }

                    override fun onFailure(exception: KinEcosystemException) {
                        promise.reject(exception)
                    }
                }

                try {
                    Kin.payToUser(jwt, handler)
                } catch (exception: Exception) {
                    promise.reject(exception)
                }
            }
        }
    }
}

