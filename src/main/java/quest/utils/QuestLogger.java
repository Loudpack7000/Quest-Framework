package quest.utils;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Simple, reliable quest logging utility
 * Ensures ALL automation logs get written to files regardless of DreamBot's logging system
 */
public class QuestLogger {
    
    private static QuestLogger instance;
    private BufferedWriter currentLogWriter;
    private String currentLogFile;
    private String currentQuestName;
    
    private QuestLogger() {
        // Private constructor for singleton
    }
    
    public static QuestLogger getInstance() {
        if (instance == null) {
            instance = new QuestLogger();
        }
        return instance;
    }
    
    /**
     * Initialize logging for a specific quest
     */
    public void initializeQuest(String questName) {
        try {
            // Close any existing log
            close();
            
            this.currentQuestName = questName;
            
            // Create quest_logs directory
            File logDir = new File("quest_logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }
            
            // Generate unique log filename
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
            currentLogFile = "quest_logs/QUEST_" + questName.replaceAll("[^a-zA-Z0-9]", "_") + "_AUTO_" + timestamp + ".log";
            
            // Open log file for writing
            currentLogWriter = new BufferedWriter(new FileWriter(currentLogFile, true));
            
            // Write header
            writeHeader();
            
            // Log initialization
            log("QUEST_LOGGER", "[SUCCESS] Quest logging initialized for: " + questName);
            log("QUEST_LOGGER", "[INFO] Log file: " + currentLogFile);
            
        } catch (Exception e) {
            System.err.println("[ERROR] Failed to initialize quest logger: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Write header to log file
     */
    private void writeHeader() throws IOException {
        if (currentLogWriter != null) {
            currentLogWriter.write("=====================================\n");
            currentLogWriter.write("    AUTOMATED QUEST EXECUTION LOG   \n");
            currentLogWriter.write("=====================================\n");
            currentLogWriter.write("Quest: " + currentQuestName + "\n");
            currentLogWriter.write("Started: " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) + "\n");
            currentLogWriter.write("Mode: AUTOMATED EXECUTION\n");
            currentLogWriter.write("=====================================\n\n");
            currentLogWriter.flush();
        }
    }
    
    /**
     * Log a message with category
     */
    public void log(String category, String message) {
        try {
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String logLine = String.format("[%s] %s: %s\n", timestamp, category, message);
            
            // 1. Write to file
            if (currentLogWriter != null) {
                currentLogWriter.write(logLine);
                currentLogWriter.flush(); // Immediate flush to ensure logs are written
            }
            
            // 2. Write to console
            System.out.println("[QuestLogger] " + logLine.trim());
            
        } catch (Exception e) {
            System.err.println("Failed to write log: " + e.getMessage());
        }
    }
    
    /**
     * Log a simple message (uses AUTOMATION category)
     */
    public void log(String message) {
        log("AUTOMATION", message);
    }
    
    /**
     * Log debug information
     */
    public void debug(String message) {
        log("DEBUG", "🔧 " + message);
    }
    
    /**
     * Log success messages
     */
    public void success(String message) {
        log("SUCCESS", "[SUCCESS] " + message);
    }
    
    /**
     * Log error messages
     */
    public void error(String message) {
        log("ERROR", "[ERROR] " + message);
    }
    
    /**
     * Log warning messages
     */
    public void warning(String message) {
        log("WARNING", "[WARNING] " + message);
    }
    
    /**
     * Log Grand Exchange related messages
     */
    public void ge(String message) {
        log("GRAND_EXCHANGE", "🛒 " + message);
    }
    
    /**
     * Log quest progression messages
     */
    public void quest(String message) {
        log("QUEST_PROGRESS", "🎯 " + message);
    }
    
    /**
     * Close the current log file
     */
    public void close() {
        try {
            if (currentLogWriter != null) {
                log("QUEST_LOGGER", "[INFO] Quest logging session ended");
                currentLogWriter.write("\n=====================================\n");
                currentLogWriter.write("    QUEST EXECUTION LOG ENDED       \n");
                currentLogWriter.write("=====================================\n");
                currentLogWriter.close();
                currentLogWriter = null;
            }
        } catch (Exception e) {
            System.err.println("Error closing quest logger: " + e.getMessage());
        }
    }
    
    /**
     * Get current log file path
     */
    public String getCurrentLogFile() {
        return currentLogFile;
    }
    
    /**
     * Check if logger is active
     */
    public boolean isActive() {
        return currentLogWriter != null;
    }
} 