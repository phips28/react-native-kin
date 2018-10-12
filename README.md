# react-native-kin

_React Native wrapper for Kin ecosystem SDK for iOS and Android_

Overall Kin doc: https://partners.kinecosystem.com/docs/server/jwt_service.html

## Getting started

`$ npm install react-native-kin --save`

### Mostly automatic installation

`$ react-native link react-native-kin`

### Manual installation

#### iOS

1. In XCode, in the project navigator, right click `Libraries` ➜ `Add Files to [your project's name]`
2. Go to `node_modules` ➜ `react-native-kin` and add `RNKin.xcodeproj`
3. In XCode, in the project navigator, select your project. Add `libRNKin.a` to your project's `Build Phases` ➜ `Link Binary With Libraries`
4. Run your project (`Cmd+R`)<

#### Android

1. Open up `android/app/src/main/java/[...]/MainActivity.java`
2. Add `import com.kin.reactnative.RNKinPackage;` to the imports at the top of the file
3. Add `new RNKinPackage()` to the list returned by the `getPackages()` method
4. Append the following lines to `android/settings.gradle`:
  	```
  	include ':react-native-kin'
  	project(':react-native-kin').projectDir = new File(rootProject.projectDir, 	'../node_modules/react-native-kin/android')
  	```
5. Insert the following lines inside the dependencies block in `android/app/build.gradle`:
  	```
      compile project(':react-native-kin')
  	```


### Additional Installation/Setup:

**iOS:**

- add `use_modular_headers!` to Podfile
- add `platform :ios, '9.0'` to Podfile as minimum build platform
- if your iOS project is ObjC only, then add an empty swift file `DummyToAvoidBuildProblem.swift` to avoid building issues.
 And also add the bridge header. More infos: https://stackoverflow.com/a/50495316/2842800

**Android:**

- MainApplication.java: change to `new RNKinPackage(MainApplication.this)`
- add repository `maven { url 'https://jitpack.io' }` to your project gradle: (TODO: use repo from subproject)
```
allprojects {
    repositories {
        ...
        maven { url 'https://jitpack.io' }
        ...
    }
}
```

## Usage
```javascript
import kin from 'react-native-kin';

// As soon as you have a unique id from your user, start the Kin SDK

// Set credentials
kin.setCredentials({
    "apiKey": "ABCD", // get from Kin
    "appId": "abcd", // get from Kin
    "useJWT": true,
    "jwtServiceUrl": "https://localhost..." // see "Setup JWT Service"
  })
  .then((credentials) => {
    // successfully set credentials
    // now you can start listening on events:
    initEventHandler();
  })
  // Start Kin SDK
  .then(() => kin.start({
    userId: userId, // must be a unique id
    username: username, // used in 'payToUser()' as description who sent Kin
    environment: kin.ENVIRONMENT_PLAYGROUND, // or kin.ENVIRONMENT_PRODUCTION
  }))
  .then((start) => {
    // successfully SDK started
    console.log('start', start);
  })
  .catch((error) => {
    // error :(
    console.error('start', error);
  });

// Listeners
initEventHandler() {
  kin.events.addListener('onNativeOfferClicked', (offer) => {
    console.log('Listener: offer clicked', offer);
  });
  kin.events.addListener('onBalanceChanged', (balance) => {
    console.log('Listener: amount changed', balance);
  });
}

// Important: Remove listeners if you dont need them anymore

// ------------------------------------------------------------

// Launch marketplace
kin.launchMarketplace()
  .then((marketPlace) => {
    console.log({ marketPlace });
  })
  .catch((error) => {
    console.error('launchMarketplace', error);
  });

// Get wallet address
kin.getWalletAddress()
  .then((walletAddress) => {
    console.log({ walletAddress });
  })
  .catch((error) => {
    console.error('getWalletAddress', error);
  });

// Get balance
kin.getCurrentBalance()
  .then((currentBalance) => {
    console.log({ currentBalance });
  })
  .catch((error) => {
    console.error('getCurrentBalance', error);
  });

// Earn
kin.earn({
    offerId: 'unique-id',
    offerAmount: 100,
    offerTitle: 'This is a demo earn',
    offerDescription: 'earn: demo',
    recipientUserId: userId,
  })
  .then((jwtConfirmation) => {
    console.log(`https://jwt.io/#debugger-io?token=${jwtConfirmation}`);
  })
  .catch((error) => {
    console.error('earn', error);
  });

// Spend
kin.spend({
    offerId: 'unique-id',
    offerAmount: 10,
    offerTitle: 'This is a demo spend',
    offerDescription: 'spend: demo',
    recipientUserId: userId, // current user id
  })
  .then((jwtConfirmation) => {
    console.log(`https://jwt.io/#debugger-io?token=${jwtConfirmation}`);
  })
  .catch((error) => {
    console.error('spend', error);
  });

// Pay to other user
kin.payToUser({
    offerId: `pay-to-user-${userId}-${toUserId}`,
    offerAmount: 10,
    toUserId: toUserId,
    toUsername: toUsername,
    fromUsername: 'tester' // customize name here, or it will use the 'username' from start()
  })
  .then((jwtConfirmation) => {
    console.log(`https://jwt.io/#debugger-io?token=${jwtConfirmation}`);
    alert(`payToUser: ${jwtConfirmation}`);
  })
  .catch((error) => {
    console.error('payToUser', error);
  });

// Add spend offer to marketplace
kin.addSpendOffer({
    offerId: offerId,
    offerAmount: 10,
    offerTitle: 'offer title',
    offerDescription: 'offer description',
    offerImageURL: 'https://via.placeholder.com/300x225',
    isModal: true, // close on tap = true; should always be true
  })
  .then((success) => {
    console.log(`addSpendOffer: ${offerId}: ${success}`);
  })
  .catch((error) => {
    console.error('addSpendOffer', error);
  });

// Remove spend offer from marketplace
kin.removeSpendOffer({
    offerId: offerId,
  })
  .then((success) => {
    console.log(`removeSpendOffer: ${offerId}: ${success}`);
  })
  .catch((error) => {
    console.error('removeSpendOffer', error);
  });
```

## Setup JWT Service

1. clone: https://github.com/kinecosystem/jwt-service
2. create .sh file `create_keys.sh` + `chmod +x create_keys.sh`

```
#!/bin/sh
# usage:   program <DIR>
# example: program /tmp/out

if [ $# -ge 1 ]
then
    DIR=$1
else
    DIR=.
fi

for i in `seq 1 10`; do
    uuid=$(uuidgen)

    pub=es256_$uuid.pem
    priv=es256_$uuid-priv.pem

    openssl ecparam -name secp256k1 -genkey -noout -out $DIR/keys/$priv
    openssl ec -in $DIR/keys/$priv -pubout -out $DIR/keys/$pub

done
```
3. and create keys `./create_keys.sh`
4. send public keys to kin team (tell them kid = es256_UUID)
5. npm run transpile
6. npm start

## Native Module Reads

- https://facebook.github.io/react-native/docs/native-modules-ios.html
- https://facebook.github.io/react-native/docs/native-modules-android
- https://teabreak.e-spres-oh.com/swift-in-react-native-the-ultimate-guide-part-1-modules-9bb8d054db03#d267

## Demo - Start the example app

see example/demo/README.md
