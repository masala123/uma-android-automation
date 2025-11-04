import { NavigationContainer } from "@react-navigation/native"
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs"
import { createNativeStackNavigator } from "@react-navigation/native-stack"
import { Ionicons } from "@expo/vector-icons"
import { PortalHost } from "@rn-primitives/portal"
import { StatusBar } from "expo-status-bar"
import { SafeAreaView } from "react-native-safe-area-context"
import { BotStateProvider } from "./context/BotStateContext"
import { MessageLogProvider } from "./context/MessageLogContext"
import { SettingsProvider } from "./context/SettingsContext"
import { ThemeProvider, useTheme } from "./context/ThemeContext"
import { useBootstrap } from "./hooks/useBootstrap"
import Home from "./pages/Home"
import Settings from "./pages/Settings"
import TrainingSettings from "./pages/TrainingSettings"
import TrainingEventSettings from "./pages/TrainingEventSettings"
import OCRSettings from "./pages/OCRSettings"
import RacingSettings from "./pages/RacingSettings"
import RacingPlanSettings from "./pages/RacingPlanSettings"
import EventLogVisualizer from "./pages/EventLogVisualizer"
import { NAV_THEME } from "./lib/theme"

export const Tag = "UAA"

const Tab = createBottomTabNavigator()
const Stack = createNativeStackNavigator()

function SettingsStack() {
    return (
        <Stack.Navigator>
            <Stack.Screen name="SettingsMain" component={Settings} options={{ headerShown: false }} />
            <Stack.Screen name="TrainingSettings" component={TrainingSettings} options={{ headerShown: false }} />
            <Stack.Screen name="TrainingEventSettings" component={TrainingEventSettings} options={{ headerShown: false }} />
            <Stack.Screen name="OCRSettings" component={OCRSettings} options={{ headerShown: false }} />
            <Stack.Screen name="RacingSettings" component={RacingSettings} options={{ headerShown: false }} />
            <Stack.Screen name="RacingPlanSettings" component={RacingPlanSettings} options={{ headerShown: false }} />
            <Stack.Screen name="EventLogVisualizer" component={EventLogVisualizer} options={{ headerShown: false }} />
        </Stack.Navigator>
    )
}

function AppWithBootstrap({ theme, colors }: { theme: string; colors: any }) {
    // Initialize app with bootstrap logic.
    useBootstrap()

    return (
        <SafeAreaView edges={["top"]} style={{ flex: 1, backgroundColor: colors.background }}>
            <NavigationContainer theme={NAV_THEME[theme as "light" | "dark"]}>
                <StatusBar style={theme === "light" ? "dark" : "light"} />
                <Tab.Navigator
                    screenOptions={({ route }) => ({
                        tabBarIcon: ({ focused, size }: { focused: boolean; size: number }) => {
                            if (route.name === "Home") {
                                return <Ionicons name={focused ? "home" : "home-outline"} size={size} color={colors.primary} />
                            } else if (route.name === "Settings") {
                                return <Ionicons name={focused ? "settings" : "settings-outline"} size={size} color={colors.primary} />
                            }
                        },
                        headerShown: false,
                        animation: "fade",
                    })}
                >
                    <Tab.Screen name="Home" component={Home} />
                    <Tab.Screen name="Settings" component={SettingsStack} />
                </Tab.Navigator>
                <PortalHost />
            </NavigationContainer>
        </SafeAreaView>
    )
}

function AppContent() {
    const { theme, colors } = useTheme()

    return (
        <BotStateProvider>
            <MessageLogProvider>
                <SettingsProvider>
                    <AppWithBootstrap theme={theme} colors={colors} />
                </SettingsProvider>
            </MessageLogProvider>
        </BotStateProvider>
    )
}

function App() {
    return (
        <ThemeProvider>
            <AppContent />
        </ThemeProvider>
    )
}

export default App
