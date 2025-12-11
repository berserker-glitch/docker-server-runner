package com.dockermanager.service;

import com.dockermanager.model.Project;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class ProjectLogger {
    private static final Logger logger = LoggerFactory.getLogger(ProjectLogger.class);
    private static final String LOGS_BASE_DIR = System.getProperty("user.home") + File.separator + ".docker-project-manager" + File.separator + "project-logs";
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");
    
    private final Project project;
    private final String projectLogDir;
    private final String currentLogFile;
    private BufferedWriter logWriter;

    public ProjectLogger(Project project) {
        this.project = project;
        this.projectLogDir = LOGS_BASE_DIR + File.separator + sanitizeName(project.getName());
        this.currentLogFile = projectLogDir + File.separator + "run_" + LocalDateTime.now().format(FILE_FORMAT) + ".log";
        
        initializeLogFile();
    }

    private void initializeLogFile() {
        try {
            // Create project log directory if it doesn't exist
            Path logDirPath = Paths.get(projectLogDir);
            if (!Files.exists(logDirPath)) {
                Files.createDirectories(logDirPath);
                logger.info("Created log directory: {}", projectLogDir);
            }
            
            // Create log file
            File logFile = new File(currentLogFile);
            logFile.createNewFile();
            
            // Initialize writer
            logWriter = new BufferedWriter(new FileWriter(logFile, true));
            
            // Write header
            writeHeader();
            
        } catch (IOException e) {
            logger.error("Failed to initialize project log file: {}", e.getMessage(), e);
        }
    }

    private void writeHeader() {
        try {
            logWriter.write("=".repeat(80));
            logWriter.newLine();
            logWriter.write("Docker Project Manager - Project Log");
            logWriter.newLine();
            logWriter.write("Project: " + project.getName());
            logWriter.newLine();
            logWriter.write("Type: " + project.getType().getDisplayName());
            logWriter.newLine();
            logWriter.write("Path: " + project.getPath());
            logWriter.newLine();
            logWriter.write("Port: " + project.getPort());
            logWriter.newLine();
            logWriter.write("Started: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
            logWriter.newLine();
            logWriter.write("=".repeat(80));
            logWriter.newLine();
            logWriter.newLine();
            logWriter.flush();
        } catch (IOException e) {
            logger.error("Failed to write log header: {}", e.getMessage());
        }
    }

    public void logInfo(String message) {
        writeLog("INFO", message);
    }

    public void logError(String message) {
        writeLog("ERROR", message);
    }

    public void logBuild(String message) {
        writeLog("BUILD", message);
    }

    public void logContainer(String message) {
        writeLog("CONTAINER", message);
    }

    private void writeLog(String level, String message) {
        if (logWriter == null) {
            return;
        }

        try {
            String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
            String logLine = String.format("[%s] [%s] %s", timestamp, level, message);
            logWriter.write(logLine);
            logWriter.newLine();
            logWriter.flush(); // Flush immediately so logs are visible in real-time
        } catch (IOException e) {
            logger.error("Failed to write to project log: {}", e.getMessage());
        }
    }

    public void close() {
        if (logWriter != null) {
            try {
                logWriter.write("\n");
                logWriter.write("=".repeat(80));
                logWriter.newLine();
                logWriter.write("Log ended: " + LocalDateTime.now().format(TIMESTAMP_FORMAT));
                logWriter.newLine();
                logWriter.write("=".repeat(80));
                logWriter.newLine();
                logWriter.flush();
                logWriter.close();
                logger.info("Closed project log file: {}", currentLogFile);
            } catch (IOException e) {
                logger.error("Failed to close project log: {}", e.getMessage());
            }
        }
    }

    public String getLogFilePath() {
        return currentLogFile;
    }

    public String getLogDirectory() {
        return projectLogDir;
    }

    private String sanitizeName(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_]", "_");
    }

    /**
     * Get the latest log file for a project
     */
    public static String getLatestLogFile(Project project) {
        String projectLogDir = LOGS_BASE_DIR + File.separator + project.getName().replaceAll("[^a-zA-Z0-9-_]", "_");
        File dir = new File(projectLogDir);
        
        if (!dir.exists() || !dir.isDirectory()) {
            return null;
        }
        
        File[] logFiles = dir.listFiles((d, name) -> name.endsWith(".log"));
        if (logFiles == null || logFiles.length == 0) {
            return null;
        }
        
        // Sort by last modified, get the most recent
        File latest = logFiles[0];
        for (File f : logFiles) {
            if (f.lastModified() > latest.lastModified()) {
                latest = f;
            }
        }
        
        return latest.getAbsolutePath();
    }
}

