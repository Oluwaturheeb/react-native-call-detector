const { AndroidConfig, createRunOncePlugin } = require('@expo/config-plugins');
const pkg = require('react-native-call-detector/package.json');

const withAndroidPermissions = (config) => {
  return AndroidConfig.Permissions.withPermissions(config, [
    'android.permission.READ_PHONE_STATE',
  ]);
};

const withCallDetector = (config) => withAndroidPermissions(config);

module.exports = createRunOncePlugin(withCallDetector, pkg.name, pkg.version);
