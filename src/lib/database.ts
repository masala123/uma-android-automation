import * as SQLite from "expo-sqlite"
import { startTiming } from "./performanceLogger"
import { logWithTimestamp, logErrorWithTimestamp } from "./logger"

// The schema for the database.
export interface DatabaseSettings {
    id: number
    category: string
    key: string
    value: string
    updated_at: string
}

export interface DatabaseRace {
    id: number
    key: string
    name: string
    date: string
    raceTrack: string
    course: string | null
    direction: string
    grade: string
    terrain: string
    distanceType: string
    distanceMeters: number
    fans: number
    turnNumber: number
    nameFormatted: string
}

/**
 * Database utility class for managing settings persistence with SQLite.
 * Stores settings as key-value pairs organized by category for efficient querying.
 */
export class DatabaseManager {
    private db: SQLite.SQLiteDatabase | null = null
    private isInitializing = false
    private initializationPromise: Promise<void> | null = null
    private isTransactionActive = false
    private transactionQueue: Array<() => Promise<void>> = []

    /**
     * Initialize the database and create tables if they don't exist.
     */
    async initialize(): Promise<void> {
        const endTiming = startTiming("database_initialize", "database")

        // If already initializing, wait for the existing initialization to complete.
        if (this.isInitializing && this.initializationPromise) {
            logWithTimestamp("Database initialization already in progress, waiting...")
            endTiming({ status: "already_initializing" })
            return this.initializationPromise
        }

        // If already initialized, return immediately.
        if (this.db) {
            logWithTimestamp("Database already initialized, skipping...")
            endTiming({ status: "already_initialized" })
            return
        }

        this.isInitializing = true
        this.initializationPromise = this._performInitialization()

        try {
            await this.initializationPromise
            endTiming({ status: "success" })
        } finally {
            this.isInitializing = false
            this.initializationPromise = null
        }
    }

    private async _performInitialization(): Promise<void> {
        try {
            logWithTimestamp("Starting database initialization...")
            this.db = await SQLite.openDatabaseAsync("settings.db", {
                useNewConnection: true,
            })
            logWithTimestamp("Database opened successfully")

            if (!this.db) {
                throw new Error("Database object is null after opening")
            }

            // Create settings table.
            logWithTimestamp("Creating settings table...")
            await this.db.execAsync(`
                CREATE TABLE IF NOT EXISTS settings (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    category TEXT NOT NULL,
                    key TEXT NOT NULL,
                    value TEXT NOT NULL,
                    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP,
                    UNIQUE(category, key)
                )
            `)
            logWithTimestamp("Settings table created successfully.")

            // Create races table.
            logWithTimestamp("Creating races table...")
            await this.db.execAsync(`
                CREATE TABLE IF NOT EXISTS races (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    key TEXT UNIQUE NOT NULL,
                    name TEXT NOT NULL,
                    date TEXT NOT NULL,
                    raceTrack TEXT NOT NULL,
                    course TEXT,
                    direction TEXT NOT NULL,
                    grade TEXT NOT NULL,
                    terrain TEXT NOT NULL,
                    distanceType TEXT NOT NULL,
                    distanceMeters INTEGER NOT NULL,
                    fans INTEGER NOT NULL,
                    turnNumber INTEGER NOT NULL,
                    nameFormatted TEXT NOT NULL
                )
            `)
            logWithTimestamp("Races table created successfully.")

            // Create indexes for faster queries.
            logWithTimestamp("Creating indexes...")
            await this.db.execAsync(`
                CREATE INDEX IF NOT EXISTS idx_settings_category_key 
                ON settings(category, key)
            `)
            await this.db.execAsync(`
                CREATE INDEX IF NOT EXISTS idx_races_turn_number 
                ON races(turnNumber)
            `)
            await this.db.execAsync(`
                CREATE INDEX IF NOT EXISTS idx_races_name_formatted 
                ON races(nameFormatted)
            `)
            logWithTimestamp("Indexes created successfully.")

            logWithTimestamp("Database initialized successfully.")
        } catch (error) {
            logErrorWithTimestamp("Failed to initialize database:", error)
            this.db = null // Reset database on error.
            throw error
        }
    }

    /**
     * Save settings to database by category and key.
     */
    async saveSetting(category: string, key: string, value: any, suppressLogging: boolean = false): Promise<void> {
        const endTiming = startTiming("database_save_setting", "database")

        if (!this.db) {
            logErrorWithTimestamp("Database is null when trying to save setting.")
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        try {
            const valueString = typeof value === "string" ? value : JSON.stringify(value)
            if (!suppressLogging) {
                logWithTimestamp(`[DB] Saving setting: ${category}.${key} = ${valueString.substring(0, 100)}...`)
            }
            await this.db.runAsync(
                `INSERT OR REPLACE INTO settings (category, key, value, updated_at) 
                 VALUES (?, ?, ?, CURRENT_TIMESTAMP)`,
                [category, key, valueString]
            )
            if (!suppressLogging) {
                logWithTimestamp(`[DB] Successfully saved setting: ${category}.${key}`)
            }
            endTiming({ status: "success", category, key })
        } catch (error) {
            logErrorWithTimestamp(`[DB] Failed to save setting ${category}.${key}:`, error)
            endTiming({ status: "error", category, key, error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Execute a database operation with proper transaction management to prevent nested transactions.
     */
    private async executeWithTransaction<T>(operation: () => Promise<T>): Promise<T> {
        return new Promise((resolve, reject) => {
            const executeOperation = async () => {
                if (this.isTransactionActive) {
                    // If a transaction is already active, queue this operation.
                    this.transactionQueue.push(executeOperation)
                    return
                }

                this.isTransactionActive = true

                try {
                    const result = await operation()
                    resolve(result)
                } catch (error) {
                    // Clear the transaction queue on error to prevent cascading failures.
                    this.clearTransactionQueue()
                    reject(error)
                } finally {
                    this.isTransactionActive = false

                    // Process the next queued operation if any.
                    if (this.transactionQueue.length > 0) {
                        const nextOperation = this.transactionQueue.shift()
                        if (nextOperation) {
                            // Use setTimeout to avoid stack overflow with recursive calls.
                            setTimeout(() => nextOperation(), 0)
                        }
                    }
                }
            }

            executeOperation()
        })
    }

    /**
     * Save multiple settings in a single transaction for better performance.
     */
    async saveSettingsBatch(settings: Array<{ category: string; key: string; value: any }>): Promise<void> {
        const endTiming = startTiming("database_save_settings_batch", "database")

        if (!this.db) {
            logErrorWithTimestamp("Database is null when trying to save settings batch.")
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        if (settings.length === 0) {
            endTiming({ status: "skipped", reason: "no_settings" })
            return
        }

        try {
            await this.executeWithTransaction(async () => {
                logWithTimestamp(`[DB] Saving ${settings.length} settings in batch.`)

                await this.db!.runAsync("BEGIN TRANSACTION")
                const stmt = await this.db!.prepareAsync(
                    `INSERT OR REPLACE INTO settings (category, key, value, updated_at) 
                     VALUES (?, ?, ?, CURRENT_TIMESTAMP)`
                )

                // Execute all settings in batch.
                for (const setting of settings) {
                    const valueString = typeof setting.value === "string" ? setting.value : JSON.stringify(setting.value)
                    await stmt.executeAsync([setting.category, setting.key, valueString])
                }

                // Finalize statement and commit transaction.
                await stmt.finalizeAsync()
                await this.db!.runAsync("COMMIT")

                logWithTimestamp(`[DB] Successfully saved ${settings.length} settings in batch.`)
            })

            endTiming({ status: "success", settingsCount: settings.length })
        } catch (error) {
            const settingsInfo = settings.length > 0 ? ` (${settings.length} settings: ${settings.map((s) => `${s.category}.${s.key}`).join(", ")})` : " (no settings)"
            logErrorWithTimestamp(`[DB] Failed to save settings batch${settingsInfo}:`, error)

            // Rollback transaction on error.
            try {
                if (this.db && this.isTransactionActive) {
                    await this.db.runAsync("ROLLBACK")
                }
            } catch (rollbackError) {
                logErrorWithTimestamp(`[DB] Failed to rollback transaction${settingsInfo}:`, rollbackError)
            }

            endTiming({ status: "error", settingsCount: settings.length, error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Flush the SQLite database to the Kotlin layer.
     */
    async flushSQLiteForKotlin(): Promise<void> {
        const endTiming = startTiming("database_flush_to_kotlin", "database")

        if (this.db) {
            await this.db.execAsync("PRAGMA wal_checkpoint(FULL);")
            logWithTimestamp("[DB] Successfully flushed SQLite database to Kotlin layer.")
            endTiming({ status: "success" })
        } else {
            logErrorWithTimestamp("Database is null when trying to flush to Kotlin layer.")
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database is null when trying to flush to Kotlin layer.")
        }
    }

    /**
     * Load a specific setting from database.
     */
    async loadSetting(category: string, key: string): Promise<any> {
        if (!this.db) {
            throw new Error("Database not initialized")
        }

        try {
            const result = await this.db.getFirstAsync<DatabaseSettings>("SELECT * FROM settings WHERE category = ? AND key = ?", [category, key])

            if (!result) {
                return null
            }

            // Settings that should remain as JSON strings (not parsed into objects)
            const stringOnlySettings = ["racingPlan", "racingPlanData"]

            if (stringOnlySettings.includes(key)) {
                return result.value
            } else {
                // Try to parse as JSON and fallback to string.
                try {
                    return JSON.parse(result.value)
                } catch {
                    return result.value
                }
            }
        } catch (error) {
            logErrorWithTimestamp(`[DB] Failed to load setting ${category}.${key}:`, error)
            throw error
        }
    }

    /**
     * Load all settings from database.
     */
    async loadAllSettings(): Promise<Record<string, Record<string, any>>> {
        const endTiming = startTiming("database_load_all_settings", "database")

        if (!this.db) {
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        try {
            const results = await this.db.getAllAsync<DatabaseSettings>("SELECT * FROM settings ORDER BY category, key")

            const settings: Record<string, Record<string, any>> = {}
            for (const result of results) {
                if (!settings[result.category]) {
                    settings[result.category] = {}
                }

                // Settings that should remain as JSON strings (not parsed into objects)
                const stringOnlySettings = ["racingPlan", "racingPlanData"]

                if (stringOnlySettings.includes(result.key)) {
                    settings[result.category][result.key] = result.value
                } else {
                    try {
                        settings[result.category][result.key] = JSON.parse(result.value)
                    } catch {
                        settings[result.category][result.key] = result.value
                    }
                }
            }

            endTiming({ status: "success", totalSettings: results.length, categoriesCount: Object.keys(settings).length })
            return settings
        } catch (error) {
            logErrorWithTimestamp("[DB] Failed to load all settings:", error)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Save a race to the database.
     */
    async saveRace(race: Omit<DatabaseRace, "id">): Promise<void> {
        const endTiming = startTiming("database_save_race", "database")

        if (!this.db) {
            logErrorWithTimestamp("Database is null when trying to save race.")
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        try {
            logWithTimestamp(`[DB] Saving race: ${race.name} (${race.turnNumber})`)
            await this.db.runAsync(
                `INSERT OR REPLACE INTO races (key, name, date, raceTrack, course, direction, grade, terrain, distanceType, distanceMeters, fans, turnNumber, nameFormatted) 
                 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
                [
                    race.key,
                    race.name,
                    race.date,
                    race.raceTrack,
                    race.course,
                    race.direction,
                    race.grade,
                    race.terrain,
                    race.distanceType,
                    race.distanceMeters,
                    race.fans,
                    race.turnNumber,
                    race.nameFormatted,
                ]
            )
            logWithTimestamp(`[DB] Successfully saved race: ${race.name}`)
            endTiming({ status: "success", raceName: race.name })
        } catch (error) {
            logErrorWithTimestamp(`[DB] Failed to save race ${race.name} (turn ${race.turnNumber}):`, error)
            endTiming({ status: "error", raceName: race.name, error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Save multiple races using prepared statements for better performance and security.
     */
    async saveRacesBatch(races: Array<Omit<DatabaseRace, "id">>): Promise<void> {
        const endTiming = startTiming("database_save_races_batch", "database")

        if (!this.db) {
            logErrorWithTimestamp("Database is null when trying to save races batch.")
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        if (races.length === 0) {
            endTiming({ status: "skipped", reason: "no_races" })
            return
        }

        try {
            await this.executeWithTransaction(async () => {
                logWithTimestamp(`[DB] Saving ${races.length} races using prepared statement.`)

                await this.db!.runAsync("BEGIN TRANSACTION")
                const stmt = await this.db!.prepareAsync(
                    `INSERT OR REPLACE INTO races (key, name, date, raceTrack, course, direction, grade, terrain, distanceType, distanceMeters, fans, turnNumber, nameFormatted) 
                     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
                )

                // Execute all races in batch using prepared statement.
                for (const race of races) {
                    await stmt.executeAsync([
                        race.key,
                        race.name,
                        race.date,
                        race.raceTrack,
                        race.course,
                        race.direction,
                        race.grade,
                        race.terrain,
                        race.distanceType,
                        race.distanceMeters,
                        race.fans,
                        race.turnNumber,
                        race.nameFormatted,
                    ])
                }

                // Finalize statement and commit transaction.
                await stmt.finalizeAsync()
                await this.db!.runAsync("COMMIT")

                logWithTimestamp(`[DB] Successfully saved ${races.length} races in batch.`)
            })

            endTiming({ status: "success", racesCount: races.length })
        } catch (error) {
            const racesInfo = races.length > 0 ? ` (${races.length} races: ${races.map((r) => `${r.name} (turn ${r.turnNumber})`).join(", ")})` : " (no races)"
            logErrorWithTimestamp(`[DB] Failed to save races batch${racesInfo}:`, error)

            // Rollback transaction on error.
            try {
                if (this.db && this.isTransactionActive) {
                    await this.db.runAsync("ROLLBACK")
                }
            } catch (rollbackError) {
                logErrorWithTimestamp(`[DB] Failed to rollback transaction${racesInfo}:`, rollbackError)
            }

            endTiming({ status: "error", racesCount: races.length, error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Load all races from database.
     */
    async loadAllRaces(): Promise<DatabaseRace[]> {
        const endTiming = startTiming("database_load_all_races", "database")

        if (!this.db) {
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        try {
            const results = await this.db.getAllAsync<DatabaseRace>("SELECT * FROM races ORDER BY turnNumber, name")
            endTiming({ status: "success", totalRaces: results.length })
            return results
        } catch (error) {
            logErrorWithTimestamp("[DB] Failed to load all races:", error)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Load races by turn number.
     */
    async loadRacesByTurnNumber(turnNumber: number): Promise<DatabaseRace[]> {
        const endTiming = startTiming("database_load_races_by_turn", "database")

        if (!this.db) {
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        try {
            const results = await this.db.getAllAsync<DatabaseRace>("SELECT * FROM races WHERE turnNumber = ? ORDER BY name", [turnNumber])
            endTiming({ status: "success", turnNumber, racesCount: results.length })
            return results
        } catch (error) {
            logErrorWithTimestamp(`[DB] Failed to load races for turn ${turnNumber}:`, error)
            endTiming({ status: "error", turnNumber, error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Clear all races from the database.
     */
    async clearRaces(): Promise<void> {
        const endTiming = startTiming("database_clear_races", "database")

        if (!this.db) {
            endTiming({ status: "error", error: "database_not_initialized" })
            throw new Error("Database not initialized")
        }

        try {
            await this.db.runAsync("DELETE FROM races")
            logWithTimestamp("[DB] Successfully cleared all races.")
            endTiming({ status: "success" })
        } catch (error) {
            logErrorWithTimestamp("[DB] Failed to clear races:", error)
            endTiming({ status: "error", error: error instanceof Error ? error.message : String(error) })
            throw error
        }
    }

    /**
     * Check if the database is properly initialized.
     */
    isInitialized(): boolean {
        return this.db !== null
    }

    /**
     * Clear the transaction queue and reset transaction state (for error recovery).
     */
    private clearTransactionQueue(): void {
        this.transactionQueue = []
        this.isTransactionActive = false
    }
}

// Available as a singleton instance.
export const databaseManager = new DatabaseManager()
