import React from 'react';
import {
  StyleSheet,
  View,
  ActivityIndicator,
  Text,
} from 'react-native';

const Loader = (props) => {
  const { loading, text } = props;

  return (
    <View style={styles.container}>
      <View style={styles.activityIndicatorWrapper}>
        <ActivityIndicator animating={loading} />
        {text && <Text>{text}</Text>}
      </View>
    </View>
  )
};

const styles = StyleSheet.create({
  container: {
    position: 'absolute',
    top: 0,
    left: 0,
    right: 0,
    bottom: 0,
    flex: 1,
    zIndex: 2,
    alignItems: 'center',
    flexDirection: 'column',
    justifyContent: 'space-around',
    backgroundColor: '#00000040'
  },
  activityIndicatorWrapper: {
    backgroundColor: '#FFFFFF',
    height: 100,
    width: 100,
    borderRadius: 10,
    display: 'flex',
    alignItems: 'center',
    justifyContent: 'space-around'
  }
});

export default Loader;
