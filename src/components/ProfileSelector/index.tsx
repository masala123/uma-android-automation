import React, { useState, useEffect, useMemo, useCallback } from "react"
import { View, StyleSheet, TouchableOpacity } from "react-native"
import { useTheme } from "../../context/ThemeContext"
import CustomSelect from "../CustomSelect"
import { useProfileManager, DEFAULT_PROFILE_NAME } from "../../hooks/useProfileManager"
import ProfileManagerModal from "../ProfileManagerModal"
import ProfileCreationModal from "../ProfileCreationModal"
import { Settings } from "../../context/BotStateContext"
import { Plus, Settings as SettingsIcon } from "lucide-react-native"

interface ProfileSelectorProps {
    currentTrainingSettings: Settings["training"]
    currentTrainingStatTargetSettings: Settings["trainingStatTarget"]
    onOverwriteSettings?: (settings: Partial<Settings>) => Promise<void>
    onProfileDeleted?: (deletedProfileName: string) => void
    onNoChangesDetected?: (profileName: string) => void
    onError?: (message: string) => void
}

/**
 * Get the profile name to select based on available profiles.
 */
const getDefaultSelectedProfile = (profiles: Array<{ name: string }>): string => {
    return profiles.length > 0 ? profiles[0].name : DEFAULT_PROFILE_NAME
}

const ProfileSelector: React.FC<ProfileSelectorProps> = ({ currentTrainingSettings, currentTrainingStatTargetSettings, onOverwriteSettings, onProfileDeleted, onNoChangesDetected, onError }) => {
    const { colors } = useTheme()
    const { profiles, loadProfiles } = useProfileManager(onError)
    const [showManageModal, setShowManageModal] = useState(false)
    const [showCreateModal, setShowCreateModal] = useState(false)
    const [selectedProfileName, setSelectedProfileName] = useState<string>(DEFAULT_PROFILE_NAME)
    const [pendingProfileSwitch, setPendingProfileSwitch] = useState<string | null>(null)

    const styles = StyleSheet.create({
        container: {
            marginBottom: 20,
            padding: 16,
            backgroundColor: colors.background,
            borderRadius: 8,
            borderWidth: 1,
            borderColor: colors.border,
        },
        row: {
            flexDirection: "row",
            alignItems: "center",
            gap: 12,
        },
        selectContainer: {
            flex: 1,
        },
        iconButton: {
            padding: 8,
            borderRadius: 8,
            backgroundColor: colors.secondary,
            justifyContent: "center",
            alignItems: "center",
        },
    })

    // Initialize and sync selected profile with available profiles.
    // If the currently selected profile no longer exists, reset appropriately.
    useEffect(() => {
        const currentProfileExists = profiles.some((p) => p.name === selectedProfileName)
        // If no profiles exist but something else is selected, switch to "Default Profile".
        const shouldUseDefault = profiles.length === 0 && selectedProfileName !== DEFAULT_PROFILE_NAME

        // If "Default Profile" is selected but profiles now exist, switch to first profile.
        const shouldSwitchToFirst = profiles.length > 0 && selectedProfileName === DEFAULT_PROFILE_NAME

        if (!currentProfileExists || shouldUseDefault || shouldSwitchToFirst) {
            // If the selected profile no longer exists, reset to "Default Profile" if no profiles exist, otherwise switch to the first profile.
            setSelectedProfileName(getDefaultSelectedProfile(profiles))
        }
    }, [profiles, selectedProfileName])

    // Build the profile dropdown options: Display the default profile if no profiles exist, otherwise show the available profiles.
    const profileOptions = useMemo(() => {
        return profiles.length === 0 ? [{ value: DEFAULT_PROFILE_NAME, label: DEFAULT_PROFILE_NAME }] : profiles.map((p) => ({ value: p.name, label: p.name }))
    }, [profiles])

    const handleProfileChange = useCallback(
        async (value: string | undefined) => {
            if (!value) {
                return
            }

            // Update the selected profile name immediately for UI feedback.
            setSelectedProfileName(value)

            if (!onOverwriteSettings) {
                // If no overwrite callback, just update the display.
                return
            }

            try {
                if (value === DEFAULT_PROFILE_NAME) {
                    // Default Profile - keep current settings as-is.
                    return
                } else {
                    // Find the selected profile and apply its settings.
                    const selectedProfile = profiles.find((p) => p.name === value)
                    if (selectedProfile) {
                        // Apply the profile's settings immediately when switching.
                        await onOverwriteSettings(selectedProfile.settings)
                    } else {
                        console.warn(`Profile "${value}" not found in profiles list.`)
                    }
                }
            } catch (error) {
                // If overwrite fails, revert to the previous selection or the first available profile.
                console.error("Failed to apply profile settings:", error)
                setSelectedProfileName(getDefaultSelectedProfile(profiles))
            }
        },
        [profiles, onOverwriteSettings]
    )

    // Handle pending profile switch after profiles have been reloaded.
    useEffect(() => {
        if (pendingProfileSwitch && profiles.some((p) => p.name === pendingProfileSwitch)) {
            handleProfileChange(pendingProfileSwitch)
            setPendingProfileSwitch(null)
        }
    }, [profiles, pendingProfileSwitch, handleProfileChange])

    return (
        <View style={styles.container}>
            <View style={styles.row}>
                <View style={styles.selectContainer}>
                    <CustomSelect placeholder="Select a profile" options={profileOptions} value={selectedProfileName} onValueChange={handleProfileChange} width="100%" />
                </View>
                <TouchableOpacity style={styles.iconButton} onPress={() => setShowCreateModal(true)}>
                    <Plus size={20} color={colors.foreground} />
                </TouchableOpacity>
                <TouchableOpacity style={styles.iconButton} onPress={() => setShowManageModal(true)}>
                    <SettingsIcon size={20} color={colors.foreground} />
                </TouchableOpacity>
            </View>

            <ProfileCreationModal
                visible={showCreateModal}
                onClose={() => setShowCreateModal(false)}
                currentTrainingSettings={currentTrainingSettings}
                currentTrainingStatTargetSettings={currentTrainingStatTargetSettings}
                onProfileCreated={async (profileName) => {
                    setShowCreateModal(false)
                    // Set pending switch and reload profiles. The useEffect will handle switching once the profile gets added to the list.
                    setPendingProfileSwitch(profileName)
                    await loadProfiles()
                }}
                onError={onError}
            />

            <ProfileManagerModal
                visible={showManageModal}
                onClose={async () => {
                    setShowManageModal(false)
                    // Reload profiles to ensure we have the latest list after any deletions.
                    await loadProfiles()
                }}
                currentTrainingSettings={currentTrainingSettings}
                currentTrainingStatTargetSettings={currentTrainingStatTargetSettings}
                onOverwriteSettings={onOverwriteSettings}
                onProfileDeleted={async (deletedProfileName) => {
                    // If the deleted profile was selected, reset appropriately.
                    if (selectedProfileName === deletedProfileName) {
                        // After deletion, profiles will be reloaded. If no profiles remain, use "Default Profile".
                        const remainingProfiles = profiles.filter((p) => p.name !== deletedProfileName)
                        if (remainingProfiles.length === 0) {
                            await handleProfileChange(DEFAULT_PROFILE_NAME)
                        } else {
                            await handleProfileChange(remainingProfiles[0].name)
                        }
                    }
                    if (onProfileDeleted) {
                        onProfileDeleted(deletedProfileName)
                    }
                }}
                onProfileUpdated={async (oldName, newName) => {
                    await loadProfiles()
                    // If the renamed profile was the selected one, update to the new name.
                    if (oldName && newName && selectedProfileName === oldName) {
                        setSelectedProfileName(newName)
                    }
                }}
                onNoChangesDetected={onNoChangesDetected}
                onError={onError}
            />
        </View>
    )
}

export default ProfileSelector
