import { useState, useEffect, useCallback } from "react"
import { databaseManager } from "../lib/database"
import { logWithTimestamp, logErrorWithTimestamp } from "../lib/logger"
import { Settings, defaultSettings } from "../context/BotStateContext"

export interface Profile {
    id: number
    name: string
    settings: Partial<Settings>
    created_at: string
    updated_at: string
}

/**
 * Type for settings categories.
 */
export type SettingsCategory = "training" | "trainingStatTarget"

/**
 * The reserved name for the default profile.
 */
export const DEFAULT_PROFILE_NAME = "Default Profile"

/**
 * Hook for managing profiles.
 *
 * @param onError - Optional callback to handle errors for UI display (e.g., Snackbar).
 */
export const useProfileManager = (onError?: (message: string) => void) => {
    const [profiles, setProfiles] = useState<Profile[]>([])
    const [currentProfileName, setCurrentProfileName] = useState<string | null>(null)
    const [isLoading, setIsLoading] = useState(true)

    /**
     * Find a profile by ID in the profiles array.
     *
     * @param id - The ID of the profile to find.
     * @returns The profile with the given ID, or undefined if not found.
     */
    const findProfileById = useCallback(
        (id: number): Profile | undefined => {
            return profiles.find((p) => p.id === id)
        },
        [profiles]
    )

    /**
     * Find a profile by name in the profiles array.
     *
     * @param name - The name of the profile to find.
     * @returns The profile with the given name, or undefined if not found.
     */
    const findProfileByName = useCallback(
        (name: string): Profile | undefined => {
            return profiles.find((p) => p.name === name)
        },
        [profiles]
    )

    /**
     * Check if a profile name already exists (case-insensitive).
     *
     * @param name - The name of the profile to check.
     * @param excludeId - The ID of the profile to exclude from the check.
     * @returns True if the name already exists, false otherwise.
     */
    const hasNameConflict = useCallback(
        (name: string, excludeId?: number): boolean => {
            return profiles.some((p) => p.id !== excludeId && p.name.toLowerCase() === name.toLowerCase())
        },
        [profiles]
    )

    /**
     * Compare current settings with a profile to show differences.
     *
     * @param profile - The profile to compare with.
     * @param currentSettings - The current settings.
     * @param categoriesToCompare - The categories to compare. If not provided, compares all categories in the profile.
     * @returns A record of the differences between the current settings and the profile settings.
     */
    const compareWithProfile = useCallback((profile: Profile, currentSettings: Partial<Settings>, categoriesToCompare?: (keyof Settings)[]): Record<string, { current: any; profile: any }> => {
        const diff: Record<string, { current: any; profile: any }> = {}

        // Determine which categories to compare.
        const categories = categoriesToCompare || (Object.keys(profile.settings) as (keyof Settings)[])

        // Compare each category.
        for (const category of categories) {
            const profileCategory = profile.settings[category]
            const currentCategory = currentSettings[category]

            if (!profileCategory || !currentCategory) {
                continue
            }

            // Compare all keys in the category.
            const categoryKeys = Object.keys(profileCategory) as Array<keyof typeof profileCategory>
            for (const key of categoryKeys) {
                const currentValue = (currentCategory as any)[key]
                const profileValue = (profileCategory as any)[key]
                // Save the diff if the values are not the same.
                if (JSON.stringify(currentValue) !== JSON.stringify(profileValue)) {
                    const diffKey = category === "trainingStatTarget" ? `trainingStatTarget.${String(key)}` : String(key)
                    diff[diffKey] = { current: currentValue, profile: profileValue }
                }
            }
        }

        return diff
    }, [])

    /**
     * Load all profiles from the database.
     *
     * @returns A promise that resolves when the profiles are loaded.
     */
    const loadProfiles = useCallback(async () => {
        try {
            setIsLoading(true)

            const dbProfiles = await databaseManager.getAllProfiles()

            const parsedProfiles: Profile[] = dbProfiles.map((p) => ({
                id: p.id,
                name: p.name,
                settings: JSON.parse(p.settings),
                created_at: p.created_at,
                updated_at: p.updated_at,
            }))

            // Sort profiles alphabetically.
            parsedProfiles.sort((a, b) => a.name.localeCompare(b.name))

            setProfiles(parsedProfiles)
            logWithTimestamp(`[ProfileManager] Loaded ${parsedProfiles.length} profiles.`)
        } catch (error) {
            logErrorWithTimestamp("[ProfileManager] Failed to load profiles:", error)
            setProfiles([])
        } finally {
            setIsLoading(false)
        }
    }, [])

    /**
     * Load the current active profile name.
     *
     * @returns A promise that resolves when the current profile name is loaded.
     */
    const loadCurrentProfileName = useCallback(async () => {
        try {
            const profileName = await databaseManager.getCurrentProfileName()
            setCurrentProfileName(profileName)
        } catch (error) {
            logErrorWithTimestamp("[ProfileManager] Failed to load current profile name:", error)
            setCurrentProfileName(null)
        }
    }, [])

    useEffect(() => {
        loadProfiles()
        loadCurrentProfileName()
    }, [loadProfiles, loadCurrentProfileName])

    /**
     * Create a new profile from current settings.
     *
     * @param name - The name of the profile to create.
     * @param settings - The settings to create the profile with.
     * @returns A promise that resolves when the profile is created.
     */
    const createProfile = useCallback(
        async (name: string, settings: Partial<Settings>): Promise<number> => {
            try {
                if (name.toLowerCase() === DEFAULT_PROFILE_NAME.toLowerCase()) {
                    const errorMessage = `Cannot create a profile with the reserved name '${DEFAULT_PROFILE_NAME}'.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return 0
                }

                // Reload profiles to ensure we have the latest list before checking for duplicates.
                await loadProfiles()

                // Check for name conflicts using current profiles state.
                if (hasNameConflict(name)) {
                    const errorMessage = `Profile with name "${name}" already exists.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return 0
                }

                const profileId = await databaseManager.saveProfile({
                    name,
                    settings,
                })

                await loadProfiles()
                logWithTimestamp(`[ProfileManager] Created profile: ${name}`)
                return profileId
            } catch (error) {
                const errorMessage = `Failed to create profile ${name}: ${error instanceof Error ? error.message : String(error)}`
                logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`, error)
                onError?.(errorMessage)
                return 0
            }
        },
        [loadProfiles, profiles, hasNameConflict, onError]
    )

    /**
     * Update an existing profile.
     *
     * @param id - The ID of the profile to update.
     * @param updates - The updates to apply to the profile.
     * @returns A promise that resolves when the profile is updated.
     */
    const updateProfile = useCallback(
        async (
            id: number,
            updates: {
                name?: string
                settings?: Partial<Settings>
            }
        ): Promise<void> => {
            try {
                const existingProfile = findProfileById(id)
                if (!existingProfile) {
                    const errorMessage = `Profile with id ${id} not found.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return
                }

                // Prevent renaming to Default Profile.
                if (updates.name && updates.name.toLowerCase() === DEFAULT_PROFILE_NAME.toLowerCase()) {
                    const errorMessage = `Cannot use the reserved name '${DEFAULT_PROFILE_NAME}'.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return
                }

                // Check for name conflicts if name is being updated.
                if (updates.name && updates.name !== existingProfile.name && hasNameConflict(updates.name, id)) {
                    const errorMessage = `Profile with name "${updates.name}" already exists.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return
                }

                // Merge existing settings with updates.
                const mergedSettings = updates.settings ? { ...existingProfile.settings, ...updates.settings } : existingProfile.settings

                await databaseManager.saveProfile({
                    id,
                    name: updates.name || existingProfile.name,
                    settings: mergedSettings,
                })

                await loadProfiles()
                logWithTimestamp(`[ProfileManager] Updated profile: ${updates.name || existingProfile.name}`)
            } catch (error) {
                const errorMessage = `Failed to update profile ${id}: ${error instanceof Error ? error.message : String(error)}`
                logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`, error)
                onError?.(errorMessage)
            }
        },
        [profiles, loadProfiles, findProfileById, hasNameConflict, onError]
    )

    /**
     * Delete a profile.
     *
     * @param id - The ID of the profile to delete.
     * @returns A promise that resolves when the profile is deleted.
     */
    const deleteProfile = useCallback(
        async (id: number): Promise<void> => {
            try {
                const profile = findProfileById(id)
                if (!profile) {
                    const errorMessage = `Profile with id ${id} not found.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return
                }

                await databaseManager.deleteProfile(id)

                // If the deleted profile was the current one, clear the current profile name.
                if (currentProfileName === profile.name) {
                    await databaseManager.setCurrentProfileName(null)
                    setCurrentProfileName(null)
                }

                await loadProfiles()
                logWithTimestamp(`[ProfileManager] Deleted profile: ${profile.name}`)
            } catch (error) {
                const errorMessage = `Failed to delete profile ${id}: ${error instanceof Error ? error.message : String(error)}`
                logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`, error)
                onError?.(errorMessage)
            }
        },
        [profiles, currentProfileName, loadProfiles, findProfileById, onError]
    )

    /**
     * Switch to a profile and apply its settings immediately.
     *
     * @param profileName - The name of the profile to switch to.
     * @returns A promise that resolves when the profile is switched to.
     */
    const switchProfile = useCallback(
        async (profileName: string | null): Promise<Profile | null> => {
            try {
                if (!profileName) {
                    // Clear current profile.
                    await databaseManager.setCurrentProfileName(null)
                    setCurrentProfileName(null)
                    return null
                }

                const profile = findProfileByName(profileName)
                if (!profile) {
                    const errorMessage = `Profile "${profileName}" not found.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return null
                }

                await databaseManager.setCurrentProfileName(profileName)
                setCurrentProfileName(profileName)
                logWithTimestamp(`[ProfileManager] Switched to profile: ${profileName}`)
                return profile
            } catch (error) {
                const errorMessage = `Failed to switch to profile ${profileName}: ${error instanceof Error ? error.message : String(error)}`
                logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`, error)
                onError?.(errorMessage)
                return null
            }
        },
        [profiles, findProfileByName, onError]
    )

    /**
     * Get the current active profile.
     *
     * @returns The current active profile, or null if no profile is active.
     */
    const getCurrentProfile = useCallback((): Profile | null => {
        if (!currentProfileName) {
            return null
        }
        return findProfileByName(currentProfileName) || null
    }, [currentProfileName, findProfileByName])

    /**
     * Overwrite a profile's settings with current settings.
     * This applies the profile's settings to the current settings.
     *
     * @param profileId - The ID of the profile to overwrite the settings of.
     * @param applySettings - The function to apply the profile's settings to the current settings.
     * @returns A promise that resolves when the profile settings are overwritten.
     */
    const overwriteProfileSettings = useCallback(
        async (profileId: number, applySettings: (settings: Partial<Settings>) => Promise<void>): Promise<void> => {
            try {
                const profile = findProfileById(profileId)
                if (!profile) {
                    const errorMessage = `Profile with id ${profileId} not found.`
                    logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`)
                    onError?.(errorMessage)
                    return
                }

                // Apply the profile's settings to current settings.
                await applySettings(profile.settings)
                logWithTimestamp(`[ProfileManager] Applied profile settings: ${profile.name}`)
            } catch (error) {
                const errorMessage = `Failed to overwrite settings with profile ${profileId}: ${error instanceof Error ? error.message : String(error)}`
                logErrorWithTimestamp(`[ProfileManager] ${errorMessage}`, error)
                onError?.(errorMessage)
            }
        },
        [profiles, findProfileById, onError]
    )

    return {
        profiles,
        currentProfileName,
        isLoading,
        loadProfiles,
        createProfile,
        updateProfile,
        deleteProfile,
        switchProfile,
        getCurrentProfile,
        compareWithProfile,
        overwriteProfileSettings,
    }
}
