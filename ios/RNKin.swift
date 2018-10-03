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

@objc(RNKin)
class RNKin: NSObject {
    private var apiKey: String? = nil
    private var appId: String? = nil
    private var privateKey: String? = nil
    private var keyPairIdentifier: String? = nil
    private var useJWT: Bool = false

    private var isOnboarded: Bool = false

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

    private var count = 0

    @objc func increment(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {
        count += 1
        print("count is \(count)")
        resolve(["count": count])
    }

    /**
     usage in JS:
     kin.decrement({})
     .then(console.log)
     .catch((error) => {
     console.error(error)
     });
     */
    @objc func decrement(
        _ options: [AnyHashable : Any],
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {

        if (count == 0) {
            let error = NSError(domain: "", code: 200, userInfo: nil)
            // error code, error message, full error object
            reject("E_COUNT", "count cannot be negative", error)
        } else {
            count -= 1
            resolve("count was decremented")
        }
        print("count is \(count)")
    }

    @objc func openAlert(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        if let rootViewController = self.getRootViewController() {
            let alert = UIAlertController(title: "Please Restart", message: "A new user was created.", preferredStyle: .alert)
            alert.addAction(UIAlertAction(title: "Yuhu", style: .default, handler: { action in
                resolve("default");
            }))
            alert.addAction(UIAlertAction(title: "Oh ok", style: .cancel, handler: { action in
                resolve("cancel");
            }))
            alert.addAction(UIAlertAction(title: "Destroy", style: .destructive, handler: { action in
                let error = NSError(domain: "", code: 200, userInfo: nil)
                reject("BOOM", "destroy", error);
            }))
            rootViewController.present(alert, animated: true, completion: nil)
        } else {
            reject("500", "rootViewController not found", NSError(domain: "", code: 500, userInfo: nil))
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
        print("Credentials:", [
            "apiKey": self.apiKey ?? "NOT-SET",
            "appId": self.appId ?? "NOT-SET",
            "privateKey": self.privateKey ?? "NOT-SET",
            "keyPairIdentifier": self.keyPairIdentifier ?? "NOT-SET",
            "useJWT": self.useJWT
            ])
    }

    /**
     check if credentials are correct
     returns false if not correct
     returns true if correct
     */
    private func checkCredentials() throws {
        if self.apiKey == nil || self.appId == nil {
            throw NSError(domain: "apiKey and appId must not be empty", code: 500, userInfo: nil)
        }
        if self.useJWT && (self.privateKey == nil || self.keyPairIdentifier == nil) {
            throw NSError(domain: "privateKey and keyPairIdentifier must not be empty when useJWT is true", code: 500, userInfo: nil)
        }
    }

    /**
     set credentials and initialize Object

     parameters:
     options {
     - apiKey: String
     - appId: String
     - privateKey: String?
     - keyPairIdentifier: String?
     - useJWT: Bool?
     }
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

    private func loginWithJWT(
        _ userId: String,
        environment: Environment
        ) throws {

        guard let encoded = JWTUtil.encode(
            header: [
                "alg": "RS512",
                "typ": "jwt",
                "kid" : self.keyPairIdentifier!
            ],
            body: [
                "user_id": userId
            ],
            subject: "register",
            id: self.appId!,
            privateKey: self.privateKey!
            ) else {
                throw NSError(domain: "loginWithJWT encode failed", code: 500, userInfo: nil)
        }
        try Kin.shared.start(userId: userId, jwt: encoded, environment: environment)
    }

    /*
     -----------------------------------------------------------------------------
     - login(userId)
     1) app id and key:
     if NOT self.useJWT
     Kin.shared.start(userId: "myUserId", apiKey: self.apiKey, appId: self.appId, environment: DEV ? .playground : .production)
     2) jwt:
     if self.useJWT
     Kin.shared.start(userId: "myUserId", jwt: encodedJWT, environment: DEV ? .playground : .production)

     => after start() is finished -> set isOnboarded=true

     parameters:
     options {
     - userId: String
     - environment: String playground|production
     }
     */
    @objc func start(
        _ options: [AnyHashable : Any],
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {

        guard let userId = options["userId"] as? String else {
            self.rejectError(reject: reject, message: "userId must not be empty");
            return
        }

        let environment = getEnvironment(environment: options["environment"] as? String ?? "")

        if self.useJWT {
            do {
                print("useJWT: do");
                try loginWithJWT(userId, environment: environment)
                print("useJWT: after loginWithJWT");
            } catch {
                reject(nil, nil, error)
                return
            }
        } else {
            do {
                print("apiKey: do");
                try Kin.shared.start(userId: userId, apiKey: self.apiKey, appId: self.appId, environment: environment)
                print("apiKey: after start");
            } catch {
                reject(nil, nil, error)
                return
            }
        }
        print("YEAH ✅")
        self.isOnboarded = true

        resolve(true)
    }

    /*
     -----------------------------------------------------------------------------
     - getWalletAddress()
     only if self.isOnboarded
     Kin.shared.publicAddress
     */
    @objc func getWalletAddress(
        _ resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded {
            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
            return
        }
        resolve(Kin.shared.publicAddress)
    }

    /*
     -----------------------------------------------------------------------------
     - getCurrentBalance()
     only if self.isOnboarded
     if let amount = Kin.shared.lastKnownBalance?.amount {
       print("your balance is \(amount) KIN")
     } else {
       // Kin is not started or an account wasn't created yet.
     }
     */
    @objc func getCurrentBalance(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded {
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

    /*
     -----------------------------------------------------------------------------
     - launchMarketplace()
     only if self.isOnboarded
     Kin.shared.launchMarketplace(from: self)
     */
    @objc func launchMarketplace(
        _ resolve: @escaping RCTPromiseResolveBlock,
        rejecter reject: @escaping RCTPromiseRejectBlock
        ) -> Void {
        if !self.isOnboarded {
            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
            return
        }

        let rootViewController = self.getRootViewController()
        if rootViewController == nil {
            self.rejectError(reject: reject, message: "rootViewController not found")
            return
        }

        do {
            try Kin.shared.launchMarketplace(from: rootViewController!)
        } catch {
            self.rejectError(reject: reject, message: "launchMarketplace \(error)")
            return
        }

        resolve(true)
    }

    /*
     -----------------------------------------------------------------------------
     - requestPayment(TBD)
     https://github.com/kinecosystem/kin-ecosystem-ios-sdk#requesting-payment-for-a-custom-earn-offer
     only if self.isOnboarded

     body: [
     "offer":
       ["id":offerID, "amount":99],
       "recipient": [
         "title":"Give me Kin",
         "description":"A native earn example",
         "user_id":lastUser
       ]
     ],
     subject: "earn",
     ---
     Kin.shared.requestPayment(offerJWT: encodedJWT, completion: handler)
     */
    @objc func requestPayment(
        _ options: [AnyHashable : Any],
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {
//        if !self.isOnboarded {
//            self.rejectError(reject: reject, message: "Kin not started, use kin.start(...) first")
//            return
//        }

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

        guard let encodedJWT = JWTUtil.encode(
            header: [
                "alg": "RS512",
                "typ": "jwt",
                "kid" : self.keyPairIdentifier!
            ],
            body: [
                "offer": ["id": offerId, "amount": offerAmount],
                "recipient": [
                    "title": recipientTitle,
                    "description": recipientDescription,
                    "user_id": recipientUserId
                ]
            ],
            subject: "earn",
            id: self.appId!,
            privateKey: self.privateKey!
            ) else {
                self.rejectError(reject: reject, message: "encode JWT failed");
                return
        }

        print(encodedJWT)
        // TODO handler
        // TODO throw? error?
        // Kin.shared.requestPayment(offerJWT: encodedJWT, completion: handler)
    }

    /*
     -----------------------------------------------------------------------------
     - purchase(TBD)

     only if self.isOnboarded

     body: [
     "offer":
       ["id":offerID, "amount":99],
       "recipient": [
          "title":"Give me Kin",
          "description":"A native earn example",
          "user_id":lastUser
       ]
     ],
     subject: "earn",
     ---
     Kin.shared.purchase(offerJWT: encodedJWT, completion: handler)

     -----------------------------------------------------------------------------
     - addSpendOffer()
     https://github.com/kinecosystem/kin-ecosystem-ios-sdk#adding-a-custom-spend-offer-to-the-kin-marketplace-offer-wall
     only if self.isOnboarded

     */

}
