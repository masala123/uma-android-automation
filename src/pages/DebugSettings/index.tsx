import { useContext } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation, DrawerActions } from "@react-navigation/native"
import { Ionicons } from "@expo/vector-icons"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext } from "../../context/BotStateContext"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomTitle from "../../components/CustomTitle"
import { Separator } from "../../components/ui/separator"

const DebugSettings = () => {
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)

    const openDrawer = () => {
        navigation.dispatch(DrawerActions.openDrawer())
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
        errorContainer: {
            backgroundColor: colors.warningBg,
            borderLeftWidth: 4,
            borderLeftColor: colors.warningBorder,
            padding: 12,
            marginTop: 8,
            borderRadius: 8,
        },
        errorText: {
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
                    <Text style={styles.title}>Debug Settings</Text>
                </View>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    <View style={{ marginTop: 16 }}>
                        <CustomTitle title="Debug Settings" description="Debug mode, template matching settings, and diagnostic tests for bot troubleshooting." />

                        <CustomCheckbox
                            checked={bsc.settings.debug.enableDebugMode}
                            onCheckedChange={(checked) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    debug: { ...bsc.settings.debug, enableDebugMode: checked },
                                })
                            }}
                            label="Enable Debug Mode"
                            description="Allows debugging messages in the log and test images to be created in the /temp/ folder."
                        />

                        {bsc.settings.debug.enableDebugMode && (
                            <View style={[styles.errorContainer, { marginTop: 8 }]}>
                                <Text style={styles.errorText}>⚠️ Significantly extends the average runtime of the bot due to increased IO operations.</Text>
                            </View>
                        )}

                        <CustomSlider
                            value={bsc.settings.debug.templateMatchConfidence}
                            placeholder={bsc.defaultSettings.debug.templateMatchConfidence}
                            onValueChange={(value) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    debug: { ...bsc.settings.debug, templateMatchConfidence: value },
                                })
                            }}
                            onSlidingComplete={(value) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    debug: { ...bsc.settings.debug, templateMatchConfidence: value },
                                })
                            }}
                            min={0.5}
                            max={1.0}
                            step={0.01}
                            label="Adjust Confidence for Template Matching"
                            labelUnit=""
                            showValue={true}
                            showLabels={true}
                            description="Sets the minimum confidence level for template matching with 1080p as the baseline. Consider lowering this to something like 0.7 or 70% at lower resolutions. Making it too low will cause the bot to match on too many things as false positives."
                        />

                        <CustomSlider
                            value={bsc.settings.debug.templateMatchCustomScale}
                            placeholder={bsc.defaultSettings.debug.templateMatchCustomScale}
                            onValueChange={(value) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    debug: { ...bsc.settings.debug, templateMatchCustomScale: value },
                                })
                            }}
                            onSlidingComplete={(value) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    debug: { ...bsc.settings.debug, templateMatchCustomScale: value },
                                })
                            }}
                            min={0.5}
                            max={3.0}
                            step={0.01}
                            label="Set the Custom Image Scale for Template Matching"
                            labelUnit=""
                            showValue={true}
                            showLabels={true}
                            description="Manually set the scale to do template matching. The Basic Template Matching Test can help find your recommended scale. Making it too low or too high will cause the bot to match on too little or too many things as false positives."
                        />

                        <Separator style={{ marginVertical: 16 }} />

                        <CustomTitle title="Debug Tests" description="Run diagnostic tests to verify template matching and OCR functionality. Only one test can be enabled at a time." />

                        {/* Warning message for debug tests */}
                        <View style={[styles.errorContainer, { marginBottom: 16 }]}>
                            <Text style={styles.errorText}>
                                {"⚠️ Only one debug test can be enabled at a time. Enabling a test will automatically disable the others.\n\nHaving Debug Mode enabled will output more helpful logs."}
                            </Text>
                        </View>

                        <CustomCheckbox
                            checked={bsc.settings.debug.debugMode_startTemplateMatchingTest}
                            onCheckedChange={(checked) => {
                                if (checked) {
                                    // Disable other tests when enabling this one.
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: {
                                            ...bsc.settings.debug,
                                            debugMode_startTemplateMatchingTest: true,
                                            debugMode_startSingleTrainingOCRTest: false,
                                            debugMode_startComprehensiveTrainingOCRTest: false,
                                            debugMode_startDateOCRTest: false,
                                            debugMode_startRaceListDetectionTest: false,
                                            debugMode_startAptitudesDetectionTest: false,
                                        },
                                    })
                                } else {
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: { ...bsc.settings.debug, debugMode_startTemplateMatchingTest: false },
                                    })
                                }
                            }}
                            label="Start Basic Template Matching Test"
                            description="Disables normal bot operations and starts the template match test. Only on the Home screen and will check if it can find certain essential buttons on the screen. It will also output what scale it had the most success with."
                            style={{ marginTop: 10 }}
                        />

                        <CustomCheckbox
                            checked={bsc.settings.debug.debugMode_startSingleTrainingOCRTest}
                            onCheckedChange={(checked) => {
                                if (checked) {
                                    // Disable other tests when enabling this one.
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: {
                                            ...bsc.settings.debug,
                                            debugMode_startTemplateMatchingTest: false,
                                            debugMode_startSingleTrainingOCRTest: true,
                                            debugMode_startComprehensiveTrainingOCRTest: false,
                                            debugMode_startDateOCRTest: false,
                                            debugMode_startRaceListDetectionTest: false,
                                            debugMode_startAptitudesDetectionTest: false,
                                        },
                                    })
                                } else {
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: { ...bsc.settings.debug, debugMode_startSingleTrainingOCRTest: false },
                                    })
                                }
                            }}
                            label="Start Single Training OCR Test"
                            description="Disables normal bot operations and starts the single training OCR test. Only on the Training screen and tests the current training on display for stat gains and failure chances."
                            style={{ marginTop: 10 }}
                        />

                        <CustomCheckbox
                            checked={bsc.settings.debug.debugMode_startComprehensiveTrainingOCRTest}
                            onCheckedChange={(checked) => {
                                if (checked) {
                                    // Disable other tests when enabling this one.
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: {
                                            ...bsc.settings.debug,
                                            debugMode_startTemplateMatchingTest: false,
                                            debugMode_startSingleTrainingOCRTest: false,
                                            debugMode_startComprehensiveTrainingOCRTest: true,
                                            debugMode_startDateOCRTest: false,
                                            debugMode_startRaceListDetectionTest: false,
                                            debugMode_startAptitudesDetectionTest: false,
                                        },
                                    })
                                } else {
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: { ...bsc.settings.debug, debugMode_startComprehensiveTrainingOCRTest: false },
                                    })
                                }
                            }}
                            label="Start Comprehensive Training OCR Test"
                            description="Disables normal bot operations and starts the comprehensive training OCR test. Only on the Training screen and tests all 5 trainings for their stat gains and failure chances."
                            style={{ marginTop: 10 }}
                        />

                        <CustomCheckbox
                            checked={bsc.settings.debug.debugMode_startDateOCRTest}
                            onCheckedChange={(checked) => {
                                if (checked) {
                                    // Disable other tests when enabling this one.
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: {
                                            ...bsc.settings.debug,
                                            debugMode_startTemplateMatchingTest: false,
                                            debugMode_startSingleTrainingOCRTest: false,
                                            debugMode_startComprehensiveTrainingOCRTest: false,
                                            debugMode_startDateOCRTest: true,
                                            debugMode_startRaceListDetectionTest: false,
                                            debugMode_startAptitudesDetectionTest: false,
                                        },
                                    })
                                } else {
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: { ...bsc.settings.debug, debugMode_startDateOCRTest: false },
                                    })
                                }
                            }}
                            label="Start Date OCR Test"
                            description="Disables normal bot operations and starts the date OCR test. Only on the Main screen and the Race List screen and tests detecting the current date."
                            style={{ marginTop: 10 }}
                        />

                        <CustomCheckbox
                            checked={bsc.settings.debug.debugMode_startRaceListDetectionTest}
                            onCheckedChange={(checked) => {
                                if (checked) {
                                    // Disable other tests when enabling this one.
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: {
                                            ...bsc.settings.debug,
                                            debugMode_startTemplateMatchingTest: false,
                                            debugMode_startSingleTrainingOCRTest: false,
                                            debugMode_startComprehensiveTrainingOCRTest: false,
                                            debugMode_startDateOCRTest: false,
                                            debugMode_startRaceListDetectionTest: true,
                                            debugMode_startAptitudesDetectionTest: false,
                                        },
                                    })
                                } else {
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: { ...bsc.settings.debug, debugMode_startRaceListDetectionTest: false },
                                    })
                                }
                            }}
                            label="Start Race List Detection Test"
                            description="Disables normal bot operations and starts the Race List detection test. Only on the Race List screen and tests detecting the races with double star predictions currently on display."
                            style={{ marginTop: 10 }}
                        />

                        <CustomCheckbox
                            checked={bsc.settings.debug.debugMode_startAptitudesDetectionTest}
                            onCheckedChange={(checked) => {
                                if (checked) {
                                    // Disable other tests when enabling this one.
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: {
                                            ...bsc.settings.debug,
                                            debugMode_startTemplateMatchingTest: false,
                                            debugMode_startSingleTrainingOCRTest: false,
                                            debugMode_startComprehensiveTrainingOCRTest: false,
                                            debugMode_startDateOCRTest: false,
                                            debugMode_startRaceListDetectionTest: false,
                                            debugMode_startAptitudesDetectionTest: true,
                                        },
                                    })
                                } else {
                                    bsc.setSettings({
                                        ...bsc.settings,
                                        debug: { ...bsc.settings.debug, debugMode_startAptitudesDetectionTest: false },
                                    })
                                }
                            }}
                            label="Start Aptitudes Detection Test"
                            description="Disables normal bot operations and starts the Aptitudes detection test. Only on the Main screen and tests detecting the current aptitudes."
                            style={{ marginTop: 10 }}
                        />
                    </View>
                </View>
            </ScrollView>
        </View>
    )
}

export default DebugSettings
