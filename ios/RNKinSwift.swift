//
//  RNKinSwift.swift
//  RNKinSwift
//
//  Created by Philipp on 9/28/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

import Foundation

@objc(RNKinSwift)
class RNKinSwift: NSObject {

  static func requiresMainQueueSetup() -> Bool {
    return true
  }

  private var count = 0

  func logi() {
      count += 1
      print("count is \(count)")
    }

}
