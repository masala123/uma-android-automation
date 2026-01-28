import { useContext, useState } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image } from "react-native"
import { Divider } from "react-native-paper"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import CustomSelect from "../../components/CustomSelect"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomButton from "../../components/CustomButton"
import CustomScrollView from "../../components/CustomScrollView"
import PageHeader from "../../components/PageHeader"
import { Input } from "../../components/ui/input"
import { CircleCheckBig, Trash2 } from "lucide-react-native"
import skillsData from "../../data/skills.json"
import icons from "../SkillSettings/icons";

interface Skill {
    id: number
    name_en: string
    desc_en: string
    icon_id: number
    cost: number
    eval_pt: number
    pt_ratio: number
    rarity: number
    condition: string
    precondition: string
    inherited: boolean
    community_tier: number | null
    versions: number[]
    upgrade: number | null
    downgrade: number | null
}

interface PlannedSkill {
    name: string
    priority: number
}

const SkillPlanCareerCompleteSettings = () => {
    const { colors } = useTheme()
    const bsc = useContext(BotStateContext)

    const { settings, setSettings } = bsc

    // Merge current skills settings with defaults to handle missing properties.
    const skillSettings = { ...defaultSettings.skills, ...settings.skills }
    const {
        enableCareerCompleteSkillPlan,
        careerCompleteSpendingStrategy,
        enableCareerCompleteBuyInheritedSkills,
        enableCareerCompleteBuyNegativeSkills,
        careerCompleteSkillPlan,
    } = skillSettings

    const [searchQuery, setSearchQuery] = useState("")

    // Parse racing plan from JSON string.
    const parsedSkillPlan: PlannedSkill[] = careerCompleteSkillPlan && careerCompleteSkillPlan !== "[]" && typeof careerCompleteSkillPlan === "string" ? JSON.parse(careerCompleteSkillPlan) : []

    // Convert races.json to array.
    const allSkills: Skill[] = Object.values(skillsData)

    // Filter skills based on search and preferences.
    const filteredSkills = allSkills.filter((skill) => {
        const matchesSearch = skill.name_en.toLowerCase().includes(searchQuery.toLowerCase())

        return matchesSearch
    })

    const updateSkillsSetting = (key: string, value: any) => {
        setSettings({
            ...bsc.settings,
            skills: {
                ...bsc.settings.skills,
                [key]: value,
            },
        })
    }

    const handleSkillPress = (skill: Skill) => {
        // Determine if this should be added to the skill plan or removed.
        const isSelected = parsedSkillPlan.some((planned) => planned.name === skill.name_en)

        let newPlan: PlannedSkill[] = []
        if (isSelected) {
            // Remove the skill from the skill plan.
            newPlan = parsedSkillPlan.filter((planned) => !(planned.name === skill.name_en))
        } else {
            // Add the skill to the skill plan.
            const newPlannedSkill: PlannedSkill = {
                name: skill.name_en,
                priority: parsedSkillPlan.length,
            }
            newPlan = [...parsedSkillPlan, newPlannedSkill]
        }

        // Update the racing plan with the changes.
        updateSkillsSetting("careerCompleteSkillPlan", JSON.stringify(newPlan))
    }

    const clearAllSkillsFromPlan = () => {
        updateSkillsSetting("careerCompleteSkillPlan", JSON.stringify([]))
    }

    const styles = StyleSheet.create({
        root: {
            flex: 1,
            flexDirection: "column",
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
        description: {
            fontSize: 14,
            color: colors.foreground,
            opacity: 0.7,
            marginBottom: 16,
            lineHeight: 20,
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
        skillItem: {
            backgroundColor: colors.card,
            padding: 16,
            borderRadius: 8,
            marginBottom: 8,
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
        },
        skillName: {
            fontSize: 16,
            fontWeight: "600",
            color: colors.foreground,
        },
        skillDescription: {
            fontSize: 14,
            color: colors.foreground,
            opacity: 0.7,
            marginTop: 4,
        },
        skillSubtext: {
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
        inputContainer: {
            marginBottom: 16,
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

    const renderOptions = () => {
        return (
            <>
                <View style={styles.inputContainer}>
                    <CustomCheckbox
                        id="enable-career-complete-buy-inherited-skills"
                        checked={enableCareerCompleteBuyInheritedSkills}
                        onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteBuyInheritedSkills", checked)}
                        label="Purchase All Inherited Unique Skills"
                        description={"When enabled, the bot will attempt to purchase all inherited unique skills regardless of their evaluated rating or community tier list rating."}
                        style={{ marginTop: 16 }}
                    />
                    <CustomCheckbox
                        id="enable-career-complete-buy-negative-skills"
                        checked={enableCareerCompleteBuyNegativeSkills}
                        onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteBuyNegativeSkills", checked)}
                        label="Purchase All Negative Skills"
                        description={"When enabled, the bot will attempt to purchase all negative skills (i.e. Firm Conditions ×)."}
                        style={{ marginTop: 16 }}
                    />
                </View>
                <View style={styles.inputContainer}>
                    <Text style={styles.inputLabel}>Automated Skill Point Spending Strategy</Text>
                    <CustomSelect
                        options={[
                            { value: "default", label: "Do Not Spend Remaining Points" },
                            { value: "optimize_skills", label: "Best Skills First" },
                            { value: "optimize_rank", label: "Optimize Rank" },
                        ]}
                        value={careerCompleteSpendingStrategy}
                        defaultValue={defaultSettings.skills.careerCompleteSpendingStrategy}
                        onValueChange={(value) => updateSkillsSetting("careerCompleteSpendingStrategy", value)}
                        placeholder="Select Strategy"
                    />
                    {careerCompleteSpendingStrategy == "optimize_rank" && (
                        <View style={styles.warningContainer}>
                            <Text style={styles.warningText}>
                                ⚠️ Warning: Optimize Rank ignores any of the Skill
                                Style Overrides set in the Skill Settings page.
                            </Text>
                        </View>
                    )}
                    <Text style={styles.inputDescription}>
                        This option determines what the bot does with any
                        remaining skill points after it has purchased all of
                        the skills from the Planned Skills section and the
                        other options on this page.
                    </Text>
                    <Text style={styles.inputDescription}>
                        Best Skills First will use a community skill tier list
                        to purchase better skills first and then within each
                        tier it will attempt to optimize rank since the skills
                        within each tier are not ordered.
                    </Text>
                    <Text style={styles.inputDescription}>
                        Optimize Rank will purchase skills in a way which will
                        result in the highest trainee rank. Avoid this option
                        if you wish to train an uma up for TT or CM.
                    </Text>
                </View>
            </>
        )
    }

    const renderSkillList = () => {
        return (
            <View style={styles.section}>
                <View style={{ flexDirection: "row", alignItems: "center", marginBottom: 12, gap: 12 }}>
                    <View style={{ flex: 1 }}>
                        <Text style={styles.sectionTitle}>Planned Skills</Text>
                        <Text style={[styles.inputDescription, { marginTop: 0 }]}>
                            Selected {parsedSkillPlan.length} / {filteredSkills.length} skills
                        </Text>
                    </View>
                    <View style={{ flexDirection: "row", gap: 8 }}>
                        <CustomButton icon={<Trash2 size={16} />} onPress={() => clearAllSkillsFromPlan()}>
                            Clear
                        </CustomButton>
                    </View>
                </View>

                <View style={{ flexDirection: "row", marginBottom: 12 }}>
                    <View style={{ flex: 1 }}>
                        <Text style={[styles.inputDescription, { marginTop: 0 }]}>
                            Select skills that the bot will always attempt to buy.
                        </Text>
                    </View>
                </View>

                <View style={{ marginBottom: 16 }}>
                    <Input style={styles.input} value={searchQuery} onChangeText={setSearchQuery} placeholder="Search skills by name..." />
                    <View style={{ height: 700 }}>
                        <CustomScrollView
                            targetProps={{
                                data: filteredSkills,
                                renderItem: ({ item: skill }) => (
                                    <TouchableOpacity onPress={() => handleSkillPress(skill)} style={styles.skillItem}>
                                        <View style={{ flexDirection: "row", alignItems: "center", gap: 8 }}>
                                            <Image source={icons[skill.icon_id]} style={{ width: 64, height: 64, marginRight: 8 }} />
                                            <View style={{ flex: 1 }}>
                                                <Text style={styles.skillName}>{skill.name_en}</Text>
                                                <Text style={styles.skillDescription}>{skill.desc_en}</Text>
                                                <Text style={styles.skillSubtext}>ID: {skill.id}</Text>
                                            </View>
                                            {parsedSkillPlan.some((planned) => planned.name === skill.name_en) && (
                                                <CircleCheckBig size={18} color={"green"} />
                                            )}
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
            <PageHeader title="Career Complete Skill Plan" />
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    <Text style={styles.description}>
                        Purchases skills automatically after the career is completed.
                    </Text>
                    <Divider style={{ marginBottom: 16 }} />
                    <CustomCheckbox
                        id="enable-career-complete-skill-plan"
                        checked={enableCareerCompleteSkillPlan}
                        onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteSkillPlan", checked)}
                        label="Enable Career Complete Skill Plan (Beta)"
                        description={"When enabled, the bot will attempt to purchase skills based on the following configuration."}
                    />
                    {enableCareerCompleteSkillPlan && (
                        <>
                            {renderOptions()}
                            <Divider style={{ marginBottom: 16 }} />
                            {renderSkillList()}
                        </>
                    )}
                </View>
            </ScrollView>
        </View>
    )
}

export default SkillPlanCareerCompleteSettings
