import { useContext } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation, DrawerActions } from "@react-navigation/native"
import { Divider } from "react-native-paper"
import { Ionicons } from "@expo/vector-icons"
import { useTheme } from "../../context/ThemeContext"
import NavigationLink from "../../components/NavigationLink"
import CustomSelect from "../../components/CustomSelect"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"

const RacingSettings = () => {
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)

    const openDrawer = () => {
        navigation.dispatch(DrawerActions.openDrawer())
    }

    const { settings, setSettings } = bsc

    // Merge current skills settings with defaults to handle missing properties.
    const skillSettings = { ...defaultSettings.skills, ...settings.skills }
    const {
        preferredRunningStyle,
        preferredTrackDistance,
        preferredTrackSurface,
    } = skillSettings

    const updateSkillsSetting = (key: string, value: any) => {
        setSettings({
            ...bsc.settings,
            skills: {
                ...bsc.settings.skills,
                [key]: value,
            },
        })
    }

    const styles = StyleSheet.create({
        root: {
            flex: 1,
            flexDirection: "column",
            justifyContent: "center",
            margin: 10,
            backgroundColor: colors.background,
        },
        header: {
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 20,
        },
        headerLeft: {
            flexDirection: "row",
            alignItems: "center",
            gap: 12,
        },
        menuButton: {
            padding: 8,
            borderRadius: 8,
        },
        title: {
            fontSize: 24,
            fontWeight: "bold",
            color: colors.foreground,
        },
        section: {
            marginBottom: 24,
        },
        sectionTitle: {
            fontSize: 18,
            fontWeight: "600",
            color: colors.foreground,
            marginBottom: 12,
        },
        inputContainer: {
            marginBottom: 16,
        },
        inputLabel: {
            fontSize: 16,
            color: colors.foreground,
            marginBottom: 8,
        },
        input: {
            borderWidth: 1,
            borderColor: colors.border,
            borderRadius: 8,
            padding: 12,
            fontSize: 16,
            color: colors.foreground,
            backgroundColor: colors.background,
        },
        inputDescription: {
            fontSize: 14,
            color: colors.foreground,
            opacity: 0.7,
            marginTop: 4,
        },
        warningContainer: {
            backgroundColor: colors.warningBg,
            borderLeftWidth: 4,
            borderLeftColor: colors.warningBorder,
            padding: 12,
            marginTop: 12,
            borderRadius: 8,
        },
        warningText: {
            fontSize: 14,
            color: colors.warningText,
            lineHeight: 20,
        },
    })

    return (
        <View style={styles.root}>
            <View style={styles.header}>
                <View style={styles.headerLeft}>
                    <TouchableOpacity onPress={openDrawer} style={styles.menuButton} activeOpacity={0.7}>
                        <Ionicons name="menu" size={28} color={colors.foreground} />
                    </TouchableOpacity>
                    <Text style={styles.title}>Skill Settings</Text>
                </View>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View style={styles.inputContainer}>
                    <Text style={styles.inputLabel}>Running Style Override</Text>
                    <CustomSelect
                        options={[
                            { value: "inherit", label: "Use Racing Setting" },
                            { value: "no_preference", label: "No Preference" },
                            { value: "front_runner", label: "Front Runner" },
                            { value: "pace_chaser", label: "Pace Chaser" },
                            { value: "late_surger", label: "Late Surger" },
                            { value: "end_closer", label: "End Closer" },
                        ]}
                        value={preferredRunningStyle}
                        defaultValue={defaultSettings.skills.preferredRunningStyle}
                        onValueChange={(value) => updateSkillsSetting("preferredRunningStyle", value)}
                        placeholder="Select Running Style"
                    />
                    <Text style={styles.inputDescription}>Overrides the preferred running style when determining which skills to purchase.</Text>
                    <Text style={styles.inputDescription}>When No Preference is selected, the bot will buy skills regardless of whether they match our trainee's aptitudes or the user-specified settings. Otherwise, the bot will only attempt to purchase skills that are running style dependent if they match this setting.</Text>
                </View>
                <View style={styles.inputContainer}>
                    <Text style={styles.inputLabel}>Track Distance Override</Text>
                    <CustomSelect
                        options={[
                            { value: "inherit", label: "Use Training Setting" },
                            { value: "no_preference", label: "No Preference" },
                            { value: "sprint", label: "Sprint" },
                            { value: "mile", label: "Mile" },
                            { value: "medium", label: "Medium" },
                            { value: "long", label: "Long" },
                        ]}
                        value={preferredTrackDistance}
                        defaultValue={defaultSettings.skills.preferredTrackDistance}
                        onValueChange={(value) => updateSkillsSetting("preferredTrackDistance", value)}
                        placeholder="Select Track Distance"
                    />
                    <Text style={styles.inputDescription}>Overrides the preferred track distance when determining which skills to purchase.</Text>
                    <Text style={styles.inputDescription}>When No Preference is selected, the bot will buy skills regardless of whether they match our trainee's aptitudes or the user-specified settings. Otherwise, the bot will only attempt to purchase skills that are track distance dependent if they match this setting.</Text>
                </View>
                <View style={styles.inputContainer}>
                    <Text style={styles.inputLabel}>Track Surface Override</Text>
                    <CustomSelect
                        options={[
                            { value: "no_preference", label: "No Preference" },
                            { value: "turf", label: "Turf" },
                            { value: "dirt", label: "Dirt" },
                        ]}
                        value={preferredTrackSurface}
                        defaultValue={defaultSettings.skills.preferredTrackSurface}
                        onValueChange={(value) => updateSkillsSetting("preferredTrackSurface", value)}
                        placeholder="Select Track Surface"
                    />
                    <Text style={styles.inputDescription}>Overrides the preferred track surface when determining which skills to purchase.</Text>
                    <Text style={styles.inputDescription}>When No Preference is selected, the bot will buy skills regardless of whether they match our trainee's aptitudes or the user-specified settings. Otherwise, the bot will only attempt to purchase skills that are track surface dependent if they match this setting.</Text>
                </View>
                <Divider style={{ marginBottom: 16 }} />
                <View className="m-1">
                    <NavigationLink
                        title="Go to Pre-Finals Skill Plan Settings"
                        description="Configure prioritized skills for purchasing just before the finale season."
                        onPress={() => navigation.navigate("SkillPlanPreFinalsSettings" as never)}
                        style={{ ...styles.section, marginTop: 0 }}
                    />
                    <NavigationLink
                        title="Go to Career Complete Skill Plan Settings"
                        description="Configure prioritized skills for purchasing upon career completion."
                        onPress={() => navigation.navigate("SkillPlanCareerCompleteSettings" as never)}
                        style={{ ...styles.section, marginTop: 0 }}
                    />
                </View>
            </ScrollView>
        </View>
    )
}

export default RacingSettings
