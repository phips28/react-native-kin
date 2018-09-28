import { NativeModules } from 'react-native';

const { RNKin } = NativeModules;

console.log({ RNKin });

const kin = {};

// RNKin.log = (any) => {
//   console.log({ any });
// };

// class Kin {
// constructor() {
//   console.log('Kin constructor');
// }
//
kin.log = (any) => {
  console.log('log', any);
  console.log(NativeModules);
  console.log(NativeModules.RNKin);
  console.log(RNKin);
  // return RNKin.log(any);
};

// }

export default kin;

