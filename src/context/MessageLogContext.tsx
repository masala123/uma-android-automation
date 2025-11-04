import { createContext, useState, useRef } from "react"

export interface MessageLogProviderProps {
    messageLog: string[]
    setMessageLog: React.Dispatch<React.SetStateAction<string[]>>
    addMessageToLog: (id: number, message: string) => void
}

export const MessageLogContext = createContext<MessageLogProviderProps>({} as MessageLogProviderProps)

// https://stackoverflow.com/a/60130448 and https://stackoverflow.com/a/60198351
export const MessageLogProvider = ({ children }: any): React.ReactElement => {
    const [messageLog, setMessageLog] = useState<string[]>([])
    const lastSeenId = useRef(0)

    // Add to the message log while keeping track of the sequential message IDs to prevent duplication.
    const addMessageToLog = (id: number, message: string) => {
        if (id <= lastSeenId.current) {
            return
        }

        lastSeenId.current = id
        setMessageLog((prev) => [...prev, message])
    }

    const providerValues: MessageLogProviderProps = {
        messageLog,
        setMessageLog,
        addMessageToLog,
    }

    return <MessageLogContext.Provider value={providerValues}>{children}</MessageLogContext.Provider>
}
