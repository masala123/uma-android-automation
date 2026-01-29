import React, { useRef, useState } from "react"
import { View, LayoutChangeEvent, ViewStyle } from "react-native"
import { Option, Select, SelectContent, SelectGroup, SelectItem, SelectLabel, SelectTrigger, SelectValue, NativeSelectScrollView } from "../ui/select"

interface SelectOption {
    value: string
    label: string
    disabled?: boolean
}

interface CustomSelectProps {
    placeholder?: string
    options?: SelectOption[]
    width?: string | number
    groupLabel?: string
    onValueChange?: (value: string | undefined) => void
    setValue?: React.Dispatch<React.SetStateAction<string>>
    defaultValue?: string
    value?: string
    disabled?: boolean
    style?: ViewStyle
    portalHost?: string
}

const CustomSelect: React.FC<CustomSelectProps> = ({
    placeholder = "Select an option",
    options = [],
    width = "100%",
    groupLabel,
    onValueChange,
    setValue,
    defaultValue,
    value,
    disabled = false,
    style,
    portalHost,
}) => {
    const [triggerWidth, setTriggerWidth] = useState<number>(0)
    const triggerRef = useRef<View>(null)

    const onTriggerLayout = (event: LayoutChangeEvent) => {
        const { width: measuredWidth } = event.nativeEvent.layout
        setTriggerWidth(measuredWidth)
    }

    const handleValueChange = (option: Option) => {
        if (onValueChange) {
            onValueChange(option?.value || "")
        }
        if (setValue) {
            setValue(option?.value || "")
        }
    }

    return (
        <Select onValueChange={handleValueChange} value={value as any} defaultValue={defaultValue as any} disabled={disabled}>
            <View ref={triggerRef} style={[{ width: width as any }, style]} onLayout={onTriggerLayout}>
                <SelectTrigger style={{ backgroundColor: "white" }}>
                    <SelectValue placeholder={value || placeholder} style={{ color: "black" }} />
                </SelectTrigger>
            </View>
            <SelectContent style={{ width: triggerWidth }} portalHost={portalHost}>
                <NativeSelectScrollView>
                    <SelectGroup>
                        {groupLabel && <SelectLabel>{groupLabel}</SelectLabel>}
                        {options &&
                            options.map((option) => (
                                <SelectItem key={option.value} label={option.label} value={option.value} disabled={option.disabled}>
                                    {option.label}
                                </SelectItem>
                            ))}
                    </SelectGroup>
                </NativeSelectScrollView>
            </SelectContent>
        </Select>
    )
}

export default CustomSelect
