import { useContext } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation } from "@react-navigation/native"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext } from "../../context/BotStateContext"
import CustomAccordion from "../../components/CustomAccordion"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomSelect from "../../components/CustomSelect"
import CustomTitle from "../../components/CustomTitle"
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
    const { enablePrioritizeEnergyOptions, specialEventOverrides } = settings.trainingEvent

    // Extract character and support names from the data.
    const characterNames = Object.keys(charactersData)
    const supportNames = Object.keys(supportsData)

    const acupunctureOptions = [
        { value: "Option 1: All stats +20", label: "Option 1: All stats +20" },
        { value: "Option 2: Get Corner and Straightaway Recovery skills", label: "Option 2: Get Corner and Straightaway Recovery skills" },
        { value: "Option 3: Energy recovery + Heal all negative status effects", label: "Option 3: Energy recovery + Heal all negative status effects" },
        { value: "Option 4: Get Charming status effect", label: "Option 4: Get Charming status effect" },
        { value: "Option 5: Energy +10", label: "Option 5: Energy +10" },
    ]
    const etsukoOptions = [
        { value: "Option 1: (Random) Energy Down / Mood -1 / Random stat increase / Gain skill points", label: "Option 1: (Random) Energy Down / Mood -1 / Random stat increase / Gain skill points" },
        { value: "Option 2: Energy Down / Gain skill points", label: "Option 2: Energy Down / Gain skill points" },
    ]
    const newYearResolutionsOptions = [
        { value: "Option 1: Stat +10", label: "Option 1: Stat +10" },
        { value: "Option 2: Energy +20", label: "Option 2: Energy +20" },
        { value: "Option 3: Skill points +20", label: "Option 3: Skill points +20" },
    ]
    const newYearShrineVisitOptions = [
        { value: "Option 1: Energy +30", label: "Option 1: Energy +30" },
        { value: "Option 2: All stats +5", label: "Option 2: All stats +5" },
        { value: "Option 3: Skill points +35", label: "Option 3: Skill points +35" },
    ]
    const victoryOptions = [
        { value: "Option 1: Energy -15 and random stat gain", label: "Option 1: Energy -15 and random stat gain" },
        { value: "Option 2: Energy -5 and random stat gain", label: "Option 2: Energy -5 and random stat gain" },
    ]
    const solidShowingOptions = [
        { value: "Option 1: Energy -15 and random stat gain", label: "Option 1: Energy -15 and random stat gain" },
        { value: "Option 2: Energy -5/-20 and random stat gain", label: "Option 2: Energy -5/-20 and random stat gain" },
    ]
    const defeatOptions = [
        { value: "Option 1: Energy -25 and random stat gain", label: "Option 1: Energy -25 and random stat gain" },
        { value: "Option 2: Energy -15/-35 and random stat gain", label: "Option 2: Energy -15/-35 and random stat gain" },
    ]
    const getWellSoonOptions = [
        { value: "Option 1: Mood -1 / Stat decrease / Get Practice Poor negative status", label: "Option 1: Mood -1 / Stat decrease / Get Practice Poor negative status" },
        { value: "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status", label: "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status" },
    ]
    const dontOverdoItOptions = [
        { value: "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status", label: "Option 1: Energy +10 / Mood -2 / Stat decrease / Get Practice Poor negative status" },
        { value: "Option 2: (Random) Mood -3 / Stat decrease / Get Practice Poor negative status", label: "Option 2: (Random) Mood -3 / Stat decrease / Get Practice Poor negative status" },
    ]
    const extraTrainingOptions = [
        { value: "Option 1: Energy -5 / Stat increase / (Random) Heal a negative status effect", label: "Option 1: Energy -5 / Stat increase / (Random) Heal a negative status effect" },
        { value: "Option 2: Energy +5", label: "Option 2: Energy +5" },
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

    const updateSpecialEventOverride = (eventName: string, field: "selectedOption" | "requiresConfirmation", value: any) => {
        setSettings({
            ...bsc.settings,
            trainingEvent: {
                ...bsc.settings.trainingEvent,
                specialEventOverrides: {
                    ...bsc.settings.trainingEvent.specialEventOverrides,
                    [eventName]: {
                        ...bsc.settings.trainingEvent.specialEventOverrides[eventName],
                        [field]: value,
                    },
                },
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

                    <CustomTitle
                        title="Special Event Overrides"
                        description="Override the bot's normal stat prioritization for specific training events. These settings bypass the standard weight calculation system."
                    />

                    <CustomAccordion
                        type="single"
                        style={{ marginBottom: 24 }}
                        sections={[
                            {
                                value: "holiday-events",
                                title: "Holiday Events",
                                children: (
                                    <View>
                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>New Year's Resolutions (Classic Year)</Text>
                                            <CustomSelect
                                                options={newYearResolutionsOptions}
                                                value={specialEventOverrides["New Year's Resolutions"]?.selectedOption || "Option 2: Energy +20"}
                                                onValueChange={(value) => updateSpecialEventOverride("New Year's Resolutions", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>

                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>New Year's Shrine Visit (Senior Year)</Text>
                                            <CustomSelect
                                                options={newYearShrineVisitOptions}
                                                value={specialEventOverrides["New Year's Shrine Visit"]?.selectedOption || "Option 1: Energy +30"}
                                                onValueChange={(value) => updateSpecialEventOverride("New Year's Shrine Visit", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>
                                    </View>
                                ),
                            },
                            {
                                value: "race-results",
                                title: "Race Result Events",
                                children: (
                                    <View>
                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Victory!</Text>
                                            <CustomSelect
                                                options={victoryOptions}
                                                value={specialEventOverrides["Victory!"]?.selectedOption || "Option 2: Energy -5 and random stat gain"}
                                                onValueChange={(value) => updateSpecialEventOverride("Victory!", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>

                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Solid Showing</Text>
                                            <CustomSelect
                                                options={solidShowingOptions}
                                                value={specialEventOverrides["Solid Showing"]?.selectedOption || "Option 2: Energy -5/-20 and random stat gain"}
                                                onValueChange={(value) => updateSpecialEventOverride("Solid Showing", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>

                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Defeat</Text>
                                            <CustomSelect
                                                options={defeatOptions}
                                                value={specialEventOverrides["Defeat"]?.selectedOption || "Option 1: Energy -25 and random stat gain"}
                                                onValueChange={(value) => updateSpecialEventOverride("Defeat", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>
                                    </View>
                                ),
                            },
                            {
                                value: "training-failures",
                                title: "Training Failure Events",
                                children: (
                                    <View>
                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Get Well Soon!</Text>
                                            <CustomSelect
                                                options={getWellSoonOptions}
                                                value={
                                                    specialEventOverrides["Get Well Soon!"]?.selectedOption ||
                                                    "Option 2: (Random) Mood -1 / Stat decrease / Get Practice Poor negative status"
                                                }
                                                onValueChange={(value) => updateSpecialEventOverride("Get Well Soon!", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>

                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Don't Overdo It!</Text>
                                            <CustomSelect
                                                options={dontOverdoItOptions}
                                                value={
                                                    specialEventOverrides["Don't Overdo It!"]?.selectedOption ||
                                                    "Option 2: (Random) Mood -3 / Stat decrease / Get Practice Poor negative status"
                                                }
                                                onValueChange={(value) => updateSpecialEventOverride("Don't Overdo It!", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>
                                    </View>
                                ),
                            },
                            {
                                value: "miscellaneous",
                                title: "Miscellaneous Events",
                                children: (
                                    <View>
                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Extra Training</Text>
                                            <CustomSelect
                                                options={extraTrainingOptions}
                                                value={specialEventOverrides["Extra Training"]?.selectedOption || "Option 2: Energy +5"}
                                                onValueChange={(value) => updateSpecialEventOverride("Extra Training", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>

                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 8 }}>Acupuncture (Just an Acupuncturist, No Worries! ☆)</Text>
                                            <Text style={{ fontSize: 14, color: colors.mutedForeground, marginBottom: 12 }}>
                                                Select your preferred option for the Acupuncture event. Note: Options 1-4 have a 70%/55%/30%/15% chance to fail, while Option 5 will always succeed.
                                            </Text>
                                            <CustomSelect
                                                options={acupunctureOptions}
                                                value={specialEventOverrides["Acupuncture (Just an Acupuncturist, No Worries! ☆)"]?.selectedOption || "Option 5: Energy +10"}
                                                onValueChange={(value) => updateSpecialEventOverride("Acupuncture (Just an Acupuncturist, No Worries! ☆)", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>

                                        <View style={styles.section}>
                                            <Text style={{ fontSize: 16, fontWeight: "600", color: colors.foreground, marginBottom: 12 }}>Etsuko's Exhaustive Coverage</Text>
                                            <CustomSelect
                                                options={etsukoOptions}
                                                value={specialEventOverrides["Etsuko's Exhaustive Coverage"]?.selectedOption || "Option 2: Energy Down / Gain skill points"}
                                                onValueChange={(value) => updateSpecialEventOverride("Etsuko's Exhaustive Coverage", "selectedOption", value)}
                                                placeholder="Select Option"
                                                width="100%"
                                            />
                                        </View>
                                    </View>
                                ),
                            },
                        ]}
                    />

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
