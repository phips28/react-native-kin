//
//  JWTUtil.swift
//  EcosystemSampleApp
//
//  Created by Elazar Yifrach on 10/05/2018.
//  Copyright © 2018 Kik Interactive. All rights reserved.
//

import Foundation
import JWT
// Just a sample utility for encoding a rs512 jwt using an external framework
// with a default expiration time of 1 day

class JWTUtil {
    static func encode(header: [AnyHashable: Any], body: [AnyHashable: Any], subject: String, id: String, privateKey: String) -> String? {

        do {
            var originalPrivateKey = "MHQCAQEEIGb2TuUkWvnTskK7ufBSw3b0uXRsTpwWPV4belQ1jpMEoAcGBSuBBAAKoUQDQgAErRRQk2bdvLHFm3R3eXlQoJ2wBhbIqOMAv5irwz7gDEtBvduTtR+HXxt8RJ3DahCcK2l/F9eV9wUjJOsAMEQ2tA=="
            print("originalPrivateKey", originalPrivateKey)

//            let privateKeyCrypto1 = try JWTCryptoKeyPrivate(base64String: originalPrivateKey, parameters: nil)
            let data = Data(base64Encoded: originalPrivateKey)!
            print("data", data)
            let privateKeyCrypto2 = try JWTCryptoKeyPrivate(data: data, parameters: nil)
            originalPrivateKey = String(data: data, encoding: .ascii)!
            print(originalPrivateKey)
            let privateKeyCrypto3 = try JWTCryptoKeyPrivate(pemEncoded: originalPrivateKey, parameters: nil)


            let privateKeyCrypto = try JWTCryptoKeyPrivate(data: Data(base64Encoded: privateKey) , parameters: nil)

            guard let holder = JWTAlgorithmRSFamilyDataHolder()
                .signKey(privateKeyCrypto)?
                .secretData(privateKey.data(using: .utf8))?
                .algorithmName(JWTAlgorithmNameES256)
                else { return nil }

            guard let encoding = JWTEncodingBuilder
                .encodePayload(body)
                .headers(header)?
                .addHolder(holder)
                else { return nil }

            let result = encoding.result

            print(result?.successResult?.encoded ?? "Encoding failed")
            print(result?.errorResult?.error ?? "No encoding error")

//            let verifyDataHolder = JWTAlgorithmRSFamilyDataHolder().signKey(theCrypto)?.secretData(publicKey.data(using: .utf8)!)?.algorithmName(JWTAlgorithmNameRS256)
//
//            let verifyResult = JWTDecodingBuilder.decodeMessage(result?.successResult?.encoded).addHolder(verifyDataHolder)?.result
//
//            if verifyResult?.successResult != nil, let result = verifyResult?.successResult.encoded {
//                print("Verification successful, result: \(result)")
//            } else {
//                print("Verification error: \(verifyResult!.errorResult.error)")
//            }
//            resultStr = result?.successResult.encoded
        } catch {
            print(error)
            return nil
        }


        var key: JWTCryptoKeyPrivate?
        var holder_: JWTAlgorithmRSFamilyDataHolder?
        do {
            key = try JWTCryptoKeyPrivate(pemEncoded: privateKey, parameters: nil)
            holder_ = (JWTAlgorithmRSFamilyDataHolder().signKey(key)?.secretData(privateKey.data(using: .utf8))?.algorithmName(JWTAlgorithmNameRS512) as? JWTAlgorithmRSFamilyDataHolder)
            holder_ = (JWTAlgorithmRSFamilyDataHolder().signKey(key)?
                .algorithmName(JWTAlgorithmNameRS512) as? JWTAlgorithmRSFamilyDataHolder)
            holder_ = (JWTAlgorithmRSFamilyDataHolder().secretData(privateKey.data(using: .utf8))?
                .algorithmName(JWTAlgorithmNameRS512) as? JWTAlgorithmRSFamilyDataHolder)
        } catch  {
            print(error)
        }
        guard let holder = holder_ else {
            return nil
        }
        let claims = JWTClaimsSet()
        let issuedAt = Date()
        claims.issuer = id
        claims.issuedAt = issuedAt
        claims.expirationDate = issuedAt.addingTimeInterval(86400.0)
        claims.subject = subject

        guard var claimsDict = JWTClaimsSetSerializer.dictionary(with: claims) else {
            return nil
        }
        for (k, v) in body {
            claimsDict[k] = v
        }
        guard let result = JWTEncodingBuilder.encodePayload(claimsDict)
            .headers(header)?
            .addHolder(holder)?
            .result.successResult?.encoded else {
                return nil
        }
        return result
    }
}
