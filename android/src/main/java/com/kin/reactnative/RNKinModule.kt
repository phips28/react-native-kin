package com.kin.reactnative;

import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.bridge.ReactContext
import com.facebook.react.bridge.ReactContextBaseJavaModule
import com.facebook.react.bridge.ReactMethod
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReadableMap

import com.kin.ecosystem.Environment
import com.kin.ecosystem.Kin
import com.kin.ecosystem.common.KinEnvironment
import com.kin.ecosystem.common.KinCallback
import com.kin.ecosystem.common.NativeOfferClickEvent
import com.kin.ecosystem.common.Observer
import com.kin.ecosystem.common.exception.ClientException
import com.kin.ecosystem.common.exception.KinEcosystemException
import com.kin.ecosystem.common.exception.ServiceException
import com.kin.ecosystem.common.model.Balance
import com.kin.ecosystem.common.model.NativeSpendOffer
import com.kin.ecosystem.common.model.OrderConfirmation
import com.kin.ecosystem.common.model.WhitelistData

import java.lang.Exception;
import android.app.Application;

class RNKinModule(reactContext: ReactApplicationContext, application: Application) : ReactContextBaseJavaModule(reactContext) {

    private var reactContext: ReactApplicationContext = reactContext;
    private var application: Application = application;

    private var apiKey: String? = null
    private var appId: String? = null
    private var privateKey: String? = null
    private var keyPairIdentifier: String? = null
    private var useJWT: Boolean = false
    private var jwtServiceUrl: String? = null

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

    private fun printCredentials() {
        val credentials: Map<String, Any?> = mapOf(
                Pair("apiKey", this.apiKey),
                Pair("appId", this.appId),
                Pair("privateKey", this.privateKey),
                Pair("keyPairIdentifier", this.keyPairIdentifier),
                Pair("useJWT", this.useJWT),
                Pair("jwtServiceUrl", this.jwtServiceUrl)
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
            completion: (Error?, String?) -> Unit
    ) {
        if (this.jwtServiceUrl == null) {
            // TODO for now we do not support local JWT signing
            completion(Error("local JWT signing is not supported, set a jwtServiceUrl"), null)
            return
        }

        // TODO request
//        Alamofire.request("${this.jwtServiceUrl!!}/sign", method = . post, parameters = parameters, encoding = JSONEncoding.default).validate().responseJSON { response ->
//            if (!response.result.isSuccess) {
//                print("Error while signing JWT: ${String(describing = response.result.error)}")
//                if (response.data != null) {
//                    try {
//                        var json = JSONSerialization.jsonObject(with = response.data!!, options = listOf()) as? Map<String, Any>
//                        json?["alamofireError"] = String(describing = response.result.error)
//                        completion(NSError(domain = "Error while signing JWT", code = response.response?.statusCode
//                                ?: 500, userInfo = json), null)
//                        return@responseJSON
//                    } catch (error: Exception) {
//                        print(error)
//                    }
//                }
//                completion(NSError(domain = "Error while signing JWT: ${String(describing = response.result.error)}", code = response.response?.statusCode
//                        ?: 500, userInfo = null), null)
//                return@responseJSON
//            }
//            val value = response.result.value as? Map<String, Any>
//            val jwt = value["jwt"] as? String
//            if (value == null || jwt == null) {
//                print("JWT not received from sign service")
//                completion(NSError(domain = "JWT not received from sign service", code = 500, userInfo = null), null)
//                return@responseJSON
//            }
//            completion(null, jwt)
//        }
    }

    private fun loginWithJWT(
            userId: String,
            environment: KinEnvironment,
            completion: (Error?) -> Unit
    ) {
        val parameters: Map<String, Any> = mapOf(
                "subject" to "register",
                "payload" to mapOf("user_id" to userId)
        )
        this.signJWT(parameters) { error, jwt ->
            if (error != null) {
                completion(error)
                return@signJWT
            }
            try {
                Kin.start(this.reactContext, jwt as String, environment)
                completion(null)
            } catch (error: Error) {
                println("Kin.start: ${error}")
                completion(error)
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
                promise.resolve(cachedBalance)
                return
            } catch (e: Exception) {
                println(e)
                // do nothing, and getBalance()
            }

            Kin.getBalance(object : KinCallback<Balance> {
                override fun onResponse(balance: Balance) {
                    promise.resolve(balance.getAmount())
                }

                override fun onFailure(exception: KinEcosystemException) {
                    promise.reject(Error("Error fetching current balance ${exception}"))
                }
            })
        } catch (exception: ClientException) {
            promise.reject(Error("Error fetching current balance ${exception}"))
        }
    }

    @ReactMethod
    fun launchMarketplace(promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val currentActivity = getCurrentActivity()
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
    fun earn(options: MutableMap<String, Any>, promise: Promise) {
        var newOptions: MutableMap<String, Any> = options
        newOptions["offerType"] = "earn"
        this.earnOrSpendOffer(newOptions, promise = promise)
    }

    @ReactMethod
    fun spend(options: MutableMap<String, Any>, promise: Promise) {
        var newOptions: MutableMap<String, Any> = options
        newOptions["offerType"] = "spend"
        this.earnOrSpendOffer(newOptions, promise = promise)
    }

    private fun earnOrSpendOffer(options: Map<String, Any>, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val offerType = options["offerType"] as? String
        if (offerType == null) {
            promise.reject(Error("offerType must not be empty: earn or spend"))
            return
        }
        if (offerType != "earn" && offerType != "spend") {
            promise.reject(Error("offerType has invalid value '${offerType}'; possible: earn or spend"))
            return
        }
        val earn = offerType == "earn"
        val offerId = options["offerId"] as? String
        if (offerId == null) {
            promise.reject(Error("offerId must not be empty"))
            return
        }
        val offerAmount = options["offerAmount"] as? Int
        if (offerAmount == null) {
            promise.reject(Error("offerAmount must not be empty"))
            return
        }
        val offerTitle = options["offerTitle"] as? String
        if (offerTitle == null) {
            promise.reject(Error("offerTitle must not be empty"))
            return
        }
        val offerDescription = options["offerDescription"] as? String
        if (offerDescription == null) {
            promise.reject(Error("offerDescription must not be empty"))
            return
        }
        val recipientUserId = options["recipientUserId"] as? String
        if (recipientUserId == null) {
            promise.reject(Error("recipientUserId must not be empty"))
            return
        }
        var recipientOrSenderKey = "sender"
        if (earn) {
            recipientOrSenderKey = "recipient"
        }
        val parameters: Map<String, Any> = mapOf("subject" to offerType, "payload" to mapOf("offer" to mapOf("id" to offerId, "amount" to offerAmount), recipientOrSenderKey to mapOf("title" to offerTitle, "description" to offerDescription, "user_id" to recipientUserId)))
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
            } catch (exception: ClientException) {
                promise.reject(exception)
            }
        }
    }

    @ReactMethod
    fun addSpendOffer(options: Map<String, Any>, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val offerId = options["offerId"] as? String
        if (offerId == null) {
            promise.reject(Error("offerId must not be empty"))
            return
        }
        val offerAmount = options["offerAmount"] as? Int
        if (offerAmount == null) {
            promise.reject(Error("offerAmount must not be empty"))
            return
        }
        val offerTitle = options["offerTitle"] as? String
        if (offerTitle == null) {
            promise.reject(Error("offerTitle must not be empty"))
            return
        }
        val offerDescription = options["offerDescription"] as? String
        if (offerDescription == null) {
            promise.reject(Error("offerDescription must not be empty"))
            return
        }
        val offerImageURL = options["offerImageURL"] as? String
        if (offerImageURL == null) {
            promise.reject(Error("offerImageURL must not be empty"))
            return
        }
        val isModal = options["isModal"] as? Boolean
        if (isModal == null) {
            promise.reject(Error("isModal must not be empty"))
            return
        }
        var offer: NativeSpendOffer = NativeSpendOffer(offerId)
                .title(offerTitle)
                .description(offerDescription)
                .amount(offerAmount)
                .image(offerImageURL)

        try {
            if (Kin.addNativeOffer(offer, isModal)) {
                promise.resolve(true)
            } else {
                promise.reject(Error("failed to add native offer, unknown error"))
            }
        } catch (exception: ClientException) {
            promise.reject(exception)
        }
    }

    private fun initEventEmitters() {
        this.initNativeOfferEventEmitter()
        this.initBalanceEventEmitter()
    }

    private fun initNativeOfferEventEmitter() {
        object : Observer<NativeOfferClickEvent>() {
            override fun onChanged(nativeOfferClickEvent: NativeOfferClickEvent) {
                val offer = nativeOfferClickEvent.getNativeOffer() as NativeSpendOffer
                val offerDict: Map<String, Any> = mapOf(
                        "id" to offer.getId(),
                        "title" to offer.getTitle(),
                        "description" to offer.getDescription(),
                        "amount" to offer.getAmount(),
                        "image" to offer.getImage(),
                        "isModal" to nativeOfferClickEvent.isDismissOnTap(),
                        "offerType" to offer.getOfferType()
                )

                // TODO send event
                // this.sendEvent("onNativeOfferClicked", offerDict)
            }
        }
    }

    private fun initBalanceEventEmitter() {
        val balanceObserver = object : Observer<Balance>() {
            override fun onChanged(value: Balance) {
                // TODO send event
                // this.sendEvent("onBalanceChanged", value.getAmount())
                println(value)
            }
        }

        try {
            Kin.addBalanceObserver(balanceObserver)
        } catch (exception: ClientException) {
            println("Error setting balance observer: ${exception}")
        }
    }

    @ReactMethod
    fun hasAccount(options: Map<String, Any>, promise: Promise) {
        val userId = options["userId"] as? String
        if (userId == null) {
            promise.reject(Error("userId must not be empty"))
            return
        }
        this.hasAccount_(userId) { error, hasAccount ->
            if (error != null) {
                promise.reject(error)
                return@hasAccount_
            }
            promise.resolve(hasAccount)
        }
    }

    private fun hasAccount_(userId: String, completion: (Exception?, Boolean) -> Unit) {
        try {
            Kin.hasAccount(userId, object : KinCallback<Boolean> {
                override fun onResponse(hasAccount: Boolean) {
                    completion(null, hasAccount)
                }

                override fun onFailure(exception: KinEcosystemException) {
                    completion(exception, false)
                }
            })
        } catch (exception: ClientException) {
            completion(exception, false)
        }
    }

    @ReactMethod
    fun payToUser(options: Map<String, Any>, promise: Promise) {
        if (!this.isOnboarded_) {
            promise.reject(Error("Kin not started, use kin.start(...) first"))
            return
        }
        val toUserId = options["toUserId"] as? String
        if (toUserId == null) {
            promise.reject(Error("toUserId must not be empty"))
            return
        }
        val offerId = options["offerId"] as? String
        if (offerId == null) {
            promise.reject(Error("offerId must not be empty"))
            return
        }
        val offerAmount = options["offerAmount"] as? Int
        if (offerAmount == null) {
            promise.reject(Error("offerAmount must not be empty"))
            return
        }
        val toUsername: String = options["toUsername"] as? String ?: toUserId
        val fromUsername: String = options["fromUsername"] as? String ?: this.loggedInUsername
        ?: this.loggedInUserId!!
        this.hasAccount_(toUserId) { error, hasAccount ->
            if (error != null) {
                promise.reject(error)
                return@hasAccount_
            }
            if (hasAccount == false) {
                promise.reject(Error("User ${toUserId} could not be found. Make sure the receiving user has activated kin."))
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
                                    "title" to "Pay to ${toUsername}",
                                    "description" to "Kin transfer to ${toUsername}",
                                    "user_id" to this.loggedInUserId!!),
                            "recipient" to mapOf(
                                    "title" to "${fromUsername} paid you",
                                    "description" to "Kin transfer from ${fromUsername}",
                                    "user_id" to toUserId
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
                        promise.reject(exception)
                    }
                }

                try {
                    Kin.payToUser(jwt!!, handler)
                } catch (exception: ClientException) {
                    promise.reject(exception)
                }
            }
        }
    }
}

