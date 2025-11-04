import * as Application from "expo-application"
import MessageLog from "../../components/MessageLog"
import { useContext, useEffect, useState } from "react"
import { BotStateContext } from "../../context/BotStateContext"
import { useSettings } from "../../context/SettingsContext"
import { logWithTimestamp, logErrorWithTimestamp } from "../../lib/logger"
import { DeviceEventEmitter, StyleSheet, View, NativeModules } from "react-native"
import { MessageLogContext } from "../../context/MessageLogContext"
import { useTheme } from "../../context/ThemeContext"
import CustomButton from "../../components/CustomButton"
import { Text } from "../../components/ui/text"
import { AlertDialog, AlertDialogAction, AlertDialogContent, AlertDialogDescription, AlertDialogFooter, AlertDialogHeader, AlertDialogTitle } from "../../components/ui/alert-dialog"

const styles = StyleSheet.create({
    root: {
        flex: 1,
        flexDirection: "column",
        alignItems: "center",
        paddingHorizontal: 10,
        paddingVertical: 10,
    },
    contentContainer: {
        flex: 1,
        width: "100%",
        flexDirection: "column",
    },
    buttonContainer: {
        alignItems: "center",
        marginBottom: 10,
    },
    button: {
        width: 100,
    },
})

const Home = () => {
    const { StartModule } = NativeModules

    const { isDark } = useTheme()
    const [isRunning, setIsRunning] = useState<boolean>(false)
    const [showNotReadyDialog, setShowNotReadyDialog] = useState<boolean>(false)

    const bsc = useContext(BotStateContext)
    const mlc = useContext(MessageLogContext)
    const { saveSettings } = useSettings()

    useEffect(() => {
        const mediaProjectionSubscription = DeviceEventEmitter.addListener("MediaProjectionService", (data) => {
            setIsRunning(data["message"] === "Running")
        })

        const botServiceSubscription = DeviceEventEmitter.addListener("BotService", (data) => {
            if (data["message"] === "Running") {
                mlc.setMessageLog([])
            }
        })

        getVersion()

        return () => {
            mediaProjectionSubscription.remove()
            botServiceSubscription.remove()
        }
    }, [])

    // Grab the program name and version.
    const getVersion = () => {
        const appName = Application.applicationName || "App"
        var version = Application.nativeApplicationVersion || "0.0.0"
        version += " (" + (Application.nativeBuildVersion || "0") + ")"
        logWithTimestamp("Android app version is " + version)
        bsc.setAppName(appName)
        bsc.setAppVersion(version)
    }

    const handleButtonPress = async () => {
        if (isRunning) {
            StartModule.stop()
        } else if (bsc.readyStatus) {
            // Save settings before starting the bot.
            // Also has the added benefit of only writing to the SQLite database when the bot is started instead of every time the settings are changed.
            logWithTimestamp("[Home] Saving settings before starting bot...")
            try {
                await saveSettings()
                logWithTimestamp("[Home] Settings saved successfully, starting bot...")
            } catch (error) {
                logErrorWithTimestamp("[Home] Failed to save settings:", error)
                mlc.setMessageLog([...mlc.messageLog, `\n[ERROR] Failed to save settings before starting: ${error}`])
            }
            StartModule.start()
        } else {
            setShowNotReadyDialog(true)
        }
    }

    return (
        <View style={styles.root}>
            <View style={styles.buttonContainer}>
                <CustomButton variant={isRunning ? "destructive" : isDark ? "default" : "secondary"} onPress={handleButtonPress} isLoading={isRunning} style={styles.button}>
                    {isRunning ? "Stop" : bsc.readyStatus ? "Start" : "Not Ready"}
                </CustomButton>
            </View>

            <View style={styles.contentContainer}>
                <MessageLog />
            </View>

            <AlertDialog open={showNotReadyDialog} onOpenChange={setShowNotReadyDialog}>
                <AlertDialogContent onDismiss={() => setShowNotReadyDialog(false)}>
                    <AlertDialogHeader>
                        <AlertDialogTitle>Not Ready</AlertDialogTitle>
                        <AlertDialogDescription>A scenario must be selected before starting the bot. Please go to Settings to select a scenario.</AlertDialogDescription>
                    </AlertDialogHeader>
                    <AlertDialogFooter>
                        <AlertDialogAction onPress={() => setShowNotReadyDialog(false)}>
                            <Text>OK</Text>
                        </AlertDialogAction>
                    </AlertDialogFooter>
                </AlertDialogContent>
            </AlertDialog>
        </View>
    )
}

export default Home
