import { useContext } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation, DrawerActions } from "@react-navigation/native"
import { Ionicons } from "@expo/vector-icons"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"

const OCRSettings = () => {
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)

    const openDrawer = () => {
        navigation.dispatch(DrawerActions.openDrawer())
    }

    const { settings, setSettings } = bsc
    // Merge current OCR settings with defaults to handle missing properties.
    const ocrSettings = { ...defaultSettings.ocr, ...settings.ocr }
    const { ocrThreshold, enableAutomaticOCRRetry, ocrConfidence } = ocrSettings

    const updateOCRSetting = (key: keyof typeof settings.ocr, value: any) => {
        setSettings({
            ...bsc.settings,
            ocr: {
                ...bsc.settings.ocr,
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
    })

    return (
        <View style={styles.root}>
            <View style={styles.header}>
                <View style={styles.headerLeft}>
                    <TouchableOpacity onPress={openDrawer} style={styles.menuButton} activeOpacity={0.7}>
                        <Ionicons name="menu" size={28} color={colors.foreground} />
                    </TouchableOpacity>
                    <Text style={styles.title}>OCR Settings</Text>
                </View>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    <View style={styles.section}>
                        <CustomSlider
                            value={ocrThreshold}
                            placeholder={bsc.defaultSettings.ocr.ocrThreshold}
                            onValueChange={(value) => updateOCRSetting("ocrThreshold", value)}
                            min={100}
                            max={255}
                            step={5}
                            label="OCR Threshold"
                            labelUnit=""
                            showValue={true}
                            showLabels={true}
                            description="Adjust the threshold for OCR text detection. Higher values make text detection more strict, lower values make it more lenient."
                        />
                    </View>

                    <View style={styles.section}>
                        <CustomCheckbox
                            id="enable-automatic-ocr-retry"
                            checked={enableAutomaticOCRRetry}
                            onCheckedChange={(checked) => updateOCRSetting("enableAutomaticOCRRetry", checked)}
                            label="Enable Automatic OCR Retry"
                            description="When enabled, the bot will automatically retry OCR detection if the initial attempt fails or has low confidence."
                            className="my-2"
                        />
                    </View>

                    <View style={styles.section}>
                        <CustomSlider
                            value={ocrConfidence}
                            placeholder={bsc.defaultSettings.ocr.ocrConfidence}
                            onValueChange={(value) => updateOCRSetting("ocrConfidence", value)}
                            min={50}
                            max={100}
                            step={1}
                            label="OCR Confidence"
                            labelUnit="%"
                            showValue={true}
                            showLabels={true}
                            description="Set the minimum confidence level required for OCR text detection. Higher values ensure more accurate text recognition but may miss some text."
                        />
                    </View>

                    <View style={styles.section}>
                        <CustomCheckbox
                            checked={bsc.settings.debug.enableHideOCRComparisonResults}
                            onCheckedChange={(checked) => {
                                bsc.setSettings({
                                    ...bsc.settings,
                                    debug: { ...bsc.settings.debug, enableHideOCRComparisonResults: checked },
                                })
                            }}
                            label="Hide OCR String Comparison Results during Training Event detection"
                            description="Hides the log messages involved in the string comparison process during training event detection."
                            style={{ marginTop: 10 }}
                        />
                    </View>
                </View>
            </ScrollView>
        </View>
    )
}

export default OCRSettings
