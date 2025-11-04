import React from "react"
import { View, ViewStyle } from "react-native"
import { Checkbox } from "../ui/checkbox"
import { Label } from "../ui/label"
import { Text } from "../ui/text"
import { useTheme } from "../../context/ThemeContext"

interface CustomCheckboxProps {
    id?: string
    checked: boolean
    onCheckedChange: (checked: boolean) => void
    label: string
    description?: string | null
    className?: string
    style?: ViewStyle
}

const CustomCheckbox: React.FC<CustomCheckboxProps> = ({ id = undefined, checked, onCheckedChange, label, description, className = "", style }) => {
    const { colors } = useTheme()

    return (
        <View className={`flex-row items-start gap-3 ${className}`} style={style}>
            <Checkbox id={id} checked={checked} onCheckedChange={onCheckedChange} className="dark:border-gray-400" />

            {/* flexShrink is used to make sure the description wraps properly and not overflow past the right side of the screen. */}
            <View style={{ flexShrink: 1 }}>
                <Label style={{ color: colors.foreground, fontWeight: "bold" }} onPress={() => onCheckedChange(!checked)}>
                    {label}
                </Label>
                {description && (
                    <Text
                        className="text-sm mt-1"
                        style={{
                            color: colors.mutedForeground,
                        }}
                    >
                        {description}
                    </Text>
                )}
            </View>
        </View>
    )
}

export default CustomCheckbox
