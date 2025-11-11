import { useEffect } from 'react';
import { Text, View, StyleSheet } from 'react-native';
import * as CallDetector from 'react-native-call-detector';
import { PermissionsAndroid } from 'react-native';

export default function App() {
  useEffect(() => {
    (async () => {
      await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.READ_PHONE_STATE
      );
    })();
    CallDetector.start();
    CallDetector.onChange((event: CallDetector.CallStateEvent) => {
      console.log(event);

      if (event.state == 'Incoming') {
      }
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
