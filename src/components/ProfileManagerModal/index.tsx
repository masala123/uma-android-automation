import React, { useState, useEffect, useCallback } from "react"
import { View, Text, StyleSheet, ScrollView, TouchableOpacity, Modal as RNModal } from "react-native"
import { Snackbar } from "react-native-paper"
import { useTheme } from "../../context/ThemeContext"
import CustomButton from "../CustomButton"
import { Input } from "../ui/input"
import { useProfileManager } from "../../hooks/useProfileManager"
import { Settings } from "../../context/BotStateContext"
import { X, Edit2, Trash2, Save, Check } from "lucide-react-native"
import ProfileComparison from "../ProfileComparison"

interface ProfileManagerModalProps {
    visible: boolean
    onClose: () => void
    currentTrainingSettings: Settings["training"]
    currentTrainingStatTargetSettings: Settings["trainingStatTarget"]
    onOverwriteSettings?: (settings: Partial<Settings>) => Promise<void>
    onProfileDeleted?: (deletedProfileName: string) => void
    onProfileUpdated?: () => void
    onNoChangesDetected?: (profileName: string) => void
}

const ProfileManagerModal: React.FC<ProfileManagerModalProps> = ({
    visible,
    onClose,
    currentTrainingSettings,
    currentTrainingStatTargetSettings,
    onOverwriteSettings,
    onProfileDeleted,
    onProfileUpdated,
    onNoChangesDetected,
}) => {
    const { colors } = useTheme()
    const { profiles, updateProfile, deleteProfile, loadProfiles, compareWithProfile, overwriteProfileSettings } = useProfileManager()
    const [profileName, setProfileName] = useState("")
    const [editingProfileId, setEditingProfileId] = useState<number | null>(null)
    const [deleteProfileId, setDeleteProfileId] = useState<number | null>(null)
    const [showDeleteDialog, setShowDeleteDialog] = useState(false)
    const [showComparison, setShowComparison] = useState(false)
    const [overwriteProfileId, setOverwriteProfileId] = useState<number | null>(null)
    const [comparisonData, setComparisonData] = useState<Record<string, { current: any; profile: any }> | null>(null)
    const [snackbarVisible, setSnackbarVisible] = useState(false)
    const [snackbarMessage, setSnackbarMessage] = useState("")

    const styles = StyleSheet.create({
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
            width: "90%",
            maxHeight: "80%",
        },
        header: {
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            marginBottom: 20,
        },
        title: {
            fontSize: 20,
            fontWeight: "bold",
            color: colors.foreground,
        },
        closeButton: {
            padding: 4,
        },
        profileList: {
            marginTop: 16,
        },
        profileItem: {
            flexDirection: "row",
            justifyContent: "space-between",
            alignItems: "center",
            padding: 12,
            marginBottom: 8,
            backgroundColor: colors.secondary,
            borderRadius: 8,
        },
        profileName: {
            fontSize: 16,
            color: colors.foreground,
            flex: 1,
        },
        profileNameInput: {
            flex: 1,
            marginRight: 8,
        },
        profileActions: {
            flexDirection: "row",
            gap: 8,
        },
        actionButton: {
            padding: 8,
        },
        buttonRow: {
            flexDirection: "row",
            justifyContent: "space-between",
            gap: 8,
            marginTop: 16,
        },
        emptyState: {
            padding: 20,
            alignItems: "center",
        },
        emptyText: {
            fontSize: 14,
            color: colors.foreground,
            opacity: 0.6,
        },
    })

    useEffect(() => {
        if (visible) {
            loadProfiles()
            setProfileName("")
            setEditingProfileId(null)
            setShowComparison(false)
            setOverwriteProfileId(null)
            setComparisonData(null)
        }
    }, [visible, loadProfiles])

    const handleEditProfile = useCallback(
        (profileId: number) => {
            const profile = profiles.find((p) => p.id === profileId)
            if (profile) {
                setProfileName(profile.name)
                setEditingProfileId(profileId)
            }
        },
        [profiles]
    )

    const handleUpdateProfile = useCallback(async () => {
        if (!profileName.trim() || !editingProfileId) {
            return
        }

        try {
            const newName = profileName.trim()
            await updateProfile(editingProfileId, { name: newName })
            setProfileName("")
            setEditingProfileId(null)
            // Notify parent that a profile was updated.
            onProfileUpdated?.()
        } catch (error) {
            setSnackbarMessage(`Failed to update profile: ${error instanceof Error ? error.message : String(error)}`)
            setSnackbarVisible(true)
        }
    }, [profileName, editingProfileId, profiles, updateProfile, onProfileUpdated])

    const handleDeleteClick = useCallback((profileId: number) => {
        setDeleteProfileId(profileId)
        setShowDeleteDialog(true)
    }, [])

    const handleDeleteConfirm = useCallback(async () => {
        if (!deleteProfileId) {
            return
        }

        try {
            const profileToDelete = profiles.find((p) => p.id === deleteProfileId)
            const deletedProfileName = profileToDelete?.name || ""
            await deleteProfile(deleteProfileId)
            setShowDeleteDialog(false)
            setDeleteProfileId(null)
            // Notify parent that a profile was deleted.
            if (deletedProfileName) {
                onProfileDeleted?.(deletedProfileName)
            }
        } catch (error) {
            setShowDeleteDialog(false)
            setDeleteProfileId(null)
            setSnackbarMessage(`Failed to delete profile: ${error instanceof Error ? error.message : String(error)}`)
            setSnackbarVisible(true)
        }
    }, [deleteProfileId, profiles, deleteProfile, onProfileDeleted])

    const handleDeleteCancel = useCallback(() => {
        setShowDeleteDialog(false)
        setDeleteProfileId(null)
    }, [])

    const handleCancelEdit = useCallback(() => {
        setProfileName("")
        setEditingProfileId(null)
    }, [])

    const handleSaveClick = useCallback(
        (profileId: number) => {
            const profile = profiles.find((p) => p.id === profileId)
            if (!profile || !onOverwriteSettings) {
                return
            }

            // Compare current settings with the profile's settings.
            const currentSettings: Partial<Settings> = {
                training: currentTrainingSettings,
                trainingStatTarget: currentTrainingStatTargetSettings,
            }
            const comparison = compareWithProfile(profile, currentSettings, ["training", "trainingStatTarget"])

            if (Object.keys(comparison).length > 0) {
                // Show comparison preview before overwriting.
                setOverwriteProfileId(profileId)
                setComparisonData(comparison)
                setShowComparison(true)
            } else {
                // There were no differences so we notify the parent to show a snackbar.
                onNoChangesDetected?.(profile.name)
            }
        },
        [profiles, onOverwriteSettings, compareWithProfile, currentTrainingSettings, currentTrainingStatTargetSettings, onNoChangesDetected]
    )

    const handleConfirmOverwrite = useCallback(
        async (profileId: number) => {
            if (!onOverwriteSettings) {
                return
            }

            try {
                await overwriteProfileSettings(profileId, onOverwriteSettings)
                setOverwriteProfileId(null)
                setComparisonData(null)
                setShowComparison(false)
                onClose()
            } catch (error) {
                setSnackbarMessage(`Failed to overwrite settings: ${error instanceof Error ? error.message : String(error)}`)
                setSnackbarVisible(true)
            }
        },
        [onOverwriteSettings, overwriteProfileSettings, onClose]
    )

    const handleCancelOverwrite = useCallback(() => {
        setShowComparison(false)
        setOverwriteProfileId(null)
        setComparisonData(null)
    }, [])

    return (
        <>
            <RNModal visible={visible && !showDeleteDialog} transparent={true} animationType="fade" onRequestClose={onClose}>
                <TouchableOpacity style={styles.modal} activeOpacity={1} onPress={onClose}>
                    <View style={styles.modalContent} onStartShouldSetResponder={() => true}>
                        <View style={styles.header}>
                            <Text style={styles.title}>Manage Profiles</Text>
                            <TouchableOpacity style={styles.closeButton} onPress={onClose}>
                                <X size={24} color={colors.foreground} />
                            </TouchableOpacity>
                        </View>

                        <View style={styles.profileList}>
                            {profiles.length === 0 ? (
                                <View style={styles.emptyState}>
                                    <Text style={styles.emptyText}>No profiles yet.</Text>
                                </View>
                            ) : (
                                <ScrollView style={{ maxHeight: 400 }} nestedScrollEnabled={true}>
                                    {profiles.map((profile) => {
                                        const isEditing = editingProfileId === profile.id
                                        return (
                                            <View key={profile.id} style={styles.profileItem}>
                                                {isEditing ? (
                                                    <Input
                                                        placeholder="Profile name"
                                                        value={profileName}
                                                        onChangeText={setProfileName}
                                                        onSubmitEditing={handleUpdateProfile}
                                                        style={[styles.profileNameInput, { color: colors.foreground, backgroundColor: colors.background || "#ffffff" }]}
                                                        autoFocus
                                                    />
                                                ) : (
                                                    <Text style={styles.profileName}>{profile.name}</Text>
                                                )}
                                                <View style={styles.profileActions}>
                                                    {isEditing ? (
                                                        <>
                                                            <TouchableOpacity style={styles.actionButton} onPress={handleUpdateProfile}>
                                                                <Check size={18} color={colors.primary} />
                                                            </TouchableOpacity>
                                                            <TouchableOpacity style={styles.actionButton} onPress={handleCancelEdit}>
                                                                <X size={18} color={colors.foreground} />
                                                            </TouchableOpacity>
                                                        </>
                                                    ) : (
                                                        <>
                                                            <TouchableOpacity style={styles.actionButton} onPress={() => handleEditProfile(profile.id)}>
                                                                <Edit2 size={18} color={colors.primary} />
                                                            </TouchableOpacity>
                                                            <TouchableOpacity style={styles.actionButton} onPress={() => handleDeleteClick(profile.id)}>
                                                                <Trash2 size={18} color={colors.destructive} />
                                                            </TouchableOpacity>
                                                        </>
                                                    )}
                                                    {!isEditing && onOverwriteSettings && (
                                                        <TouchableOpacity style={styles.actionButton} onPress={() => handleSaveClick(profile.id)}>
                                                            <Save size={18} color={colors.primary} />
                                                        </TouchableOpacity>
                                                    )}
                                                </View>
                                            </View>
                                        )
                                    })}
                                </ScrollView>
                            )}
                        </View>

                        {showComparison && comparisonData && overwriteProfileId && (
                            <ProfileComparison
                                comparison={comparisonData}
                                onConfirm={() => handleConfirmOverwrite(overwriteProfileId)}
                                onCancel={handleCancelOverwrite}
                                actionType="overwrite"
                                category="training"
                            />
                        )}
                    </View>
                </TouchableOpacity>
            </RNModal>

            <RNModal visible={showDeleteDialog} transparent={true} animationType="fade" onRequestClose={handleDeleteCancel} statusBarTranslucent={true}>
                <View style={[styles.modal, { zIndex: 10000 }]}>
                    <View style={[styles.modalContent, { maxWidth: "85%" }]}>
                        <View style={styles.header}>
                            <Text style={styles.title}>Delete Profile</Text>
                            <TouchableOpacity style={styles.closeButton} onPress={handleDeleteCancel}>
                                <X size={24} color={colors.foreground} />
                            </TouchableOpacity>
                        </View>
                        <Text style={{ color: colors.foreground, marginBottom: 20 }}>Are you sure you want to delete this profile? This action cannot be undone.</Text>
                        <View style={styles.buttonRow}>
                            <CustomButton onPress={handleDeleteCancel} variant="outline">
                                Cancel
                            </CustomButton>
                            <CustomButton onPress={handleDeleteConfirm} variant="destructive">
                                Delete
                            </CustomButton>
                        </View>
                    </View>
                </View>
            </RNModal>

            <Snackbar
                visible={snackbarVisible}
                onDismiss={() => setSnackbarVisible(false)}
                action={{
                    label: "Close",
                    onPress: () => {
                        setSnackbarVisible(false)
                    },
                }}
                style={{ backgroundColor: "red", borderRadius: 10 }}
                duration={4000}
            >
                {snackbarMessage}
            </Snackbar>
        </>
    )
}

export default ProfileManagerModal
