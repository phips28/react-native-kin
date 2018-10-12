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
console.log('events', events);
// and expose it
RNKin.events = events;

export default RNKin;

