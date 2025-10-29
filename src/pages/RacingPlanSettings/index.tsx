import { useContext, useState } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity } from "react-native"
import { useNavigation } from "@react-navigation/native"
import { Divider } from "react-native-paper"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext } from "../../context/BotStateContext"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomButton from "../../components/CustomButton"
import CustomScrollView from "../../components/CustomScrollView"
import CustomTitle from "../../components/CustomTitle"
import { Input } from "../../components/ui/input"
import { ArrowLeft, CircleCheckBig, Plus, Trash2 } from "lucide-react-native"
import racesData from "../../data/races.json"

interface Race {
    name: string
    date: string
    grade: string
    terrain: string
    distanceType: string
    distanceMeters: number
    fans: number
}

interface PlannedRace {
    raceName: string
    date: string
    priority: number
}

const RacingPlanSettings = () => {
    const { colors } = useTheme()
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)
    const { settings, setSettings } = bsc

    const { enableRacingPlan, racingPlan, minFansThreshold, preferredTerrain, lookAheadDays, smartRacingCheckInterval } = settings.racing
    const preferredGrades = (settings.racing as any).preferredGrades ?? ["G1", "G2", "G3"]

    const [searchQuery, setSearchQuery] = useState("")

    // Parse racing plan from JSON string.
    const parsedRacingPlan: PlannedRace[] = racingPlan && racingPlan !== "[]" && typeof racingPlan === "string" ? JSON.parse(racingPlan) : []

    // Convert races.json to array.
    const allRaces: Race[] = Object.values(racesData)

    // Filter races based on search and preferences.
    const filteredRaces = allRaces.filter((race) => {
        const matchesSearch = race.name.toLowerCase().includes(searchQuery.toLowerCase()) || race.date.toLowerCase().includes(searchQuery.toLowerCase())

        const matchesFans = race.fans >= minFansThreshold
        const matchesTerrain = preferredTerrain === "Any" || race.terrain === preferredTerrain
        const matchesGrade = preferredGrades.includes(race.grade) && race.grade !== "OP" && race.grade !== "Pre-OP"

        return matchesSearch && matchesFans && matchesTerrain && matchesGrade
    })

    const updateRacingSetting = (key: string, value: any) => {
        setSettings({
            ...bsc.settings,
            racing: {
                ...bsc.settings.racing,
                [key]: value,
            },
        })
    }

    const handleRacePress = (race: Race) => {
        // Determine if this should be added to the racing plan or removed.
        let newPlan: PlannedRace[] = []
        if (parsedRacingPlan.some((planned) => planned.raceName === race.name)) {
            // Remove the race from the racing plan.
            newPlan = parsedRacingPlan.filter((planned) => planned.raceName !== race.name)
        } else {
            // Add the race to the racing plan.
            const newPlannedRace: PlannedRace = {
                raceName: race.name,
                date: race.date,
                priority: parsedRacingPlan.length,
            }
            newPlan = [...parsedRacingPlan, newPlannedRace]
        }

        // Update the racing plan with the changes.
        updateRacingSetting("racingPlan", JSON.stringify(newPlan))
    }

    const addAllRacesToPlan = () => {
        const newPlan: PlannedRace[] = filteredRaces.map((race, index) => ({
            raceName: race.name,
            date: race.date,
            priority: index,
        }))

        updateRacingSetting("racingPlan", JSON.stringify(newPlan))
    }

    const clearAllRacesFromPlan = () => {
        updateRacingSetting("racingPlan", JSON.stringify([]))
    }

    const toggleGrade = (grade: string) => {
        if (preferredGrades.includes(grade)) {
            updateRacingSetting(
                "preferredGrades",
                preferredGrades.filter((g: string) => g !== grade)
            )
        } else {
            updateRacingSetting("preferredGrades", [...preferredGrades, grade])
        }
    }

    const styles = StyleSheet.create({
        root: {
            flex: 1,
            flexDirection: "column",
            margin: 10,
            backgroundColor: colors.background,
        },
        backButton: {
            padding: 8,
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
        section: {
            marginBottom: 24,
        },
        sectionTitle: {
            fontSize: 18,
            fontWeight: "600",
            color: colors.foreground,
            marginBottom: 12,
        },
        raceItem: {
            backgroundColor: colors.card,
            padding: 16,
            borderRadius: 8,
            marginBottom: 8,
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
        },
        raceName: {
            fontSize: 16,
            fontWeight: "600",
            color: colors.foreground,
        },
        raceDate: {
            fontSize: 14,
            color: colors.foreground,
            opacity: 0.7,
            marginTop: 4,
        },
        raceFans: {
            fontSize: 14,
            color: colors.primary,
            marginTop: 4,
        },
        input: {
            borderWidth: 1,
            borderColor: colors.border,
            borderRadius: 8,
            padding: 12,
            fontSize: 16,
            color: colors.foreground,
            backgroundColor: colors.background,
            marginBottom: 12,
        },
        inputLabel: {
            fontSize: 16,
            color: colors.foreground,
            marginBottom: 8,
        },
        inputDescription: {
            fontSize: 14,
            color: colors.foreground,
            opacity: 0.7,
            marginTop: 4,
        },
        terrainButton: {
            padding: 12,
            borderRadius: 8,
            marginRight: 8,
        },
        terrainButtonText: {
            fontSize: 14,
            fontWeight: "600",
        },
    })

    const renderOptions = () => {
        return (
            <>
                <View style={styles.section}>
                    <Text style={styles.inputLabel}>Minimum Fans Threshold</Text>
                    <Input
                        style={styles.input}
                        value={minFansThreshold.toString()}
                        onChangeText={(text) => {
                            const value = parseInt(text)
                            updateRacingSetting("minFansThreshold", value)
                        }}
                        keyboardType="numeric"
                        placeholder="0"
                    />
                    <Text style={styles.inputDescription}>Bot will prioritize races with at least this many fans.</Text>
                </View>

                <View style={styles.section}>
                    <Text style={styles.inputLabel}>Look-Ahead Days</Text>
                    <Input
                        style={styles.input}
                        value={lookAheadDays.toString()}
                        onChangeText={(text) => {
                            const value = parseInt(text)
                            updateRacingSetting("lookAheadDays", value)
                        }}
                        keyboardType="numeric"
                        placeholder="10"
                    />
                    <Text style={styles.inputDescription}>Number of days to look ahead when making smart racing decisions.</Text>
                </View>

                <View style={styles.section}>
                    <Text style={styles.inputLabel}>Smart Racing Check Interval</Text>
                    <Input
                        style={styles.input}
                        value={smartRacingCheckInterval.toString()}
                        onChangeText={(text) => {
                            const value = parseInt(text)
                            updateRacingSetting("smartRacingCheckInterval", value)
                        }}
                        keyboardType="numeric"
                        placeholder="2"
                    />
                    <Text style={styles.inputDescription}>How often the bot checks for optimal racing opportunities. Lower values = more frequent checks.</Text>
                </View>

                <View style={styles.section}>
                    <Text style={styles.sectionTitle}>Preferred Terrain</Text>
                    <View style={{ flexDirection: "row" }}>
                        {["Any", "Turf", "Dirt"].map((terrain) => (
                            <TouchableOpacity
                                key={terrain}
                                onPress={() => updateRacingSetting("preferredTerrain", terrain)}
                                style={[
                                    styles.terrainButton,
                                    {
                                        backgroundColor: preferredTerrain === terrain ? colors.primary : colors.card,
                                    },
                                ]}
                            >
                                <Text
                                    style={[
                                        styles.terrainButtonText,
                                        {
                                            color: preferredTerrain === terrain ? colors.background : colors.foreground,
                                        },
                                    ]}
                                >
                                    {terrain}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>

                <View style={styles.section}>
                    <Text style={styles.sectionTitle}>Preferred Race Grades</Text>
                    <Text style={styles.inputDescription}>Select which race grades the bot should prioritize.</Text>
                    <View style={{ flexDirection: "row", flexWrap: "wrap", marginTop: 8 }}>
                        {["G1", "G2", "G3"].map((grade) => (
                            <TouchableOpacity
                                key={grade}
                                onPress={() => toggleGrade(grade)}
                                style={[
                                    styles.terrainButton,
                                    {
                                        backgroundColor: preferredGrades.includes(grade) ? colors.primary : colors.card,
                                    },
                                ]}
                            >
                                <Text
                                    style={[
                                        styles.terrainButtonText,
                                        {
                                            color: preferredGrades.includes(grade) ? colors.background : colors.foreground,
                                        },
                                    ]}
                                >
                                    {grade}
                                </Text>
                            </TouchableOpacity>
                        ))}
                    </View>
                </View>
            </>
        )
    }

    const renderRaceList = () => {
        return (
            <View style={styles.section}>
                <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 12, gap: 12 }}>
                    <View style={{ flex: 1 }}>
                        <Text style={styles.sectionTitle}>Planned Races</Text>
                        <Text style={[styles.inputDescription, { marginTop: 0 }]}>
                            Selected {parsedRacingPlan.length} / {filteredRaces.length} races
                        </Text>
                    </View>
                    <View style={{ flexDirection: "row", gap: 8 }}>
                        <CustomButton icon={<Plus size={16} />} onPress={() => addAllRacesToPlan()}>
                            Add All
                        </CustomButton>
                        <CustomButton icon={<Trash2 size={16} />} onPress={() => clearAllRacesFromPlan()}>
                            Clear
                        </CustomButton>
                    </View>
                </View>

                <View style={{ marginBottom: 16 }}>
                    <Input style={styles.input} value={searchQuery} onChangeText={setSearchQuery} placeholder="Search races by name or date..." />
                    <View style={{ height: 300 }}>
                        <CustomScrollView
                            targetProps={{
                                data: filteredRaces,
                                renderItem: ({ item: race }) => (
                                    <TouchableOpacity onPress={() => handleRacePress(race)} style={styles.raceItem}>
                                        <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                                            <View style={{ flex: 1 }}>
                                                <Text style={styles.raceName}>{race.name}</Text>
                                                <Text style={styles.raceDate}>{race.date}</Text>
                                                <Text style={styles.raceFans}>
                                                    {race.fans} fans • {race.grade} • {race.terrain} • {race.distanceType}
                                                </Text>
                                            </View>
                                            {parsedRacingPlan.some((planned) => planned.raceName === race.name) && <CircleCheckBig size={18} color={"green"} />}
                                        </View>
                                    </TouchableOpacity>
                                ),
                                nestedScrollEnabled: true,
                            }}
                            position="right"
                            horizontal={false}
                            persistentScrollbar={true}
                            indicatorStyle={{
                                width: 10,
                                backgroundColor: colors.foreground,
                            }}
                            containerStyle={{
                                flex: 1,
                            }}
                            minIndicatorSize={50}
                        />
                    </View>
                </View>
            </View>
        )
    }

    return (
        <View style={styles.root}>
            <View style={styles.header}>
                <TouchableOpacity style={styles.backButton} onPress={() => navigation.goBack()}>
                    <ArrowLeft size={24} color={colors.primary} />
                </TouchableOpacity>
                <Text style={styles.title}>Racing Plan</Text>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    <View style={styles.section}>
                        <CustomTitle
                            title="Racing Plan"
                            description={
                                "Uses opportunity cost analysis to optimize race selection by looking ahead N days for races matching your character's aptitudes (A/S terrain/distance). Scores races by fans, grade, and aptitude matches.\n\nUses standard settings until Classic Year, then combines both this and standard racing settings during Classic Year. Only fully activates in Senior Year. Races when current opportunities are good enough and waiting doesn't offer significantly better value, ensuring steady fan accumulation without endless waiting."
                            }
                        />

                        <Divider style={{ marginBottom: 16 }} />

                        <CustomCheckbox
                            id="enable-racing-plan"
                            checked={enableRacingPlan}
                            onCheckedChange={(checked) => updateRacingSetting("enableRacingPlan", checked)}
                            label="Enable Racing Plan (BETA)"
                            description={"When enabled, the bot will use smart race planning to optimize race selection."}
                        />
                    </View>

                    {enableRacingPlan && (
                        <>
                            {renderOptions()}
                            {renderRaceList()}
                        </>
                    )}
                </View>
            </ScrollView>
        </View>
    )
}

export default RacingPlanSettings
