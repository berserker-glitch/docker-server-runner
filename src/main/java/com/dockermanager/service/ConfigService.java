package com.dockermanager.service;

import com.dockermanager.model.Project;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ConfigService {
    private static final Logger logger = LoggerFactory.getLogger(ConfigService.class);
    private static final String CONFIG_DIR = System.getProperty("user.home") + File.separator + ".docker-project-manager";
    private static final String PROJECTS_FILE = "projects.json";
    private static final String PREFERENCES_FILE = "preferences.json";
    
    private final Gson gson;
    private final Path configDirPath;
    private final Path projectsFilePath;
    private final Path preferencesFilePath;

    public ConfigService() {
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        this.configDirPath = Paths.get(CONFIG_DIR);
        this.projectsFilePath = configDirPath.resolve(PROJECTS_FILE);
        this.preferencesFilePath = configDirPath.resolve(PREFERENCES_FILE);
        
        initializeConfigDirectory();
    }

    private void initializeConfigDirectory() {
        try {
            if (!Files.exists(configDirPath)) {
                Files.createDirectories(configDirPath);
                logger.info("Created config directory: {}", configDirPath);
            }
            
            if (!Files.exists(projectsFilePath)) {
                saveProjects(new ArrayList<>());
                logger.info("Created projects file: {}", projectsFilePath);
            }
            
            if (!Files.exists(preferencesFilePath)) {
                savePreferences(new Preferences());
                logger.info("Created preferences file: {}", preferencesFilePath);
            }
            
        } catch (IOException e) {
            logger.error("Failed to initialize config directory", e);
        }
    }

    /**
     * Load all saved projects
     * @return list of projects
     */
    public List<Project> loadProjects() {
        try (FileReader reader = new FileReader(projectsFilePath.toFile())) {
            Type listType = new TypeToken<ArrayList<Project>>(){}.getType();
            List<Project> projects = gson.fromJson(reader, listType);
            
            if (projects == null) {
                projects = new ArrayList<>();
            }
            
            // Reset runtime fields and filter out null entries
            List<Project> validProjects = new ArrayList<>();
            for (Project project : projects) {
                if (project != null && project.getId() != null && project.getPath() != null) {
                    project.setStatus(Project.ProjectStatus.STOPPED);
                    project.setContainerId(null);
                    validProjects.add(project);
                } else {
                    logger.warn("Skipping invalid project entry in config");
                }
            }
            
            logger.info("Loaded {} projects from config", validProjects.size());
            return validProjects;
            
        } catch (Exception e) {
            logger.error("Failed to load projects: {}", e.getMessage(), e);
            return new ArrayList<>();
        }
    }

    /**
     * Save all projects
     * @param projects list of projects to save
     */
    public void saveProjects(List<Project> projects) {
        try (FileWriter writer = new FileWriter(projectsFilePath.toFile())) {
            gson.toJson(projects, writer);
            logger.info("Saved {} projects to config", projects.size());
            
        } catch (IOException e) {
            logger.error("Failed to save projects", e);
        }
    }

    /**
     * Add a new project
     * @param project the project to add
     * @param projects existing projects list
     */
    public void addProject(Project project, List<Project> projects) {
        // Check if project already exists by ID
        boolean exists = projects.stream().anyMatch(p -> p.getId().equals(project.getId()));
        if (!exists) {
            projects.add(project);
            saveProjects(projects);
            logger.info("Added project: {}", project.getName());
        } else {
            logger.warn("Project already exists: {}", project.getName());
        }
    }

    /**
     * Update an existing project
     * @param project the project to update
     * @param projects existing projects list
     */
    public void updateProject(Project project, List<Project> projects) {
        for (int i = 0; i < projects.size(); i++) {
            if (projects.get(i).getId().equals(project.getId())) {
                projects.set(i, project);
                saveProjects(projects);
                logger.info("Updated project: {}", project.getName());
                return;
            }
        }
    }

    /**
     * Delete a project
     * @param projectId the ID of the project to delete
     * @param projects existing projects list
     */
    public void deleteProject(String projectId, List<Project> projects) {
        projects.removeIf(p -> p.getId().equals(projectId));
        saveProjects(projects);
        logger.info("Deleted project with ID: {}", projectId);
    }

    /**
     * Load user preferences
     * @return preferences object
     */
    public Preferences loadPreferences() {
        try (FileReader reader = new FileReader(preferencesFilePath.toFile())) {
            Preferences prefs = gson.fromJson(reader, Preferences.class);
            if (prefs == null) {
                prefs = new Preferences();
            }
            logger.info("Loaded preferences");
            return prefs;
            
        } catch (IOException e) {
            logger.error("Failed to load preferences", e);
            return new Preferences();
        }
    }

    /**
     * Save user preferences
     * @param preferences preferences to save
     */
    public void savePreferences(Preferences preferences) {
        try (FileWriter writer = new FileWriter(preferencesFilePath.toFile())) {
            gson.toJson(preferences, writer);
            logger.info("Saved preferences");
            
        } catch (IOException e) {
            logger.error("Failed to save preferences", e);
        }
    }

    /**
     * Preferences class to store user settings
     */
    public static class Preferences {
        private String theme = "light";
        private long defaultMemoryLimit = 512;
        private double defaultCpuLimit = 1.0;
        private boolean autoStartDocker = false;
        private boolean deleteContainersOnStop = true;

        public String getTheme() {
            return theme;
        }

        public void setTheme(String theme) {
            this.theme = theme;
        }

        public long getDefaultMemoryLimit() {
            return defaultMemoryLimit;
        }

        public void setDefaultMemoryLimit(long defaultMemoryLimit) {
            this.defaultMemoryLimit = defaultMemoryLimit;
        }

        public double getDefaultCpuLimit() {
            return defaultCpuLimit;
        }

        public void setDefaultCpuLimit(double defaultCpuLimit) {
            this.defaultCpuLimit = defaultCpuLimit;
        }

        public boolean isAutoStartDocker() {
            return autoStartDocker;
        }

        public void setAutoStartDocker(boolean autoStartDocker) {
            this.autoStartDocker = autoStartDocker;
        }

        public boolean isDeleteContainersOnStop() {
            return deleteContainersOnStop;
        }

        public void setDeleteContainersOnStop(boolean deleteContainersOnStop) {
            this.deleteContainersOnStop = deleteContainersOnStop;
        }
    }
}

