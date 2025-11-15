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

// Exclude Android build directories from Metro's file watcher to prevent ENOENT errors on Windows.
// These directories are created dynamically by Gradle and can cause Metro to crash when trying to watch them.
config.watchFolders = config.watchFolders || []
config.resolver = config.resolver || {}
config.resolver.blockList = [
    // Exclude Android build directories.
    /android\/app\/build\/.*/,
    /android\/build\/.*/,
]

module.exports = withNativeWind(config, { input: "./global.css", inlineRem: 16 })
