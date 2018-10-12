import {
  NativeModules,
  NativeEventEmitter,
  DeviceEventEmitter,
  Platform,
} from 'react-native';

const { RNKin } = NativeModules;

// instantiate the event emitter
const events = Platform.select({
  ios: new NativeEventEmitter(RNKin),
  android: DeviceEventEmitter,
});
// and expose it
RNKin.events = events;

export default RNKin;

