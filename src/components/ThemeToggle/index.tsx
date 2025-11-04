import React from "react"
import { TouchableOpacity, TouchableOpacityProps } from "react-native"
import { Moon, Sun } from "lucide-react-native"
import { useTheme } from "../../context/ThemeContext"

interface ThemeToggleProps {
    style?: TouchableOpacityProps["style"]
}

export const ThemeToggle: React.FC<ThemeToggleProps> = ({ style }) => {
    const { theme, toggleTheme, colors } = useTheme()

    return (
        <TouchableOpacity onPress={toggleTheme} style={style}>
            {theme === "light" ? <Moon size={24} color={colors.secondaryForeground} /> : <Sun size={24} color={colors.secondaryForeground} />}
        </TouchableOpacity>
    )
}

export default ThemeToggle
