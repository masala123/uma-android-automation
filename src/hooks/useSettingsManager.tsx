import { useState, useEffect, useContext } from "react"
import * as FileSystem from "expo-file-system"
import * as Sharing from "expo-sharing"
import { startActivityAsync } from "expo-intent-launcher"
import { defaultSettings, Settings, BotStateContext } from "../context/BotStateContext"
import { databaseManager } from "../lib/database"
import { startTiming } from "../lib/performanceLogger"
import { logWithTimestamp, logErrorWithTimestamp } from "../lib/logger"

/**
 * Deep merges two objects, preserving nested structure.
 */
const deepMerge = <T extends Record<string, any>>(target: T, source: Partial<T>): T => {
    const output = { ...target }
    for (const key in source) {
        if (source[key] && typeof source[key] === "object" && !Array.isArray(source[key]) && source[key] !== null) {
            output[key] = deepMerge((target[key] || {}) as Record<string, any>, source[key] as any) as T[Extract<keyof T, string>]
        } else if (source[key] !== undefined) {
            output[key] = source[key] as T[Extract<keyof T, string>]
        }
    }
    return output
}

/**
 * Converts settings object to database batch format.
 */
const convertSettingsToBatch = (settings: Settings) => {
    const batch: Array<{ category: string; key: string; value: any }> = []

    Object.entries(settings).forEach(([category, categorySettings]) => {
        Object.entries(categorySettings).forEach(([key, value]) => {
            batch.push({ category, key, value })
        })
    })

    return batch
}

/**
 * Manages settings persistence using SQLite database.
 */
export const useSettingsManager = () => {
    // Track whether settings are currently being saved.
    const [isSaving, setIsSaving] = useState(false)
    const [migrationCompleted, setMigrationCompleted] = useState(false)

    const bsc = useContext(BotStateContext)

    // Direct database operations
    const isSQLiteInitialized = databaseManager.isInitialized()
    const isSQLiteSaving = false

    // Auto-load settings when SQLite is initialized.
    useEffect(() => {
        if (isSQLiteInitialized && !migrationCompleted) {
            logWithTimestamp("[SettingsManager] SQLite initialized and loading settings will be handled by bootstrap.")
            setMigrationCompleted(true)
        }
    }, [isSQLiteInitialized, migrationCompleted])

    // Save settings to SQLite database.
    const saveSettings = async (newSettings?: Settings) => {
        const endTiming = startTiming("settings_manager_save_settings", "settings")

        setIsSaving(true)

        try {
            const localSettings: Settings = newSettings ? newSettings : bsc.settings
            await databaseManager.saveSettingsBatch(convertSettingsToBatch(localSettings))
            endTiming({ status: "success", hasNewSettings: !!newSettings })
        } catch (error) {
            logErrorWithTimestamp(`Error saving settings: ${error}`)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
        } finally {
            setIsSaving(false)
        }
    }

    // Save settings immediately without debouncing (for background/exit saves).
    const saveSettingsImmediate = async (newSettings?: Settings) => {
        const endTiming = startTiming("settings_manager_save_settings_immediate", "settings")

        setIsSaving(true)

        try {
            const localSettings: Settings = newSettings ? newSettings : bsc.settings
            await databaseManager.saveSettingsBatch(convertSettingsToBatch(localSettings))
            endTiming({ status: "success", hasNewSettings: !!newSettings, immediate: true })
        } catch (error) {
            logErrorWithTimestamp(`Error saving settings immediately: ${error}`)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
        } finally {
            setIsSaving(false)
        }
    }

    // Load settings from SQLite database.
    const loadSettings = async (skipInitializationCheck: boolean = false) => {
        const timingName = skipInitializationCheck ? "settings_manager_load_settings_bootstrap" : "settings_manager_load_settings"
        const endTiming = startTiming(timingName, "settings")
        const context = skipInitializationCheck ? "during bootstrap" : ""

        try {
            // Wait for SQLite to be initialized (unless explicitly skipped).
            if (!skipInitializationCheck && !isSQLiteInitialized) {
                logWithTimestamp("[SettingsManager] Waiting for SQLite initialization...")
                endTiming({ status: "skipped", reason: "sqlite_not_initialized" })
                return
            }

            // Load from SQLite database.
            let newSettings: Settings = JSON.parse(JSON.stringify(defaultSettings))
            try {
                const dbSettings = await databaseManager.loadAllSettings()
                // Use deep merge to preserve nested default values.
                newSettings = deepMerge(defaultSettings, dbSettings as Partial<Settings>)
                logWithTimestamp(`[SettingsManager] Settings loaded from SQLite database ${context}.`)
            } catch (sqliteError) {
                logWithTimestamp(`[SettingsManager] Failed to load from SQLite ${context}, using defaults:`)
                console.warn(sqliteError)
            }

            bsc.setSettings(newSettings)
            logWithTimestamp(`[SettingsManager] Settings loaded and applied to context${context}.`)
            logWithTimestamp(`[SettingsManager] Scenario value after load: "${newSettings.general.scenario}"`)
            endTiming({ status: "success", usedDefaults: newSettings === defaultSettings })
        } catch (error) {
            logErrorWithTimestamp(`[SettingsManager] Error loading settings${context}:`, error)
            bsc.setSettings(JSON.parse(JSON.stringify(defaultSettings)))
            bsc.setReadyStatus(false)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
        }
    }

    // Import settings from a JSON file.
    const loadFromJSONFile = async (fileUri: string): Promise<Settings> => {
        try {
            const data = await FileSystem.readAsStringAsync(fileUri)
            const parsed: Settings = JSON.parse(data)
            const fixedSettings: Settings = fixSettings(parsed)

            logWithTimestamp("Settings imported from JSON file successfully.")
            return fixedSettings
        } catch (error: any) {
            logErrorWithTimestamp(`Error reading settings from JSON file: ${error}`)
            throw error
        }
    }

    // Ensure all required settings fields exist by filling missing ones with defaults.
    const fixSettings = (decoded: Settings): Settings => {
        return deepMerge(defaultSettings, decoded as Partial<Settings>)
    }

    // Import settings from a JSON file and save to SQLite.
    const importSettings = async (fileUri: string): Promise<boolean> => {
        const endTiming = startTiming("settings_manager_import_settings", "settings")

        try {
            setIsSaving(true)

            // Ensure database is initialized before saving.
            logWithTimestamp("Ensuring database is initialized before saving...")
            if (!isSQLiteInitialized) {
                logWithTimestamp("Database not initialized, triggering initialization...")
                await databaseManager.initialize()
            }

            // Save to SQLite database.
            const importedSettings = await loadFromJSONFile(fileUri)
            await databaseManager.saveSettingsBatch(convertSettingsToBatch(importedSettings))
            bsc.setSettings(importedSettings)

            logWithTimestamp("Settings imported successfully.")

            endTiming({ status: "success", fileUri })
            return true
        } catch (error) {
            logErrorWithTimestamp("Error importing settings:", error)
            endTiming({ status: "error", fileUri, error: error instanceof Error ? error.message : String(error) })
            return false
        } finally {
            setIsSaving(false)
        }
    }

    // Export current settings to a JSON file.
    const exportSettings = async (): Promise<string | null> => {
        const endTiming = startTiming("settings_manager_export_settings", "settings")

        try {
            const jsonString = JSON.stringify(bsc.settings, null, 4)
            // Create export object with settings and profiles, excluding large data fields.
            const settingsForExport = JSON.parse(JSON.stringify(bsc.settings))

            // Remove unnecessary fields before export.
            delete settingsForExport.racing.racingPlanData
            delete settingsForExport.trainingEvent.characterEventData
            delete settingsForExport.trainingEvent.supportEventData
            delete settingsForExport.misc.formattedSettingsString
            delete settingsForExport.misc.currentProfileName

            const exportData = {
                ...settingsForExport,
                profiles: profiles.length > 0 ? profiles : undefined,
            }

            const jsonString = JSON.stringify(exportData, null, 4)

            // Create a temporary file name with timestamp.
            const timestamp = new Date().toISOString().replace(/[:.]/g, "-")
            const fileName = `UAA-settings-${timestamp}.json`
            const fileUri = FileSystem.documentDirectory + fileName

            // Write the settings to file.
            await FileSystem.writeAsStringAsync(fileUri, jsonString)

            logWithTimestamp(`Settings exported successfully to: ${fileUri}`)

            endTiming({ status: "success", fileName, fileSize: jsonString.length })
            return fileUri
        } catch (error) {
            logErrorWithTimestamp("Error exporting settings:", error)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
            return null
        }
    }

    // Open the app's data directory using Storage Access Framework.
    const openDataDirectory = async () => {
        // Get the app's package name from the document directory path.
        const packageName = "com.steve1316.uma_android_automation"

        try {
            // Try Storage Access Framework first (recommended for Android 11+).
            try {
                await startActivityAsync("android.intent.action.OPEN_DOCUMENT_TREE", {
                    data: `content://com.android.externalstorage.documents/tree/primary%3AAndroid%2Fdata%2F${packageName}/files`,
                    flags: 1, // FLAG_GRANT_READ_URI_PERMISSION
                })

                return
            } catch (safError) {
                console.warn("SAF approach failed, trying fallback:", safError)
            }

            // Fallback: Try to open the data folder with the android.intent.action.VIEW Intent.
            try {
                await startActivityAsync("android.intent.action.VIEW", {
                    data: `/storage/emulated/0/Android/data/${packageName}/files`,
                    type: "resource/folder",
                })

                return
            } catch (folderError) {
                console.warn("Folder approach failed, trying file sharing:", folderError)
            }

            // Final fallback: Share the settings file directly.
            const settingsPath = FileSystem.documentDirectory + "settings.json"
            const fileInfo = await FileSystem.getInfoAsync(settingsPath)

            if (fileInfo.exists) {
                const isAvailable = await Sharing.isAvailableAsync()
                if (isAvailable) {
                    await Sharing.shareAsync(settingsPath, {
                        mimeType: "application/json",
                        dialogTitle: "Share Settings File",
                    })
                } else {
                    throw new Error("Sharing not available")
                }
            } else {
                throw new Error("Settings file not found")
            }
        } catch (error) {
            logErrorWithTimestamp(`Error opening app data directory: ${error}`)
        }
    }

    // Reset settings to default values.
    const resetSettings = async (): Promise<boolean> => {
        const endTiming = startTiming("settings_manager_reset_settings", "settings")

        try {
            setIsSaving(true)

            // Ensure database is initialized before saving.
            logWithTimestamp("Ensuring database is initialized before resetting...")
            if (!isSQLiteInitialized) {
                logWithTimestamp("Database not initialized, triggering initialization...")
                await databaseManager.initialize()
            }

            // Create a deep copy of default settings to avoid reference issues.
            const defaultSettingsCopy = JSON.parse(JSON.stringify(defaultSettings))

            // Save default settings to SQLite database.
            await databaseManager.saveSettingsBatch(convertSettingsToBatch(defaultSettingsCopy))

            // Update the current settings in context.
            bsc.setSettings(defaultSettingsCopy)
            bsc.setReadyStatus(false)

            logWithTimestamp("Settings reset to defaults successfully.")

            endTiming({ status: "success" })
            return true
        } catch (error) {
            logErrorWithTimestamp("Error resetting settings:", error)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
            return false
        } finally {
            setIsSaving(false)
        }
    }

    return {
        saveSettings,
        saveSettingsImmediate,
        loadSettings,
        importSettings,
        exportSettings,
        resetSettings,
        openDataDirectory,
        isSaving: isSaving || isSQLiteSaving,
    }
}
