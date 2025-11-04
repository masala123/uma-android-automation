import React from "react"
import { StyleSheet, Text, View } from "react-native"
import type { GapRecord } from "../../lib/eventLogParser"
import { useTheme } from "../../context/ThemeContext"

type Props = {
    gap: GapRecord
}

const GapsNotice: React.FC<Props> = ({ gap }) => {
    const { colors } = useTheme()

    const styles = StyleSheet.create({
        container: {
            paddingVertical: 8,
            paddingHorizontal: 12,
            borderLeftWidth: 4,
            borderRadius: 8,
            marginBottom: 10,
            backgroundColor: colors.warningBg,
            borderLeftColor: colors.warningBorder,
        },
        text: {
            fontSize: 12,
            color: colors.warningText,
        },
    })

    return (
        <View style={styles.container}>
            <Text style={styles.text}>⚠️ {gap.from === gap.to ? `Day ${gap.from} missing.` : `Days ${gap.from}-${gap.to} missing.`}</Text>
        </View>
    )
}

export default GapsNotice
