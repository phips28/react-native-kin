package com.kin.reactnative;

import android.provider.Settings.Secure;
import com.facebook.react.bridge.*
import com.facebook.react.modules.core.DeviceEventManagerModule
import com.kin.ecosystem.EcosystemExperience
import com.kin.ecosystem.Kin
import com.kin.ecosystem.common.KinCallback
import com.kin.ecosystem.common.NativeOfferClickEvent
import com.kin.ecosystem.common.Observer
import com.kin.ecosystem.common.exception.BlockchainException
import com.kin.ecosystem.common.exception.ClientException
import com.kin.ecosystem.common.exception.KinEcosystemException
import com.kin.ecosystem.common.model.*
import khttp.post
import khttp.responses.Response
import org.jetbrains.anko.doAsync
import org.json.JSONObject
import java.util.*

class RNKinModule(private var reactContext: ReactApplicationContext) : ReactContextBaseJavaModule(reactContext) {

    private var nativeOfferClickedObserver: Observer<NativeOfferClickEvent>? = null
    private var balanceObserver: Observer<Balance>? = null

    private var apiKey: String? = null
    private var appId: String? = null
    private var privateKey: String? = null
    private var keyPairIdentifier: String? = null
    private var useJWT: Boolean = true
    private var jwtServiceUrl: String? = null
    private var jwtServiceHeaderAuth: String? = null
    private var debug: Boolean = false

    private var loggedInUserId: String? = null
    private var loggedInUsername: String? = null
    private var isOnboarded_: Boolean = false

    override fun getName(): String {
        return "RNKin"
    }

    override fun getConstants(): kotlin.collections.Map<String, Any> {
        val constants: Map<String, Any> = mapOf(
                "ENVIRONMENT_BETA" to "beta",
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
                "jwtServiceHeaderAuth" to this.jwtServiceHeaderAuth,
                "debug" to this.debug
        )

        println("credentials = $credentials")
    }

    private fun getDeviceId(): String {
        val deviceId: String = Secure.getString(this.reactContext.getContentResolver(), Secure.ANDROID_ID);
        return deviceId;
    }

    /**
     * check if credentials are correct
     *
     * - Returns: false if not correct; true if correct
     */
    private fun checkCredentials() {
        if (this.apiKey == null || this.appId == null) {
            throw Error("apiKey and appId are missing")
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
     * jwtServiceUrl: String?
     * jwtServiceHeaderAuth: String?
     * debug: Bool?
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
            this.useJWT = true;
            if (options.hasKey("jwtServiceUrl")) {
                this.jwtServiceUrl = options.getString("jwtServiceUrl")
            }
            if (options.hasKey("jwtServiceHeaderAuth")) {
                this.jwtServiceHeaderAuth = options.getString("jwtServiceHeaderAuth")
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
        val jwtServiceHeaderAuth = this.jwtServiceHeaderAuth
        val headers: HashMap<String, String> = hashMapOf<String, String>()

        if (jwtServiceHeaderAuth != null) {
            headers.put("authorization", jwtServiceHeaderAuth);
        }

        doAsync {
            val response: Response = post(
                    url = "$jwtServiceUrl/sign",
                    headers = headers,
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
            completion: (Exception?) -> Unit
    ) {
        val parameters: Map<String, Any> = mapOf(
                "subject" to "register",
                "payload" to mapOf(
                        "user_id" to userId,
                        "device_id" to this.getDeviceId()
                )
        )
        this.signJWT(parameters) { error, jwt ->
            if (error != null) {
                completion(error)
                return@signJWT
            }
            try {
                Kin.login(jwt as String, object : KinCallback<Void> {
                    override fun onResponse(response: Void?) {
                        completion(null)
                    }

                    override fun onFailure(exception: KinEcosystemException) {
                        exception.printStackTrace()
                        completion(exception)
                    }
                })
            } catch (exception: BlockchainException) {
                println("Kin.start: $exception")
                completion(exception)
            }
        }
    }

    /**
    - Returns: resolve(Bool) if Kin SDK is started and onboarded; never rejects
     */
    @ReactMethod
    fun isOnboarded(promise: Promise) {
        promise.resolve(this.isOnboarded_)
    }

    /**
    Start Kin SDK with a userId; registers a user

    - Parameters: options {
    userId: String
    username: String?
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun start(
            options: ReadableMap,
            promise: Promise
    ) {
        if (!options.hasKey("userId")) {
            promise.reject(Exception("userId is missing"))
            return;
        }
        val userId: String = options.getString("userId")!!

        this.loggedInUserId = userId
        if (options.hasKey("username")) {
            this.loggedInUsername = options.getString("username")
        }

        try {
            Kin.initialize(this.reactContext)
        } catch (error: Exception) {
            promise.reject(error)
            return
        }

        this.loginWithJWT(userId) { error ->
            if (error != null) {
                promise.reject(error)
                return@loginWithJWT
            }
            println("YEAH, started 🚀")
            this.isOnboarded_ = true
            this.initEventEmitters()
            promise.resolve(true)
        }
    }

    /**
    Get wallet address

    - Returns: wallet address; resolve(String)
     */
    @ReactMethod
    fun getWalletAddress(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }
        promise.resolve(Kin.getPublicAddress())
    }

    /**
    Get current balance

    - Returns: current balance; resolve(Decimal)
     */
    @ReactMethod
    fun getCurrentBalance(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
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

    /**
    Launch marketplace

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun launchMarketplace(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }
        val currentActivity = this.reactContext.currentActivity
        if (currentActivity == null) {
            promise.reject(Error("currentActivity not found"))
            return
        }
        try {
            Kin.launchEcosystem(currentActivity, EcosystemExperience.MARKETPLACE);
        } catch (error: Error) {
            promise.reject(error)
            return
        }
        promise.resolve(true)
    }

    /**
    Launch marketplace history

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun launchMarketplaceHistory(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }
        val currentActivity = this.reactContext.currentActivity
        if (currentActivity == null) {
            promise.reject(Error("currentActivity not found"))
            return
        }
        try {
            Kin.launchEcosystem(currentActivity, EcosystemExperience.ORDER_HISTORY);
        } catch (error: Error) {
            promise.reject(error)
            return
        }
        promise.resolve(true)
    }

    /**
    Request payment; native earn offer
    https://github.com/kinecosystem/kin-ecosystem-ios-sdk#requesting-payment-for-a-custom-earn-offer

    - Parameters: options {
    offerId: String
    offerAmount: Int
    offerTitle: String
    offerDescription: String
    recipientUserId: String
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun earn(options: ReadableMap, promise: Promise) {
        val o: HashMap<String, Any?> = options.toHashMap()
        o.put("offerType", "earn")
        this.earnOrSpendOffer(o, promise = promise)
    }

    /**
    Purchase; native spend offer

    - Parameters: options {
    offerId: String
    offerAmount: Int
    offerTitle: String
    offerDescription: String
    recipientUserId: String
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun spend(options: ReadableMap, promise: Promise) {
        val o: HashMap<String, Any?> = options.toHashMap()
        o.put("offerType", "spend")
        this.earnOrSpendOffer(o, promise = promise)
    }

    /**
    Earn or Spend offer; use `offerType` to define what you want to do

    - Parameters: options {
    offerType: String (earn|spend)
    offerId: String
    offerAmount: Int32
    offerTitle: String
    offerDescription: String
    recipientUserId: String
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    private fun earnOrSpendOffer(options: HashMap<String, Any?>, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }

        val offerType = options["offerType"]
        if (offerType == null) {
            promise.reject(Error("offerType is missing: earn or spend"))
            return
        }
        if (offerType != "earn" && offerType != "spend") {
            promise.reject(Error("offerType has invalid value '$offerType'; possible: earn or spend"))
            return
        }
        val earn = offerType == "earn"
        val offerId = options["offerId"]
        if (offerId == null) {
            promise.reject(Error("offerId is missing"))
            return
        }
        val offerAmount = options["offerAmount"]
        if (offerAmount == null) {
            promise.reject(Error("offerAmount is missing"))
            return
        }
        val offerTitle = options["offerTitle"]
        if (offerTitle == null) {
            promise.reject(Error("offerTitle is missing"))
            return
        }
        val offerDescription = options["offerDescription"]
        if (offerDescription == null) {
            promise.reject(Error("offerDescription is missing"))
            return
        }
        val recipientUserId = options["recipientUserId"]
        if (recipientUserId == null) {
            promise.reject(Error("recipientUserId is missing"))
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
                                "user_id" to recipientUserId,
                                "device_id" to this.getDeviceId()
                        )
                )
        )

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
                    exception.printStackTrace()
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
                exception.printStackTrace()
                promise.reject(exception)
            }
        }
    }

    /**
    Add spend offer to marketplace

    - Parameters: options {
    offerId: String
    offerAmount: Int32
    offerTitle: String
    offerDescription: String
    offerImageURL: String
    isModal: Bool (set true to close the marketplace on tap)
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun addSpendOffer(
            options: ReadableMap,
            promise: Promise
    ) {
        val o: HashMap<String, Any?> = options.toHashMap()
        o.put("offerType", "spend")
        this.addEarnOrSpendOffer(o, promise = promise)
    }

    /**
    Add earn offer to marketplace

    - Parameters: options {
    offerId: String
    offerAmount: Int32
    offerTitle: String
    offerDescription: String
    offerImageURL: String
    isModal: Bool (set true to close the marketplace on tap)
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun addEarnOffer(
            options: ReadableMap,
            promise: Promise
    ) {
        val o: HashMap<String, Any?> = options.toHashMap()
        o.put("offerType", "earn")
        this.addEarnOrSpendOffer(o, promise = promise)
    }

    /**
    Add earn or spend offer to marketplace

    - Parameters: options {
    offerId: String
    offerAmount: Int32
    offerTitle: String
    offerDescription: String
    offerImageURL: String
    isModal: Bool (set true to close the marketplace on tap)
    offerType: String (earn|spend)
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    private fun addEarnOrSpendOffer(options: HashMap<String, Any?>, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }
        val offerId = options["offerId"]
        if (offerId == null) {
            promise.reject(Error("offerId is missing"))
            return
        }
        val offerAmount = options["offerAmount"]
        if (offerAmount == null) {
            promise.reject(Error("offerAmount is missing"))
            return
        }
        val offerTitle = options["offerTitle"]
        if (offerTitle == null) {
            promise.reject(Error("offerTitle is missing"))
            return
        }
        val offerDescription = options["offerDescription"]
        if (offerDescription == null) {
            promise.reject(Error("offerDescription is missing"))
            return
        }
        val offerImageURL = options["offerImageURL"]
        if (offerImageURL == null) {
            promise.reject(Error("offerImageURL is missing"))
            return
        }
        val isModal = options["isModal"]
        if (isModal == null) {
            promise.reject(Error("isModal is missing"))
            return
        }
        val offerType = options["offerType"]
        if (offerType == null) {
            promise.reject(Error("offerType is missing"))
            return
        }

        try {
            val offer: NativeOffer =
                    if (offerType == "earn") NativeEarnOfferBuilder(offerId as String)
                            .title(offerTitle as String)
                            .description(offerDescription as String)
                            .amount((offerAmount as Double).toInt())
                            .image(offerImageURL as String)
                            .build()
                    else NativeSpendOfferBuilder(offerId as String)
                            .title(offerTitle as String)
                            .description(offerDescription as String)
                            .amount((offerAmount as Double).toInt())
                            .image(offerImageURL as String)
                            .build();
            if (Kin.addNativeOffer(offer, isModal as Boolean)) {
                promise.resolve(true)
            } else {
                promise.reject(Error("failed to add native offer, unknown error"))
            }
        } catch (exception: Exception) {
            promise.reject(exception)
        }
    }

    /**
    Remove spend offer from marketplace

    - Parameters: options {
    offerId: String
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun removeSpendOffer(
            options: ReadableMap,
            promise: Promise
    ) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }
        val options1: HashMap<String, Any?> = options.toHashMap()

        val offerId = options1["offerId"]
        if (offerId == null) {
            promise.reject(Error("offerId is missing"))
            return
        }

        try {
            val offer: NativeOffer = NativeSpendOffer(offerId as String)

            if (Kin.removeNativeOffer(offer)) {
                promise.resolve(true)
            } else {
                promise.reject(Error("failed to remove native offer, unknown error"))
            }
        } catch (exception: Exception) {
            promise.reject(exception)
        }
    }

    private fun initEventEmitters() {
        this.initNativeOfferEventEmitter()
        this.initBalanceEventEmitter()
    }

    /**
    Usage:
    kin.events.addListener('onNativeOfferClicked', (offer) => {
    console.log('offer clicked', offer);
    })
     */
    private fun initNativeOfferEventEmitter() {
        if (this.nativeOfferClickedObserver == null) {
            this.nativeOfferClickedObserver = object : Observer<NativeOfferClickEvent>() {
                override fun onChanged(nativeOfferClickEvent: NativeOfferClickEvent) {
                    val offer = nativeOfferClickEvent.getNativeOffer() as NativeOffer

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
            Kin.addNativeOfferClickedObserver(this.nativeOfferClickedObserver!!)
        } catch (exception: Exception) {
            println(exception)
        }
    }

    /**
    Usage:
    kin.events.addListener('onBalanceChanged', (balance) => {
    console.log('amount changed', balance);
    })
     */
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

    /**
    Finding out if another user has a kin account

    - Parameters: options {
    userId: String
    }

    - Returns: true if has account, false if not; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun hasAccount(
            options: ReadableMap,
            promise: Promise
    ) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }

        val options1: HashMap<String, Any?> = options.toHashMap()

        val userId = options1["userId"]
        if (userId == null) {
            promise.reject(Error("userId is missing"))
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

    /**
    Pay to another user

    - Parameters: options {
    toUserId: String
    toUsername: String?; fallback: toUserId
    fromUsername: String?; fallback: loggedInUsername || loggedInUserId
    offerId: String
    offerAmount: Int32
    }

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun payToUser(options: ReadableMap, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started yet, please wait..."))
            return
        }
        val options1: HashMap<String, Any?> = options.toHashMap()

        val toUserId = options1["toUserId"] as? String
        if (toUserId == null) {
            promise.reject(Error("toUserId is missing"))
            return
        }
        val offerId = options1["offerId"] as? String
        if (offerId == null) {
            promise.reject(Error("offerId is missing"))
            return
        }
        val offerAmount = options1["offerAmount"]
        if (offerAmount == null) {
            promise.reject(Error("offerAmount is missing"))
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
                                    "user_id" to this.loggedInUserId,
                                    "device_id" to this.getDeviceId()),
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
                        exception.printStackTrace()
                        promise.reject(exception)
                    }
                }

                try {
                    Kin.payToUser(jwt, handler)
                } catch (exception: Exception) {
                    exception.printStackTrace()
                    promise.reject(exception)
                }
            }
        }
    }

    /**
    Logout user

    - Returns: true if successful; resolve(Bool); rejects on error
     */
    @ReactMethod
    fun logout(promise: Promise) {
        try {
            Kin.logout();
        } catch (exception: ClientException) {
            exception.printStackTrace()
            promise.reject(exception)
        }
    }
}

