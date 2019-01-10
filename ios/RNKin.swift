//
//  RNKin.swift
//  RNKin
//
//  Created by Philipp on 9/28/18.
//  Copyright © 2018 Facebook. All rights reserved.
//
//  usage in JS:
//  import kin from 'react-native-kin';


import Foundation
import UIKit
import Alamofire
import KinEcosystem

@objc(RNKin)
class RNKin: RCTEventEmitter {
    private var apiKey: String? = nil
    private var appId: String? = nil
    private var privateKey: String? = nil
    private var keyPairIdentifier: String? = nil
    private var useJWT: Bool = false
    private var jwtServiceUrl: String? = nil
    private var jwtServiceHeaderAuth: String? = nil
    private var debug: Bool = false

    private var loggedInUserId: String? = nil
    private var loggedInUsername: String? = nil
    private var isOnboarded_: Bool = false

    private var hasListeners: Bool = false

    private func getRootViewController() -> UIViewController? {
        if var topController = UIApplication.shared.keyWindow?.rootViewController {
            while let presentedViewController = topController.presentedViewController {
                topController = presentedViewController
            }
            // topController should now be your topmost view controller
            return topController
        }
        return nil;
    }

    @objc override static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc override func constantsToExport() -> [AnyHashable : Any]! {
        // expose some variables, if necessary
        return [
            "ENVIRONMENT_BETA": "beta",
            "ENVIRONMENT_PRODUCTION": "production",
        ]
    }

    // we need to override this method and
    // return an array of event names that we can listen to
    @objc override func supportedEvents() -> [String]! {
        return ["onNativeOfferClicked", "onBalanceChanged"]
    }

    override func startObserving() {
        self.hasListeners = true
    }

    override func stopObserving() {
        self.hasListeners = false
    }

    override func sendEvent(withName name: String!, body: Any!) {
        if hasListeners {
            super.sendEvent(withName: name, body: body)
        }
    }

    private func rejectError(
        reject: RCTPromiseRejectBlock,
        message: String? = "unexpected error",
        code: String? = "500"
        ) {
        reject(code, message, NSError(domain: "", code: Int(code!) ?? 500, userInfo: nil))
    }

    private func printCredentials() {
        if !debug {
            return
        }
        print("Credentials:", [
            "apiKey": self.apiKey ?? "NOT-SET",
            "appId": self.appId ?? "NOT-SET",
            "privateKey": self.privateKey ?? "NOT-SET",
            "keyPairIdentifier": self.keyPairIdentifier ?? "NOT-SET",
            "useJWT": self.useJWT,
            "jwtServiceUrl": self.jwtServiceUrl ?? "NOT-SET",
            "jwtServiceHeaderAuth": self.jwtServiceHeaderAuth ?? "NOT-SET",
            "debug": self.debug
            ])
    }

    /**
     check if credentials are correct

     - Returns: false if not correct; true if correct
     */
    private func checkCredentials() throws {
        if self.apiKey == nil || self.appId == nil {
            throw NSError(domain: "apiKey and appId are missing", code: 500, userInfo: nil)
        }
        if self.useJWT && ((self.privateKey == nil || self.keyPairIdentifier == nil) && self.jwtServiceUrl == nil) {
            throw NSError(domain: "privateKey and keyPairIdentifier must not be empty when useJWT is true OR set jwtServiceUrl", code: 500, userInfo: nil)
        }
    }

    /**
     Set credentials and initialize Object

     - Parameters: options {
     apiKey: String
     appId: String
     privateKey: String?
     keyPairIdentifier: String?
     useJWT: Bool
     jwtServiceUrl: String?
     jwtServiceHeaderAuth: String?
     debug: Bool
     }

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    @objc func setCredentials(
        _ options: [AnyHashable : Any],
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {

        self.apiKey = options["apiKey"] as? String
        self.appId = options["appId"] as? String
        self.privateKey = options["privateKey"] as? String
        self.keyPairIdentifier = options["keyPairIdentifier"] as? String
        self.useJWT = options["useJWT"] != nil && options["useJWT"] as! Bool
        self.jwtServiceUrl = options["jwtServiceUrl"] as? String
        self.jwtServiceHeaderAuth = options["jwtServiceHeaderAuth"] as? String
        self.debug = options["debug"] != nil && options["debug"] as! Bool

        if self.debug {
            Kin.shared.setLogLevel(LogLevel.verbose)
        } else {
            Kin.shared.setLogLevel(LogLevel.warn)
        }

        self.printCredentials();

        do {
            try self.checkCredentials();
        } catch {
            self.rejectError(reject: reject, message: "credentials are incorrect \(error)")
            return
        }

        resolve(true);
    }

    private func getEnvironment(environment: String) -> Environment {
        switch environment {
        case "beta":
            return .beta
        case "production":
            return .production
        default:
            return .beta
        }
    }

    private func signJWT(
        _ parameters: Parameters,
        completion: @escaping (NSError?, String?) -> Void
        ) {
        if self.jwtServiceUrl == nil {
            guard let encoded = JWTUtil.encode(header: ["alg": "RS512",
                                                        "typ": "jwt",
                                                        "kid" : self.keyPairIdentifier!],
                                               body: parameters["payload"] as! [AnyHashable : Any],
                                               subject: parameters["subject"] as! String,
                                               id: self.appId!,
                                               privateKey: self.privateKey!
                ) else {
                    print("encode went wrong")
                    completion(NSError(domain: "JWT encode went wrong", code: 500, userInfo: nil), nil)
                    return
            }
            completion(nil, encoded)
            return;
        }

        var headers: HTTPHeaders = [:]
        if self.jwtServiceHeaderAuth != nil {
            headers["authorization"] = self.jwtServiceHeaderAuth
        }

        // use servive url and sign JWT on server
        Alamofire.request(
            "\(self.jwtServiceUrl!)/sign",
            method: .post,
            parameters: parameters,
            encoding: JSONEncoding.default,
            headers: headers
            )
            .validate()
            .responseJSON { response in
                guard response.result.isSuccess else {
                    print("Error while signing JWT: \(String(describing: response.result.error))")
                    if response.data != nil {
                        do {
                            var json = try JSONSerialization.jsonObject(with: response.data!, options: []) as? [String: Any]
                            json?["alamofireError"] = String(describing: response.result.error)
                            if(json?["error"] != nil) {
                                completion(NSError(domain: json?["error"] as! String, code: response.response?.statusCode ?? 500, userInfo: json), nil)
                            } else {
                                completion(NSError(domain: "Error while signing JWT", code: response.response?.statusCode ?? 500, userInfo: json), nil)
                            }
                            return
                        } catch {
                            // do nothing
                            print(error)
                        }
                    }

                    completion(NSError(domain: "Error while signing JWT: \(String(describing: response.result.error))", code: response.response?.statusCode ?? 500, userInfo: nil), nil)
                    return
                }

                guard let value = response.result.value as? [String: Any],
                    let jwt = value["jwt"] as? String else {
                        print("JWT not received from sign service")
                        completion(NSError(domain: "JWT not received from sign service", code: 500, userInfo: nil), nil)
                        return
                }

                completion(nil, jwt)
        }
    }

    private func loginWithJWT(
        _ userId: String,
        environment: Environment,
        completion: @escaping (NSError?) -> Void
        ) {

        let parameters: Parameters = [
            "subject": "register",
            "payload": [
                "user_id": userId
            ]
        ]

        self.signJWT(parameters) { (error, jwt) in
            if error != nil {
                completion(error) // there was an error fetching JWT
                return
            }
            do {
                try Kin.shared.start(userId: userId, appId: self.appId!, jwt: jwt, environment: environment)
                completion(nil)
            } catch {
                print("Kin.start: \(error)")
                completion(error as NSError)
            }
        }
    }

    /**
     - Returns: resolve(Bool) if Kin SDK is started and onboarded; never rejects
     */
    @objc func isOnboarded(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {
        resolve(self.isOnboarded_)
    }

    /**
     Start Kin SDK with a userId; registers a user

     - Parameters: options {
     userId: String
     username: String?
     environment: String beta|production
     }

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    @objc func start(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        guard let userId = options["userId"] as? String else {
            self.rejectError(reject: reject, message: "userId is missing");
            return
        }
        self.loggedInUserId = userId
        self.loggedInUsername = options["username"] as? String

        let environment = getEnvironment(environment: options["environment"] as? String ?? "")

        if self.useJWT {
            // this is async, use completer
            loginWithJWT(userId, environment: environment) { (error) in
                if error != nil {
                    reject(nil, error?.domain, error)
                    return
                }
                print("YEAH, started 🚀")
                self.isOnboarded_ = true
                self.initEventEmitters()
                resolve(true)
            }
        } else {
            do {
                // this is sync
                try Kin.shared.start(userId: userId, apiKey: self.apiKey, appId: self.appId!, environment: environment)
                print("YEAH, started 🚀")
                self.isOnboarded_ = true
                self.initEventEmitters()
                resolve(true)
            } catch {
                reject(nil, error.localizedDescription, error)
                return
            }
        }
    }

    /**
     Get wallet address

     - Returns: wallet address; resolve(String)
     */
    @objc func getWalletAddress(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }
        resolve(Kin.shared.publicAddress)
    }

    /**
     Get current balance

     - Returns: current balance; resolve(Decimal)
     */
    @objc func getCurrentBalance(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        // // sync:
        // if let amount = Kin.shared.lastKnownBalance?.amount {
        //     resolve(amount)
        // } else {
        //     self.rejectError(reject: reject, message: "Error when fetching current balance")
        // }

        // async:
        Kin.shared.balance { balance, error in
            guard let amount = balance?.amount else {
                if let error = error {
                    self.rejectError(reject: reject, message: "Error fetching current balance \(error)")
                    return
                }
                return
            }
            resolve(amount)
        }
    }

    /**
     Launch marketplace

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    @objc func launchMarketplace(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let rootViewController = self.getRootViewController()
            else {
                self.rejectError(reject: reject, message: "rootViewController not found")
                return
        }

        // get main queue, otherwise text-labels are invisible
        DispatchQueue.main.async { [weak self] in
            do {
                try Kin.shared.launchEcosystem(from: rootViewController)
            } catch {
                self?.rejectError(reject: reject, message: "launchMarketplace \(error)")
                return
            }
            resolve(true)
        }
    }

    /**
     Launch marketplace history

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    @objc func launchMarketplaceHistory(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let rootViewController = self.getRootViewController()
            else {
                self.rejectError(reject: reject, message: "rootViewController not found")
                return
        }

        // get main queue, otherwise text-labels are invisible
        DispatchQueue.main.async { [weak self] in
            do {
                try Kin.shared.launchEcosystem(from: rootViewController, at: .history)
            } catch {
                self?.rejectError(reject: reject, message: "launchMarketplaceHistory \(error)")
                return
            }
            resolve(true)
        }
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
    @objc func earn(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        var newOptions = options;
        newOptions["offerType"] = "earn"
        self.earnOrSpendOffer(newOptions, resolver: resolve, rejecter: reject)
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
    @objc func spend(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        var newOptions = options;
        newOptions["offerType"] = "spend"
        self.earnOrSpendOffer(newOptions, resolver: resolve, rejecter: reject)
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
    private func earnOrSpendOffer(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let offerType = options["offerType"] as? String else {
            self.rejectError(reject: reject, message: "offerType is missing: earn or spend");
            return
        }
        if offerType != "earn" && offerType != "spend" {
            self.rejectError(reject: reject, message: "offerType has invalid value '\(offerType)'; possible: earn or spend");
            return
        }
        let earn = offerType == "earn"

        guard let offerId = options["offerId"] as? String else {
            self.rejectError(reject: reject, message: "offerId is missing");
            return
        }
        guard let offerAmount = options["offerAmount"] as? Int32 else {
            self.rejectError(reject: reject, message: "offerAmount is missing");
            return
        }
        guard let offerTitle = options["offerTitle"] as? String else {
            self.rejectError(reject: reject, message: "offerTitle is missing");
            return
        }
        guard let offerDescription = options["offerDescription"] as? String else {
            self.rejectError(reject: reject, message: "offerDescription is missing");
            return
        }
        guard let recipientUserId = options["recipientUserId"] as? String else {
            self.rejectError(reject: reject, message: "recipientUserId is missing");
            return
        }

        var recipientOrSenderKey = "sender"
        if earn {
            recipientOrSenderKey = "recipient"
        }

        let parameters: Parameters = [
            "subject": offerType,
            "payload": [
                "offer": ["id": offerId, "amount": offerAmount],
                recipientOrSenderKey: [
                    "title": offerTitle,
                    "description": offerDescription,
                    "user_id": recipientUserId
                ]
            ]
        ]

        self.signJWT(parameters) { (error, jwt) in
            if error != nil {
                reject(nil, error?.domain, error) // there was an error fetching JWT
                return
            }

            let handler: KinCallback = { jwtConfirmation, error in
                if jwtConfirmation != nil {
                    resolve(jwtConfirmation)
                } else {
                    reject(nil, nil, error)
                }
            }

            if earn {
                _ = Kin.shared.requestPayment(offerJWT: jwt!, completion: handler)
            } else {
                _ = Kin.shared.purchase(offerJWT: jwt!, completion: handler)
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
    @objc func addSpendOffer(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        var newOptions = options
        newOptions["offerType"] = "spend"
        addEarnOrSpendOffer(newOptions, resolver: resolve, rejecter: reject)
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
    @objc func addEarnOffer(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        var newOptions = options
        newOptions["offerType"] = "earn"
        addEarnOrSpendOffer(newOptions, resolver: resolve, rejecter: reject)
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
    private func addEarnOrSpendOffer(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let offerId = options["offerId"] as? String else {
            self.rejectError(reject: reject, message: "offerId is missing");
            return
        }
        guard let offerAmount = options["offerAmount"] as? Int32 else {
            self.rejectError(reject: reject, message: "offerAmount is missing");
            return
        }
        guard let offerTitle = options["offerTitle"] as? String else {
            self.rejectError(reject: reject, message: "offerTitle is missing");
            return
        }
        guard let offerDescription = options["offerDescription"] as? String else {
            self.rejectError(reject: reject, message: "offerDescription is missing");
            return
        }
        guard let offerImageURL = options["offerImageURL"] as? String else {
            self.rejectError(reject: reject, message: "offerImageURL is missing");
            return
        }
        guard let isModal = options["isModal"] as? Bool else {
            self.rejectError(reject: reject, message: "isModal is missing");
            return
        }
        guard let offerType = options["offerType"] as? String else {
            self.rejectError(reject: reject, message: "offerType is missing");
            return
        }

        let offer = NativeOffer(id: offerId,
                                title: offerTitle,
                                description: offerDescription,
                                amount: offerAmount,
                                image: offerImageURL,
                                offerType: offerType == "earn" ? .earn : .spend,
                                isModal: isModal)
        do {
            try Kin.shared.add(nativeOffer: offer)
            resolve(true)
        } catch {
            print("failed to add native offer, error: \(error)")
            self.rejectError(reject: reject, message: "failed to add native offer, error: \(error)")
        }
    }

    /**
     Remove native offer (earn or spend) from marketplace

     - Parameters: options {
     offerId: String
     }

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    @objc func removeOffer(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let offerId = options["offerId"] as? String else {
            self.rejectError(reject: reject, message: "offerId is missing");
            return
        }

        do {
            try Kin.shared.remove(nativeOfferId: offerId)
        } catch {
            print("failed to remove native offer, error: \(error)")
            self.rejectError(reject: reject, message: "failed to remove native offer, error: \(error)")
        }
    }

    private func initEventEmitters() {
        self.initNativeOfferEventEmitter()
        self.initBalanceEventEmitter()
    }

    /**
     Usage:
     kin.events.addListener('onNativeOfferClicked', (offer) => {
     console.log('offer clicked', offer);
     })
     */
    private func initNativeOfferEventEmitter() {
        Kin.shared.nativeOfferHandler = { offer in
            let offerDict: [String: Any] = [
                "id": offer.id,
                "title": offer.title,
                "description": offer.description,
                "amount": offer.amount,
                "image": offer.image,
                "isModal": offer.isModal,
                "offerType": offer.offerType == .spend ? "spend" : "earn"
            ]

            if self.bridge != nil {
                self.sendEvent(withName: "onNativeOfferClicked", body: offerDict)
            } else {
                print("initNativeOfferEventEmitter: bridge is nil") // this happens when you reload the RN app withouth fresh start
            }
        }
    }

    /**
     Usage:
     kin.events.addListener('onBalanceChanged', (balance) => {
     console.log('amount changed', balance);
     })
     */
    private func initBalanceEventEmitter() {
        do {
            _ = try Kin.shared.addBalanceObserver { balance in
                if self.bridge != nil {
                    self.sendEvent(withName: "onBalanceChanged", body: balance.amount)
                } else {
                    print("initBalanceEventEmitter: bridge is nil") // this happens when you reload the RN app withouth fresh start
                }
            }
        } catch {
            print("Error setting balance observer: \(error)")
        }
    }

    /**
     Finding out if another user has a kin account

     - Parameters: options {
     userId: String
     }

     - Returns: true if has account, false if not; resolve(Bool); rejects on error
     */
    @objc func hasAccount(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let userId = options["userId"] as? String else {
            self.rejectError(reject: reject, message: "userId is missing");
            return
        }

        self.hasAccount_(userId) { (error, hasAccount) in
            if error != nil {
                reject(nil, error?.domain, error)
                return
            }
            resolve(hasAccount)
        }
    }

    private func hasAccount_(
        _ userId: String,
        completion: @escaping (NSError?, Bool) -> Void
        ) {
        Kin.shared.hasAccount(peer: userId) { response, error in
            if let response = response {
                guard response else {
                    completion(nil, false) // no account
                    return
                }
                completion(nil, true) // has account
            } else if let error = error {
                completion(NSError(domain: error.localizedDescription, code: 500, userInfo: nil), false)
            } else {
                completion(NSError(domain: "unknown error", code: 500, userInfo: nil), false)
            }
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
    @objc func payToUser(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started yet, please wait...")
            return
        }

        guard let toUserId = options["toUserId"] as? String else {
            self.rejectError(reject: reject, message: "toUserId is missing");
            return
        }
        guard let offerId = options["offerId"] as? String else {
            self.rejectError(reject: reject, message: "offerId is missing");
            return
        }
        guard let offerAmount = options["offerAmount"] as? Int32 else {
            self.rejectError(reject: reject, message: "offerAmount is missing");
            return
        }
        let toUsername: String = options["toUsername"] as? String ?? toUserId
        let fromUsername: String = options["fromUsername"] as? String ?? self.loggedInUsername ?? self.loggedInUserId!

        self.hasAccount_(toUserId) { (error, hasAccount) in
            if error != nil {
                reject(nil, error?.domain, error)
                return
            }
            if hasAccount == false {
                self.rejectError(reject: reject, message: "User \(toUserId) could not be found. Make sure the receiving user has activated Kin.")
                return
            }

            let parameters: Parameters = [
                "subject": "pay_to_user",
                "payload": [
                    "offer": ["id": offerId, "amount": offerAmount],
                    "sender": [
                        "title": "Pay to \(toUsername)",
                        "description": "Kin transfer to \(toUsername)",
                        "user_id": self.loggedInUserId!
                    ],
                    "recipient": [
                        "title": "\(fromUsername) paid you",
                        "description":"Kin transfer from \(fromUsername)",
                        "user_id": toUserId
                    ]
                ]
            ]

            self.signJWT(parameters) { (error, jwt) in
                if error != nil {
                    reject(nil, error?.domain, error) // there was an error fetching JWT
                    return
                }

                let handler: KinCallback = { jwtConfirmation, error in
                    if jwtConfirmation != nil {
                        resolve(jwtConfirmation)
                    } else {
                        reject(nil, nil, error)
                    }
                }

                _ = Kin.shared.payToUser(offerJWT: jwt!, completion: handler)
            }
        }
    }
}
