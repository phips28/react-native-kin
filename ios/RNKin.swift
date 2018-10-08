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
import KinEcosystem
import Alamofire

@objc(RNKin)
class RNKin: NSObject {
    private var apiKey: String? = nil
    private var appId: String? = nil
    private var privateKey: String? = nil
    private var keyPairIdentifier: String? = nil
    private var useJWT: Bool = false
    private var jwtServiceUrl: String? = nil

    private var isOnboarded_: Bool = false

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

    @objc static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func constantsToExport() -> [AnyHashable : Any]! {
        // expose some variables, if necessary
        return [
            "ENVIRONMENT_PLAYGROUND": "playground",
            "ENVIRONMENT_PRODUCTION": "production",
        ]
    }

    private func rejectError(
        reject: RCTPromiseRejectBlock,
        message: String? = "unexpected error",
        code: String? = "500"
        ) {
        reject(code, message, NSError(domain: "", code: Int(code!) ?? 500, userInfo: nil))
    }

    private func printCredentials() {
        print("Credentials:", [
            "apiKey": self.apiKey ?? "NOT-SET",
            "appId": self.appId ?? "NOT-SET",
            "privateKey": self.privateKey ?? "NOT-SET",
            "keyPairIdentifier": self.keyPairIdentifier ?? "NOT-SET",
            "useJWT": self.useJWT,
            "jwtServiceUrl": self.jwtServiceUrl ?? "NOT-SET"
            ])
    }

    /**
     check if credentials are correct

     - Returns: false if not correct; true if correct
     */
    private func checkCredentials() throws {
        if self.apiKey == nil || self.appId == nil {
            throw NSError(domain: "apiKey and appId must not be empty", code: 500, userInfo: nil)
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
     useJWT: Bool?
     jwtServiceUrl: String?
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
        case "playground":
            return .playground
        case "production":
            return .production
        default:
            return .playground
        }
    }

    private func signJWT(
        _ parameters: Parameters,
        completion: @escaping (Error?, String?) -> Void
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

        // use servive url and sign JWT on server
        Alamofire.request(
            "\(self.jwtServiceUrl!)/sign",
            method: .post,
            parameters: parameters,
            encoding: JSONEncoding.default
            )
            .validate()
            .responseJSON { response in
                guard response.result.isSuccess else {
                    print("Error while signing JWT: \(String(describing: response.result.error))")
                    print(response.result)
                    completion(response.result.error, nil)
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
        completion: @escaping (Error?) -> Void
        ) {

        //        guard let encoded = JWTUtil.encode(
        //            header: [
        //                "alg": "RS512",
        //                "typ": "jwt",
        //                "kid" : self.keyPairIdentifier!
        //            ],
        //            body: [
        //                "user_id": userId
        //            ],
        //            subject: "register",
        //            id: self.appId!,
        //            privateKey: self.privateKey!
        //            ) else {
        //                throw NSError(domain: "loginWithJWT encode failed", code: 500, userInfo: nil)
        //        }

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
                try Kin.shared.start(userId: userId, jwt: jwt, environment: environment)
                completion(nil)
            } catch {
                print("Kin.start: \(error)")
                completion(error)
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
     environment: String playground|production
     }

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    @objc func start(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {

        guard let userId = options["userId"] as? String else {
            self.rejectError(reject: reject, message: "userId must not be empty");
            return
        }

        let environment = getEnvironment(environment: options["environment"] as? String ?? "")

        if self.useJWT {
            // this is async, use completer
            self.printCredentials()
            loginWithJWT(userId, environment: environment) { (error) in
                if error != nil {
                    reject(nil, nil, error)
                    return
                }
                print("YEAH, started 🚀")
                self.isOnboarded_ = true
                resolve(true)
            }
        } else {
            do {
                // this is sync
                self.printCredentials()
                try Kin.shared.start(userId: userId, apiKey: self.apiKey, appId: self.appId, environment: environment)
                print("YEAH, started 🚀")
                self.isOnboarded_ = true
                resolve(true)
            } catch {
                reject(nil, nil, error)
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
            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
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
            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
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
            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
            return
        }

        guard let rootViewController = self.getRootViewController()
            else {
                self.rejectError(reject: reject, message: "rootViewController not found")
                return
        }

        do {
            try Kin.shared.launchMarketplace(from: rootViewController)
        } catch {
            self.rejectError(reject: reject, message: "launchMarketplace \(error)")
            return
        }

        resolve(true)
    }

    /**
     Request payment; native earn offer
     https://github.com/kinecosystem/kin-ecosystem-ios-sdk#requesting-payment-for-a-custom-earn-offer

     - Parameters: options {
     offerId: String
     offerAmount: Decimal
     recipientTitle: String
     recipientDescription: String
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
     offerAmount: Decimal
     recipientTitle: String
     recipientDescription: String
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
     offerAmount: Decimal
     recipientTitle: String
     recipientDescription: String
     recipientUserId: String
     }

     - Returns: true if successful; resolve(Bool); rejects on error
     */
    private func earnOrSpendOffer(
        _ options: [AnyHashable : Any],
        resolver resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        print(options)
        if !self.isOnboarded_ {
            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
            return
        }

        guard let offerType = options["offerType"] as? String else {
            self.rejectError(reject: reject, message: "offerType must not be empty: earn or spend");
            return
        }
        if offerType != "earn" && offerType != "spend" {
            self.rejectError(reject: reject, message: "offerType has invalid value '\(offerType)'; possible: earn or spend");
            return
        }
        let earn = offerType == "earn"

        guard let offerId = options["offerId"] as? String else {
            self.rejectError(reject: reject, message: "offerId must not be empty");
            return
        }
        guard let offerAmount = options["offerAmount"] as? Int else {
            self.rejectError(reject: reject, message: "offerAmount must not be empty");
            return
        }
        guard let recipientTitle = options["recipientTitle"] as? String else {
            self.rejectError(reject: reject, message: "recipientTitle must not be empty");
            return
        }
        guard let recipientDescription = options["recipientDescription"] as? String else {
            self.rejectError(reject: reject, message: "recipientDescription must not be empty");
            return
        }
        guard let recipientUserId = options["recipientUserId"] as? String else {
            self.rejectError(reject: reject, message: "recipientUserId must not be empty");
            return
        }

        //        guard let encodedJWT = JWTUtil.encode(
        //            header: [
        //                "alg": "RS512",
        //                "typ": "jwt",
        //                "kid" : self.keyPairIdentifier!
        //            ],
        //            body: [
        //                "offer": ["id": offerId, "amount": offerAmount],
        //                "recipient": [
        //                    "title": recipientTitle,
        //                    "description": recipientDescription,
        //                    "user_id": recipientUserId
        //                ]
        //            ],
        //            subject: offerType,
        //            id: self.appId!,
        //            privateKey: self.privateKey!
        //            ) else {
        //                self.rejectError(reject: reject, message: "encode JWT failed");
        //                return
        //        }

        var recipientOrSenderKey = "sender"
        if earn {
            recipientOrSenderKey = "recipient"
        }

        let parameters: Parameters = [
            "subject": offerType,
            "payload": [
                "offer": ["id": offerId, "amount": offerAmount],
                recipientOrSenderKey: [
                    "title": recipientTitle,
                    "description": recipientDescription,
                    "user_id": recipientUserId
                ]
            ]
        ]

        self.signJWT(parameters) { (error, jwt) in
            if error != nil {
                reject(nil, nil, error) // there was an error fetching JWT
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
     -----------------------------------------------------------------------------
     - addSpendOffer()
     https://github.com/kinecosystem/kin-ecosystem-ios-sdk#adding-a-custom-spend-offer-to-the-kin-marketplace-offer-wall
     only if self.isOnboarded_

     */
    // TODO
}
