const { AndroidConfig, createRunOncePlugin } = require('@expo/config-plugins');
const pkg = require('react-native-call-detector/package.json');

const withAndroidPermissions = (config) => {
  return AndroidConfig.Permissions.withPermissions(config, [
    'android.permission.READ_PHONE_STATE',
    'android.permission.READ_CALL_LOG',
    'android.permission.READ_PHONE_NUMBERS',
  ]);
};

const withCallDetector = (config) => withAndroidPermissions(config);

module.exports = createRunOncePlugin(withCallDetector, pkg.name, pkg.version);
