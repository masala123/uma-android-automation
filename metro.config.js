const { getDefaultConfig } = require("expo/metro-config")
const { withNativeWind } = require("nativewind/metro")
const path = require("path")

/**
 * Metro configuration
 * https://reactnative.dev/docs/metro
 *
 * @type {import('@react-native/metro-config').MetroConfig}
 */
const config = getDefaultConfig(__dirname)

// Add resolver configuration.
config.resolver.alias = {
    "@": path.resolve(__dirname, "./"),
}

module.exports = withNativeWind(config, { input: "./global.css", inlineRem: 16 })
