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

@objc(RNKin)
class RNKin: NSObject {
    private var appKey: String? = nil
    private var appId: String? = nil
    private var useJWT: Bool = false
    private var privateKey: String? = nil

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
        return ["initialCount": 0]
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

    @objc func setCredentials(
        _ options: [AnyHashable : Any],
        resolver resolve: RCTPromiseResolveBlock,
        rejecter reject: RCTPromiseRejectBlock
        ) -> Void {
        // TODO check params
        // TODO set params
    }

}
