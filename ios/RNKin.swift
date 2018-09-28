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

@objc(RNKin)
class RNKin: NSObject {

    @objc static func requiresMainQueueSetup() -> Bool {
        return true
    }

    @objc func constantsToExport() -> [AnyHashable : Any]! {
        // expose some variables, if necessary
        return ["initialCount": 0]
    }

    private var count = 0

    @objc func increment() {
        count += 1
        print("count is \(count)")
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

}
