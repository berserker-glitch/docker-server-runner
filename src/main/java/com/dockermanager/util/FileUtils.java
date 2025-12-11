package com.dockermanager.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class FileUtils {
    
    public static boolean fileExists(String directory, String fileName) {
        File file = new File(directory, fileName);
        return file.exists() && file.isFile();
    }

    public static boolean directoryExists(String directory, String dirName) {
        File dir = new File(directory, dirName);
        return dir.exists() && dir.isDirectory();
    }

    public static String readFileContent(String filePath) throws IOException {
        Path path = Paths.get(filePath);
        return Files.readString(path);
    }

    public static void writeFileContent(String filePath, String content) throws IOException {
        Path path = Paths.get(filePath);
        Files.createDirectories(path.getParent());
        Files.writeString(path, content);
    }

    public static String getFileName(String path) {
        File file = new File(path);
        return file.getName();
    }

    public static boolean isValidDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return false;
        }
        File dir = new File(path);
        return dir.exists() && dir.isDirectory();
    }
}

