const { execSync } = require("child_process")
const path = require("path")
const fs = require("fs")

// Get the project root directory (two levels up from android/).
// This script is located in android/, so we need to go up to the React Native project root.
const projectRoot = path.resolve(__dirname, "..")
const assetsDir = path.resolve(__dirname, "app/src/main/assets")

// Ensure the Android assets directory exists before attempting to generate the bundle.
// This directory is where the React Native JavaScript bundle and assets will be stored.
// The recursive option creates parent directories if they don't exist.
if (!fs.existsSync(assetsDir)) {
    fs.mkdirSync(assetsDir, { recursive: true })
}

console.log("Generating React Native bundle...")
console.log("Project root:", projectRoot)
console.log("Assets directory:", assetsDir)

try {
    // Generate the React Native JavaScript bundle for Android platform.
    // This command bundles all JavaScript code into a single file that can be loaded by the Android app.
    // 
    // Command breakdown:
    // - npx react-native bundle: Uses the React Native CLI to create a bundle
    // - --platform android: Specifies the target platform
    // - --dev false: Disables development mode for production-ready bundle
    // - --entry-file index.js: Specifies the main entry point of the React Native app
    // - --bundle-output: Sets the output path for the JavaScript bundle
    // - --assets-dest: Sets the destination for images, fonts, and other assets
    const bundleCommand = `npx react-native bundle --platform android --dev false --entry-file index.js --bundle-output "${path.join(assetsDir, "index.android.bundle")}" --assets-dest "${path.join(assetsDir, "..")}"`

    console.log("Running command:", bundleCommand)
    
    // Execute the bundle command synchronously.
    // The command runs in the project root directory to access React Native configuration.
    // stdio: "inherit" ensures console output is visible during execution.
    // shell: true allows the command to run in a shell environment for better compatibility.
    execSync(bundleCommand, {
        cwd: projectRoot,
        stdio: "inherit",
        shell: true,
    })

    console.log("Bundle generated successfully!")
} catch (error) {
    // If bundle generation fails, log the error and exit with a non-zero code.
    // This ensures the build process fails if the bundle cannot be created.
    console.error("Failed to generate bundle:", error.message)
    process.exit(1)
}
