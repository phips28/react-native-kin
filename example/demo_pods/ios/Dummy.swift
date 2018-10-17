//
//  Dummy.swift
//  demo_pods
//
//  Created by Philipp on 10/16/18.
//  Copyright © 2018 Facebook. All rights reserved.
//

import Foundation
import Alamofire
import JWT

class RNKin {
  private var jwtServiceUrl: String?
  
  private func signJWT(
    _ parameters: Parameters,
    completion: @escaping (Error?, String?) -> Void
    ) {
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
          if response.data != nil {
            do {
              var json = try JSONSerialization.jsonObject(with: response.data!, options: []) as? [String: Any]
              json?["alamofireError"] = String(describing: response.result.error)
              completion(NSError(domain: "Error while signing JWT", code: response.response?.statusCode ?? 500, userInfo: json), nil)
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
}
