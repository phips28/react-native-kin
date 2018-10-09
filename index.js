import {
  NativeModules,
  NativeEventEmitter,
} from 'react-native';

const { RNKin } = NativeModules;

// instantiate the event emitter
const events = new NativeEventEmitter(RNKin);
// and expose it
RNKin.events = events;

export default RNKin;

