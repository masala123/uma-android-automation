import React, { useContext, useEffect, useState } from "react"
import { View, Text, ScrollView, StyleSheet, Modal, TouchableOpacity, Dimensions } from "react-native"
import { useNavigation } from "@react-navigation/native"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext } from "../../context/BotStateContext"
import CustomButton from "../../components/CustomButton"
import CustomSlider from "../../components/CustomSlider"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomTitle from "../../components/CustomTitle"
import DraggablePriorityList from "../../components/DraggablePriorityList"
import CustomAccordion from "../../components/CustomAccordion"
import CustomSelect from "../../components/CustomSelect"
import { ArrowLeft } from "lucide-react-native"

const TrainingSettings = () => {
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)
    const [blacklistModalVisible, setBlacklistModalVisible] = useState(false)
    const [prioritizationModalVisible, setPrioritizationModalVisible] = useState(false)

    const { settings, setSettings, defaultSettings } = bsc
    const { maximumFailureChance, disableTrainingOnMaxedStat, focusOnSparkStatTarget, preferredDistanceOverride, mustRestBeforeSummer } = settings.training

    const [statPrioritizationItems, setStatPrioritizationItems] = useState<string[]>(settings.training.statPrioritization.length > 0 ? settings.training.statPrioritization : defaultSettings.training.statPrioritization)
    const [blacklistItems, setBlacklistItems] = useState<string[]>(settings.training.trainingBlacklist.length > 0 ? settings.training.trainingBlacklist : defaultSettings.training.trainingBlacklist)

    useEffect(() => {
        updateTrainingSetting("statPrioritization", statPrioritizationItems)
    }, [statPrioritizationItems])

    useEffect(() => {
        updateTrainingSetting("trainingBlacklist", blacklistItems)
    }, [blacklistItems])

    const updateTrainingSetting = (key: keyof typeof settings.training, value: any) => {
        setSettings({
            ...bsc.settings,
            training: {
                ...bsc.settings.training,
                [key]: value,
            },
        })
    }

    const updateTrainingStatTarget = (key: keyof typeof settings.trainingStatTarget, value: any) => {
        setSettings({
            ...bsc.settings,
            trainingStatTarget: {
                ...bsc.settings.trainingStatTarget,
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
        backText: {
            fontSize: 18,
            color: colors.primary,
            fontWeight: "600",
        },
        section: {
            marginBottom: 24,
        },

        row: {
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 16,
        },
        label: {
            fontSize: 16,
            color: colors.foreground,
            flex: 1,
        },
        pressableText: {
            fontSize: 16,
            color: colors.primary,
            textDecorationLine: "underline",
        },
        modal: {
            flex: 1,
            justifyContent: "center",
            alignItems: "center",
            backgroundColor: "rgba(70, 70, 70, 0.5)",
        },
        modalContent: {
            backgroundColor: colors.background,
            borderRadius: 12,
            padding: 20,
            width: Dimensions.get("window").width * 0.85,
            maxHeight: Dimensions.get("window").height * 0.7,
        },
        modalHeader: {
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 20,
        },
        modalTitle: {
            fontSize: 20,
            fontWeight: "bold",
            color: colors.foreground,
        },
        closeButton: {
            padding: 8,
        },
        closeText: {
            fontSize: 18,
            color: colors.primary,
        },
        statItem: {
            flexDirection: "row",
            alignItems: "center",
            marginBottom: 12,
        },
        statCheckbox: {
            marginRight: 12,
        },
        statLabel: {
            fontSize: 16,
            color: colors.foreground,
            flex: 1,
        },
        buttonRow: {
            flexDirection: "row",
            justifyContent: "space-between",
            marginTop: 20,
        },
    })

    const toggleStat = (stat: string, list: string[], setList: (value: string[]) => void) => {
        if (list.includes(stat)) {
            setList(list.filter((s) => s !== stat))
        } else {
            setList([...list, stat])
        }
    }

    const clearAll = (setList: (value: string[]) => void) => {
        setList([])
    }

    const selectAll = (setList: (value: string[]) => void, currentList: string[]) => {
        // Add any missing items from default settings to the current list, preserving order.
        const missingItems = defaultSettings.training.statPrioritization.filter(
            (stat) => !currentList.includes(stat)
        )
        setList([...currentList, ...missingItems])
    }

    const renderStatSelector = (
        title: string,
        selectedStats: string[],
        setSelectedStats: (value: string[]) => void,
        modalVisible: boolean,
        setModalVisible: React.Dispatch<React.SetStateAction<boolean>>,
        description?: string,
        mode: "checkbox" | "priority" = "checkbox"
    ) => (
        <View style={styles.section}>
            <View style={styles.row}>
                <Text style={styles.label}>{title}</Text>
                <TouchableOpacity onPress={() => setModalVisible(true)}>
                    <Text style={styles.pressableText}>{selectedStats.length === 0 ? "None" : selectedStats.join(", ")}</Text>
                </TouchableOpacity>
            </View>
            {description && <Text style={[styles.label, { fontSize: 14, color: colors.foreground, opacity: 0.7, marginTop: 4 }]}>{description}</Text>}

            <Modal visible={modalVisible} transparent={true} animationType="fade" onRequestClose={() => setModalVisible(false)}>
                <TouchableOpacity style={styles.modal} activeOpacity={1} onPress={() => setModalVisible(false)}>
                    <TouchableOpacity style={styles.modalContent} activeOpacity={1} onPress={(e) => e.stopPropagation()}>
                        <View style={styles.modalHeader}>
                            <Text style={styles.modalTitle}>{title}</Text>
                            <TouchableOpacity style={styles.closeButton} onPress={() => setModalVisible(false)}>
                                <Text style={styles.closeText}>✕</Text>
                            </TouchableOpacity>
                        </View>

                        {mode === "priority" ? (
                            <DraggablePriorityList
                                items={defaultSettings.training.statPrioritization.map((stat) => ({
                                    id: stat,
                                    label: stat,
                                }))}
                                selectedItems={selectedStats}
                                onSelectionChange={setSelectedStats}
                                onOrderChange={(orderedItems) => {
                                    // Update the order when items are reordered.
                                    setSelectedStats(orderedItems)
                                }}
                            />
                        ) : (
                            defaultSettings.training.statPrioritization.map((stat) => (
                                <CustomCheckbox
                                    key={stat}
                                    id={`stat-${stat.toLowerCase()}`}
                                    checked={selectedStats.includes(stat)}
                                    onCheckedChange={() => toggleStat(stat, selectedStats, setSelectedStats)}
                                    label={stat}
                                    className="my-2"
                                />
                            ))
                        )}

                        <View style={styles.buttonRow}>
                            <CustomButton
                                onPress={() => {
                                    if (mode === "priority") {
                                        // For prioritization, reset to default and dismiss modal.
                                        setSelectedStats(defaultSettings.training.statPrioritization)
                                        setModalVisible(false)
                                    } else {
                                        // For blacklist, just clear the list.
                                        clearAll(setSelectedStats)
                                    }
                                }}
                                variant="destructive"
                            >
                                Clear All
                            </CustomButton>
                            <CustomButton onPress={() => selectAll(setSelectedStats, selectedStats)} variant="outline">
                                Select All
                            </CustomButton>
                        </View>
                    </TouchableOpacity>
                </TouchableOpacity>
            </Modal>
        </View>
    )

    return (
        <View style={styles.root}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
                    <ArrowLeft size={24} color={colors.primary} />
                </TouchableOpacity>
                <Text style={styles.title}>Training Settings</Text>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    {renderStatSelector(
                        "Blacklist",
                        blacklistItems,
                        (value) => setBlacklistItems(value),
                        blacklistModalVisible,
                        setBlacklistModalVisible,
                        "Select which stats to exclude from training. These stats will be skipped during training sessions.",
                        "checkbox"
                    )}

                    {renderStatSelector(
                        "Prioritization",
                        statPrioritizationItems,
                        (value) => setStatPrioritizationItems(value),
                        prioritizationModalVisible,
                        setPrioritizationModalVisible,
                        "Select the priority order of the stats. The stats will be trained in the order they are selected. If none are selected, then the default order will be used.",
                        "priority"
                    )}

                    <View style={styles.section}>
                        <CustomCheckbox
                            id="disable-training-on-maxed-stats"
                            checked={disableTrainingOnMaxedStat}
                            onCheckedChange={(checked) => updateTrainingSetting("disableTrainingOnMaxedStat", checked)}
                            label="Disable Training on Maxed Stats"
                            description="When enabled, training will be skipped for stats that have reached their maximum value."
                            className="my-2"
                        />
                    </View>

                    <View style={styles.section}>
                        <CustomSlider
                            value={maximumFailureChance}
                            placeholder={defaultSettings.training.maximumFailureChance}
                            onValueChange={(value) => updateTrainingSetting("maximumFailureChance", value)}
                            min={5}
                            max={95}
                            step={5}
                            label="Set Maximum Failure Chance"
                            labelUnit="%"
                            showValue={true}
                            showLabels={true}
                            description="Set the maximum acceptable failure chance for training sessions. Training with higher failure rates will be avoided."
                        />
                    </View>

                    <View style={styles.section}>
                        <CustomCheckbox
                            id="focus-on-spark-stat-targets"
                            checked={focusOnSparkStatTarget}
                            onCheckedChange={(checked) => updateTrainingSetting("focusOnSparkStatTarget", checked)}
                            label="Focus on Sparks for Stat Targets"
                            description="When enabled, the bot will prioritize training sessions that have a chance to trigger spark events for stats that are below their target values."
                            className="my-2"
                        />
                    </View>

                    <View style={styles.section}>
                        <CustomCheckbox
                            id="must-rest-before-summer"
                            checked={mustRestBeforeSummer}
                            onCheckedChange={(checked) => updateTrainingSetting("mustRestBeforeSummer", checked)}
                            label="Must Rest before Summer"
                            description="Forces the bot to rest during June Early and Late phases in Classic and Senior Years to ensure full energy for Summer Training in July."
                            className="my-2"
                        />
                    </View>

                    <View style={styles.section}>
                        <View style={styles.row}>
                            <Text style={styles.label}>Preferred Distance Override</Text>
                            <CustomSelect
                                value={preferredDistanceOverride}
                                onValueChange={(value) => updateTrainingSetting("preferredDistanceOverride", value)}
                                options={[
                                    { label: "Auto", value: "Auto" },
                                    { label: "Sprint", value: "Sprint" },
                                    { label: "Mile", value: "Mile" },
                                    { label: "Medium", value: "Medium" },
                                    { label: "Long", value: "Long" },
                                ]}
                                placeholder="Select distance"
                                width={200}
                            />
                        </View>
                        <Text style={[styles.label, { fontSize: 14, color: colors.foreground, opacity: 0.7, marginTop: 4 }]}>
                            Set the preferred race distance for training targets. "Auto" will automatically determine based on character aptitudes reading from left to right (S {">"} A priority).
                        </Text>
                    </View>

                    {/* Stat Target Settings */}
                    <View style={styles.section}>
                        <CustomTitle
                            title="Stat Targets by Distance"
                            description="Set target values for each stat based on race distance. The bot will prioritize training stats that are below these targets."
                        />
                    </View>

                    {/* Distance Stat Targets Accordion */}
                    <CustomAccordion
                        type="single"
                        sections={[
                            {
                                value: "sprint",
                                title: "Sprint Distance",
                                children: (
                                    <>
                                        <CustomSlider
                                            value={settings.trainingStatTarget.trainingSprintStatTarget_speedStatTarget}
                                            placeholder={defaultSettings.trainingStatTarget.trainingSprintStatTarget_speedStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingSprintStatTarget_speedStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Sprint Speed Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingSprintStatTarget_staminaStatTarget}
                                            value={settings.trainingStatTarget.trainingSprintStatTarget_staminaStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingSprintStatTarget_staminaStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Sprint Stamina Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingSprintStatTarget_powerStatTarget}
                                            value={settings.trainingStatTarget.trainingSprintStatTarget_powerStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingSprintStatTarget_powerStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Sprint Power Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingSprintStatTarget_gutsStatTarget}
                                            value={settings.trainingStatTarget.trainingSprintStatTarget_gutsStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingSprintStatTarget_gutsStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Sprint Guts Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingSprintStatTarget_witStatTarget}
                                            value={settings.trainingStatTarget.trainingSprintStatTarget_witStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingSprintStatTarget_witStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Sprint Wit Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                    </>
                                ),
                            },
                            {
                                value: "mile",
                                title: "Mile Distance",
                                children: (
                                    <>
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMileStatTarget_speedStatTarget}
                                            value={settings.trainingStatTarget.trainingMileStatTarget_speedStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMileStatTarget_speedStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Mile Speed Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMileStatTarget_staminaStatTarget}
                                            value={settings.trainingStatTarget.trainingMileStatTarget_staminaStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMileStatTarget_staminaStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Mile Stamina Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMileStatTarget_powerStatTarget}
                                            value={settings.trainingStatTarget.trainingMileStatTarget_powerStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMileStatTarget_powerStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Mile Power Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMileStatTarget_gutsStatTarget}
                                            value={settings.trainingStatTarget.trainingMileStatTarget_gutsStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMileStatTarget_gutsStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Mile Guts Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMileStatTarget_witStatTarget}
                                            value={settings.trainingStatTarget.trainingMileStatTarget_witStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMileStatTarget_witStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Mile Wit Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                    </>
                                ),
                            },
                            {
                                value: "medium",
                                title: "Medium Distance",
                                children: (
                                    <>
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMediumStatTarget_speedStatTarget}
                                            value={settings.trainingStatTarget.trainingMediumStatTarget_speedStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMediumStatTarget_speedStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Medium Speed Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMediumStatTarget_staminaStatTarget}
                                            value={settings.trainingStatTarget.trainingMediumStatTarget_staminaStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMediumStatTarget_staminaStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Medium Stamina Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMediumStatTarget_powerStatTarget}
                                            value={settings.trainingStatTarget.trainingMediumStatTarget_powerStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMediumStatTarget_powerStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Medium Power Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMediumStatTarget_gutsStatTarget}
                                            value={settings.trainingStatTarget.trainingMediumStatTarget_gutsStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMediumStatTarget_gutsStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Medium Guts Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingMediumStatTarget_witStatTarget}
                                            value={settings.trainingStatTarget.trainingMediumStatTarget_witStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingMediumStatTarget_witStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Medium Wit Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                    </>
                                ),
                            },
                            {
                                value: "long",
                                title: "Long Distance",
                                children: (
                                    <>
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingLongStatTarget_speedStatTarget}
                                            value={settings.trainingStatTarget.trainingLongStatTarget_speedStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingLongStatTarget_speedStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Long Speed Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingLongStatTarget_staminaStatTarget}
                                            value={settings.trainingStatTarget.trainingLongStatTarget_staminaStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingLongStatTarget_staminaStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Long Stamina Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingLongStatTarget_powerStatTarget}
                                            value={settings.trainingStatTarget.trainingLongStatTarget_powerStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingLongStatTarget_powerStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Long Power Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingLongStatTarget_gutsStatTarget}
                                            value={settings.trainingStatTarget.trainingLongStatTarget_gutsStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingLongStatTarget_gutsStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Long Guts Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                        <CustomSlider
                                            placeholder={defaultSettings.trainingStatTarget.trainingLongStatTarget_witStatTarget}
                                            value={settings.trainingStatTarget.trainingLongStatTarget_witStatTarget}
                                            onValueChange={(value) => updateTrainingStatTarget("trainingLongStatTarget_witStatTarget", value)}
                                            min={100}
                                            max={1200}
                                            step={10}
                                            label="Long Wit Target"
                                            labelUnit=""
                                            showValue={true}
                                            showLabels={true}
                                        />
                                    </>
                                ),
                            },
                        ]}
                    />
                </View>
            </ScrollView>
        </View>
    )
}

export default TrainingSettings
