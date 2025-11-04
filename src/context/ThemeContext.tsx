import React, { createContext, useContext, useEffect, useState } from "react"
import { useColorScheme } from "react-native"
import { THEME } from "../lib/theme"

type Theme = "light" | "dark"

interface ThemeContextType {
    theme: Theme
    setTheme: (theme: Theme) => void
    toggleTheme: () => void
    isDark: boolean
    colors: typeof THEME.light
}

const ThemeContext = createContext<ThemeContextType | undefined>(undefined)

export const useTheme = () => {
    const context = useContext(ThemeContext)
    if (!context) {
        throw new Error("useTheme must be used within a ThemeProvider")
    }
    return context
}

interface ThemeProviderProps {
    children: React.ReactNode
}

export const ThemeProvider: React.FC<ThemeProviderProps> = ({ children }) => {
    const systemColorScheme = useColorScheme()
    const [theme, setTheme] = useState<Theme>("light")

    // Initialize theme based on system preference.
    useEffect(() => {
        if (systemColorScheme) {
            setTheme(systemColorScheme)
        }
    }, [systemColorScheme])

    const toggleTheme = () => {
        setTheme((prev) => (prev === "light" ? "dark" : "light"))
    }

    const isDark = theme === "dark"
    const colors = THEME[theme]

    const value: ThemeContextType = {
        theme,
        setTheme,
        toggleTheme,
        isDark,
        colors,
    }

    return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
