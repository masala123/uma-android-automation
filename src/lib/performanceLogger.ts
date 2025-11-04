/**
 * Performance logging utility for tracking operation timing and performance metrics.
 * Provides detailed timing information for debugging performance issues.
 */

export interface PerformanceMetric {
    operation: string
    duration: number
    timestamp: number
    details?: Record<string, any>
    category: "database" | "settings" | "state" | "ui"
}

export interface PerformanceLoggerOptions {
    enableConsoleLogging?: boolean
    maxMetricsHistory?: number
}

class PerformanceLogger {
    private metrics: PerformanceMetric[] = []
    private options: PerformanceLoggerOptions

    constructor(options: PerformanceLoggerOptions = {}) {
        this.options = {
            enableConsoleLogging: true,
            maxMetricsHistory: 1000,
            ...options,
        }
    }

    /**
     * Start timing an operation.
     */
    startTiming(operation: string, category: PerformanceMetric["category"] = "settings"): (details?: Record<string, any>) => PerformanceMetric {
        const startTime = performance.now()
        const timestamp = Date.now()

        return (details?: Record<string, any>) => {
            const endTime = performance.now()
            const duration = endTime - startTime

            const metric: PerformanceMetric = {
                operation,
                duration,
                timestamp,
                details,
                category,
            }

            this.recordMetric(metric)
            return metric
        }
    }

    /**
     * Record a performance metric.
     */
    recordMetric(metric: PerformanceMetric) {
        this.metrics.push(metric)

        // Keep only the most recent metrics to prevent memory issues.
        if (this.metrics.length > (this.options.maxMetricsHistory || 1000)) {
            this.metrics = this.metrics.slice(-(this.options.maxMetricsHistory || 1000))
        }

        this.logMetric(metric)
    }

    /**
     * Log a performance metric to console.
     */
    private logMetric(metric: PerformanceMetric) {
        const logMessage = `[PERF] ${metric.category.toUpperCase()} - ${metric.operation}: ${metric.duration.toFixed(2)}ms${metric.details ? ` | Details: ${JSON.stringify(metric.details)}` : ""}`

        if (this.options.enableConsoleLogging) {
            if (metric.duration > 500) {
                console.warn(logMessage) // Warn for slow operations.
            } else {
                console.log(logMessage)
            }
        }
    }
}

// Create singleton instance.
export const performanceLogger = new PerformanceLogger()

// Export convenience functions.
export const startTiming = (operation: string, category?: PerformanceMetric["category"]) => performanceLogger.startTiming(operation, category)
