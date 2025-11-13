import { useEffect } from 'react';
import { Text, View, StyleSheet } from 'react-native';
import * as CallDetector from 'react-native-call-detector';
import { PermissionsAndroid } from 'react-native';

export default function App() {
  useEffect(() => {
    (async () => {
      await PermissionsAndroid.requestMultiple([
        PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
        PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE,
        PermissionsAndroid.PERMISSIONS.READ_CALL_LOG,
        PermissionsAndroid.PERMISSIONS.READ_PHONE_NUMBERS,
      ]);
    })();

    CallDetector.start();
    console.log('helloe!');

    CallDetector.onCallStateChange(({ state, call }) => {
      console.log(state, call);
    });
  }, []);

  return (
    <View style={styles.container}>
      <Text>Only calls are allowed!!!</Text>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    backgroundColor: 'white',
    flex: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
});
