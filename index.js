/**
 * @format
 */

import "react-native-url-polyfill/auto"
import { AppRegistry } from "react-native"
import App from "./src/App"
import "./global.css"

// Register with the name that MainActivity.kt declared.
AppRegistry.registerComponent("Uma Android Automation", () => App)
