import React, { createContext, useContext, ReactNode } from "react"
import { useSettingsManager } from "../hooks/useSettingsManager"

interface SettingsContextType {
    saveSettings: (newSettings?: any) => Promise<void>
    saveSettingsImmediate: (newSettings?: any) => Promise<void>
    loadSettings: (skipInitializationCheck?: boolean) => Promise<void>
    importSettings: (fileUri: string) => Promise<boolean>
    exportSettings: () => Promise<string | null>
    resetSettings: () => Promise<boolean>
    openDataDirectory: () => Promise<void>
    isSaving: boolean
}

const SettingsContext = createContext<SettingsContextType | undefined>(undefined)

interface SettingsProviderProps {
    children: ReactNode
}

export const SettingsProvider: React.FC<SettingsProviderProps> = ({ children }) => {
    const settingsManager = useSettingsManager()

    return <SettingsContext.Provider value={settingsManager}>{children}</SettingsContext.Provider>
}

export const useSettings = (): SettingsContextType => {
    const context = useContext(SettingsContext)
    if (context === undefined) {
        throw new Error("useSettings must be used within a SettingsProvider")
    }
    return context
}
