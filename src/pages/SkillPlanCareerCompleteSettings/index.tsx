import { useContext, useState, useEffect } from "react"
import { View, Text, ScrollView, StyleSheet, TouchableOpacity, Image } from "react-native"
import { useNavigation, DrawerActions } from "@react-navigation/native"
import { Ionicons } from "@expo/vector-icons"
import { Divider } from "react-native-paper"
import { useTheme } from "../../context/ThemeContext"
import { BotStateContext, defaultSettings } from "../../context/BotStateContext"
import CustomCheckbox from "../../components/CustomCheckbox"
import CustomButton from "../../components/CustomButton"
import CustomScrollView from "../../components/CustomScrollView"
import { Input } from "../../components/ui/input"
import { CircleCheckBig, Trash2 } from "lucide-react-native"
import skillsData from "../../data/skills.json"
import icons from "../SkillSettings/icons";

interface Skill {
    id: number
    name_en: string
    desc_en: string
    icon_id: number
    cost: number | null
    rarity: number
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
    const navigation = useNavigation()
    const bsc = useContext(BotStateContext)

    const openDrawer = () => {
        navigation.dispatch(DrawerActions.openDrawer())
    }

    const { settings, setSettings } = bsc

    // Merge current skills settings with defaults to handle missing properties.
    const skillSettings = { ...defaultSettings.skills, ...settings.skills }
    const {
        enableCareerCompleteSkillPlan,
        enableCareerCompleteSpendAll,
        enableCareerCompleteOptimizeRank,
        enableCareerCompleteBuyInheritedSkills,
        enableCareerCompleteBuyNegativeSkills,
        enableCareerCompleteIgnoreGoldSkills,
        careerCompleteSkillPlan,
    } = skillSettings

    const [searchQuery, setSearchQuery] = useState("")

    // Parse racing plan from JSON string.
    const parsedSkillPlan: PlannedSkill[] = careerCompleteSkillPlan && careerCompleteSkillPlan !== "[]" && typeof careerCompleteSkillPlan === "string" ? JSON.parse(careerCompleteSkillPlan) : []

    // Convert races.json to array.
    const allSkills: Skill[] = Object.values(skillsData)

    // Filter skills based on search and preferences.
    const filteredSkills = allSkills.filter((skill) => {
        const matchesSearch = skill.name_en.toLowerCase().includes(searchQuery.toLowerCase()) || skill.id.toString().includes(searchQuery.toLowerCase())

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
                <CustomCheckbox
                    id="enable-career-complete-spend-all"
                    checked={enableCareerCompleteSpendAll}
                    onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteSpendAll", checked)}
                    label="Spend All Skill Points"
                    description={"When enabled, the bot will attempt to spend all available skill points after purchasing skills from the Planned Skills list. Skills will be purchased to prioritize trainee aptitudes. This is not fully optimized so using this option is not recommended."}
                    style={{ marginTop: 16 }}
                />
                <CustomCheckbox
                    id="enable-career-complete-optimize-rank"
                    checked={enableCareerCompleteOptimizeRank}
                    onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteOptimizeRank", checked)}
                    label="Purchase Skills to Optimize Rank"
                    description={"When enabled, the bot will attempt to spend skill points on skills such that the final rank of the trainee is optimized. This is just a lazy evaluation so the optimization is only approximated."}
                    style={{ marginTop: 16 }}
                />
                <CustomCheckbox
                    id="enable-career-complete-buy-inherited-skills"
                    checked={enableCareerCompleteBuyInheritedSkills}
                    onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteBuyInheritedSkills", checked)}
                    label="Purchase All Inherited Unique Skills"
                    description={"When enabled, the bot will attempt to purchase all inherited unique skills."}
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
                <CustomCheckbox
                    id="enable-career-complete-ignore-gold-skills"
                    checked={enableCareerCompleteIgnoreGoldSkills}
                    onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteIgnoreGoldSkills", checked)}
                    label="Ignore Gold Skills"
                    description={"When enabled, the bot will not purchase any gold skills that are not included in the skill plan."}
                    style={{ marginTop: 16 }}
                />
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
                        <Text style={[styles.inputDescription, { marginTop: 0 }]}>Be sure to double check your selected skills after making changes to the filters.</Text>
                    </View>
                </View>

                <View style={{ marginBottom: 16 }}>
                    <Input style={styles.input} value={searchQuery} onChangeText={setSearchQuery} placeholder="Search skills by name or ID..." />
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
            <View style={styles.header}>
                <View style={styles.headerLeft}>
                    <TouchableOpacity onPress={openDrawer} style={styles.menuButton} activeOpacity={0.7}>
                        <Ionicons name="menu" size={28} color={colors.foreground} />
                    </TouchableOpacity>
                    <Text style={styles.title}>Career Complete Skill Plan</Text>
                </View>
            </View>
            <ScrollView nestedScrollEnabled={true} showsVerticalScrollIndicator={false} showsHorizontalScrollIndicator={false} contentContainerStyle={{ flexGrow: 1 }}>
                <View className="m-1">
                    <View style={styles.section}>
                        <Text style={styles.description}>
                            {"Purchases skills automatically after the career is completed."}
                        </Text>

                        <Divider style={{ marginBottom: 16 }} />

                        <CustomCheckbox
                            id="enable-career-complete-skill-plan"
                            checked={enableCareerCompleteSkillPlan}
                            onCheckedChange={(checked) => updateSkillsSetting("enableCareerCompleteSkillPlan", checked)}
                            label="Enable Career Complete Skill Plan (Beta)"
                            description={"When enabled, the bot will attempt to purchase skills based on the following configuration."}
                        />
                    </View>

                    {enableCareerCompleteSkillPlan && (
                        <>
                            {renderOptions()}
                            {renderSkillList()}
                        </>
                    )}
                </View>
            </ScrollView>
        </View>
    )
}

export default SkillPlanCareerCompleteSettings
