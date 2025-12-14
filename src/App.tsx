import { NavigationContainer } from "@react-navigation/native"
import { createDrawerNavigator } from "@react-navigation/drawer"
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
import ImportSettingsPreview from "./pages/ImportSettingsPreview"
import DrawerContent from "./components/DrawerContent"
import { NAV_THEME } from "./lib/theme"

export const Tag = "UAA"

const Drawer = createDrawerNavigator()

function MainDrawer() {
    const { colors } = useTheme()

    return (
        <Drawer.Navigator
            drawerContent={(props) => <DrawerContent {...props} />}
            screenOptions={{
                headerShown: false,
                drawerType: "front",
                drawerStyle: {
                    width: 280,
                    backgroundColor: colors.card,
                },
                drawerActiveTintColor: colors.primary,
                drawerInactiveTintColor: colors.foreground,
                overlayColor: "rgba(0, 0, 0, 0.5)",
            }}
        >
            <Drawer.Screen name="Home" component={Home} />
            <Drawer.Screen name="Settings" component={Settings} />
            <Drawer.Screen name="TrainingSettings" component={TrainingSettings} />
            <Drawer.Screen name="TrainingEventSettings" component={TrainingEventSettings} />
            <Drawer.Screen name="OCRSettings" component={OCRSettings} />
            <Drawer.Screen name="RacingSettings" component={RacingSettings} />
            <Drawer.Screen name="RacingPlanSettings" component={RacingPlanSettings} />
            <Drawer.Screen name="EventLogVisualizer" component={EventLogVisualizer} />
            <Drawer.Screen name="ImportSettingsPreview" component={ImportSettingsPreview} />
        </Drawer.Navigator>
    )
}

function AppWithBootstrap({ theme, colors }: { theme: string; colors: any }) {
    // Initialize app with bootstrap logic.
    useBootstrap()

    return (
        <SafeAreaView edges={["top"]} style={{ flex: 1, backgroundColor: colors.background }}>
            <NavigationContainer theme={NAV_THEME[theme as "light" | "dark"]}>
                <StatusBar style={theme === "light" ? "dark" : "light"} />
                <MainDrawer />
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
