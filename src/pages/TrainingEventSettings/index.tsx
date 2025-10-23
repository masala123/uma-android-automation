import { useContext } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation } from "@react-navigation/native"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext } from "../../context/BotStateContext"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSelect from "../../components/CustomSelect"
import MultiSelector from "../../components/MultiSelector"
import { ArrowLeft } from "lucide-react-native"

// Import the data files.
import charactersData from "../../data/characters.json"
import supportsData from "../../data/supports.json"

const TrainingEventSettings = () => {
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)

    const { settings, setSettings } = bsc
    const { enablePrioritizeEnergyOptions, acupunctureOption } = settings.trainingEvent

    // Extract character and support names from the data.
    const characterNames = Object.keys(charactersData)
    const supportNames = Object.keys(supportsData)

    // Acupuncture event options.
    const acupunctureOptions = [
        { value: "Option 1: All stats +20", label: "Option 1: All stats +20" },
        { value: "Option 2: Get Corner and Straightaway Recovery skills", label: "Option 2: Get Corner and Straightaway Recovery skills" },
        { value: "Option 3: Energy recovery + Heal all negative status effects", label: "Option 3: Energy recovery + Heal all negative status effects" },
        { value: "Option 4: Get Charming status effect", label: "Option 4: Get Charming status effect" },
        { value: "Option 5: Energy +10", label: "Option 5: Energy +10" },
    ]

    const updateTrainingEventSetting = (key: keyof typeof settings.trainingEvent, value: any) => {
        setSettings({
            ...bsc.settings,
            trainingEvent: {
                ...bsc.settings.trainingEvent,
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
        title: {
            fontSize: 24,
            fontWeight: "bold",
            color: colors.foreground,
        },
        backButton: {
            padding: 8,
        },
        section: {
            marginBottom: 24,
        },
    })

    return (
        <View style={styles.root}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
                    <ArrowLeft size={24} color={colors.primary} />
                </TouchableOpacity>
                <Text style={styles.title}>Training Event Settings</Text>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    <View style={styles.section}>
                        <CustomCheckbox
                            id="prioritize-energy-options"
                            checked={enablePrioritizeEnergyOptions}
                            onCheckedChange={(checked) => updateTrainingEventSetting("enablePrioritizeEnergyOptions", checked)}
                            label="Prioritize Energy Options"
                            description="When enabled, the bot will prioritize training event choices that provide energy recovery or avoid energy consumption, helping to maintain optimal energy levels for training sessions."
                            className="my-2"
                        />
                    </View>

                    <View style={styles.section}>
                        <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 8 }}>
                            Acupuncture Event Option
                        </Text>
                        <Text style={{ fontSize: 14, color: colors.mutedForeground, marginBottom: 12 }}>
                            Select your preferred option for the Acupuncture event. Note: Options 1-4 have a 70%/55%/30%/15% percentage chance of failing in order, while Option 5 (Energy +10) is always successful.
                        </Text>
                        <CustomSelect
                            options={acupunctureOptions}
                            value={acupunctureOption}
                            onValueChange={(value) => updateTrainingEventSetting("acupunctureOption", value)}
                            placeholder="Select acupuncture option"
                            width="100%"
                        />
                    </View>

                    <MultiSelector
                        title="Character Selection"
                        description="Choose which characters you have in your current scenario. You can select all characters at once, or pick specific ones individually. When selecting individually, all rarity variants (R/SR/SSR) of the same character are grouped together."
                        options={characterNames}
                        selectedOptions={Object.keys(settings.trainingEvent.characterEventData)}
                        onSelectionChange={(selectedOptions) => {
                            // Create character event data for selected characters.
                            const characterEventData: Record<string, Record<string, string[]>> = {}
                            selectedOptions.forEach((characterName) => {
                                if (charactersData[characterName as keyof typeof charactersData]) {
                                    characterEventData[characterName] = charactersData[characterName as keyof typeof charactersData]
                                }
                            })

                            setSettings({
                                ...bsc.settings,
                                trainingEvent: {
                                    ...bsc.settings.trainingEvent,
                                    characterEventData,
                                    selectAllCharacters: selectedOptions.length === characterNames.length,
                                },
                            })
                        }}
                        selectAllLabel="Select All Characters"
                        selectAllDescription="Select all available characters for training events"
                        selectIndividualLabel="Select Characters"
                        selectAll={settings.trainingEvent.selectAllCharacters}
                    />

                    <MultiSelector
                        title="Support Card Selection"
                        description="Choose which support cards you have in your current scenario. Same selection behavior applies as above."
                        options={supportNames}
                        selectedOptions={Object.keys(settings.trainingEvent.supportEventData)}
                        onSelectionChange={(selectedOptions) => {
                            // Create support event data for selected supports.
                            const supportEventData: Record<string, Record<string, string[]>> = {}
                            selectedOptions.forEach((supportName) => {
                                if (supportsData[supportName as keyof typeof supportsData]) {
                                    supportEventData[supportName] = supportsData[supportName as keyof typeof supportsData]
                                }
                            })

                            setSettings({
                                ...bsc.settings,
                                trainingEvent: {
                                    ...bsc.settings.trainingEvent,
                                    supportEventData,
                                    selectAllSupportCards: selectedOptions.length === supportNames.length,
                                },
                            })
                        }}
                        selectAllLabel="Select All Support Cards"
                        selectAllDescription="Select all available support cards for training events"
                        selectIndividualLabel="Select Support Cards"
                        selectAll={settings.trainingEvent.selectAllSupportCards}
                    />
                </View>
            </ScrollView>
        </View>
    )
}

export default TrainingEventSettings
