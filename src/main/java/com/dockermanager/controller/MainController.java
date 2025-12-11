package com.dockermanager.controller;

import com.dockermanager.model.Project;
import com.dockermanager.model.ProjectType;
import com.dockermanager.service.*;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class MainController {
    private static final Logger logger = LoggerFactory.getLogger(MainController.class);

    @FXML private Label dockerStatusLabel;
    @FXML private VBox projectsContainer;
    @FXML private VBox settingsContainer;
    @FXML private VBox noProjectSelectedBox;
    @FXML private VBox settingsFormBox;
    
    // Settings form fields
    @FXML private TextField projectNameField;
    @FXML private TextField projectPathField;
    @FXML private Label projectTypeLabel;
    @FXML private TextField portField;
    @FXML private TextField memoryLimitField;
    @FXML private TextField cpuLimitField;
    @FXML private VBox envVarsContainer;
    @FXML private VBox volumeMountsContainer;
    @FXML private MenuItem addProjectMenuItem;

    // Services
    private DockerService dockerService;
    private ProjectDetectionService detectionService;
    private PortManagerService portManager;
    private ConfigService configService;

    // Data
    private List<Project> projects;
    private Project selectedProject;
    private Map<String, ProjectItemController> projectControllers;

    public void initialize() {
        logger.info("Initializing MainController");
        
        // Initialize services
        dockerService = new DockerService();
        detectionService = new ProjectDetectionService();
        portManager = new PortManagerService();
        configService = new ConfigService();
        
        // Initialize data structures
        projects = new ArrayList<>();
        projectControllers = new HashMap<>();
        
        // Update Docker status
        updateDockerStatus();
        
        // Load saved projects
        loadProjects();
        
        // Show no selection message initially
        showNoProjectSelected();
    }

    private void updateDockerStatus() {
        boolean available = dockerService.isDockerAvailable();
        Platform.runLater(() -> {
            if (available) {
                dockerStatusLabel.setText("✓ Available");
                dockerStatusLabel.setStyle("-fx-text-fill: #2ecc71; -fx-font-weight: bold;");
            } else {
                dockerStatusLabel.setText("✗ Not Available");
                dockerStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        });
    }

    private void loadProjects() {
        projects = configService.loadProjects();
        logger.info("Loaded {} projects", projects.size());
        
        // Re-assign ports for loaded projects
        for (Project project : projects) {
            if (project.getPort() > 0) {
                portManager.reservePort(project.getPort());
            }
        }
        
        refreshProjectsList();
    }

    private void refreshProjectsList() {
        Platform.runLater(() -> {
            projectsContainer.getChildren().clear();
            projectControllers.clear();
            
            for (Project project : projects) {
                addProjectToUI(project);
            }
        });
    }

    private void addProjectToUI(Project project) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/project-item.fxml"));
            VBox projectItem = loader.load();
            ProjectItemController controller = loader.getController();
            
            // Set callbacks BEFORE setting project (so click handler has the callback)
            controller.setOnRun(this::handleRunProject);
            controller.setOnStop(this::handleStopProject);
            controller.setOnSelect(this::handleSelectProject);
            controller.setOnRebuild(this::handleRebuildProject);
            controller.setOnViewLogs(this::handleViewLogs);
            
            // Now set the project (this will set up the click handler with callbacks)
            controller.setProject(project);
            
            projectControllers.put(project.getId(), controller);
            projectsContainer.getChildren().add(projectItem);
            
        } catch (IOException e) {
            logger.error("Failed to load project item UI", e);
            showError("UI Error", "Failed to load project item");
        }
    }

    @FXML
    private void handleAddProject() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Project Directory");
        
        Stage stage = (Stage) projectsContainer.getScene().getWindow();
        File selectedDir = chooser.showDialog(stage);
        
        if (selectedDir != null) {
            addNewProject(selectedDir);
        }
    }

    private void addNewProject(File directory) {
        String path = directory.getAbsolutePath();
        
        // Check if project already exists
        for (Project existingProject : projects) {
            if (existingProject.getPath().equals(path)) {
                showWarning("Project Already Exists", 
                    "A project at this location already exists: " + existingProject.getName());
                return;
            }
        }
        
        // Detect project type
        ProjectType type = detectionService.detectProjectType(path);
        
        if (type == ProjectType.UNKNOWN) {
            showWarning("Unknown Project Type", 
                "Could not automatically detect the project type.\nPlease make sure the directory contains a valid project.");
            return;
        }
        
        // Create new project
        String name = directory.getName();
        Project project = new Project(name, path, type);
        
        // Assign port
        int defaultPort = detectionService.detectDefaultPort(path, type);
        int assignedPort = portManager.findAvailablePort(defaultPort);
        project.setPort(assignedPort);
        
        // Add to list
        projects.add(project);
        configService.saveProjects(projects);
        
        // Add to UI
        addProjectToUI(project);
        
        // Select the new project
        handleSelectProject(project);
        
        showInfo("Project Added", "Project '" + name + "' has been added successfully!");
    }

    private void handleRunProject(Project project) {
        if (!dockerService.isDockerAvailable()) {
            showError("Docker Not Available", 
                "Docker is not installed or not running.\nPlease install Docker and make sure it's running.");
            return;
        }
        
        logger.info("Running project: {}", project.getName());
        
        // Update UI immediately
        ProjectItemController controller = projectControllers.get(project.getId());
        if (controller != null) {
            controller.updateStatus(Project.ProjectStatus.STARTING);
        }
        
        // Start in background thread
        new Thread(() -> {
            String containerId = dockerService.startProject(project);
            
            Platform.runLater(() -> {
                if (containerId != null) {
                    project.setContainerId(containerId);
                    project.setStatus(Project.ProjectStatus.RUNNING);
                    
                    if (controller != null) {
                        controller.updateStatus(Project.ProjectStatus.RUNNING);
                    }
                    
                    String url = "http://localhost:" + project.getPort();
                    showInfo("Project Started", 
                        "Project '" + project.getName() + "' is now running!\n\nAccess it at: " + url);
                } else {
                    project.setStatus(Project.ProjectStatus.ERROR);
                    
                    if (controller != null) {
                        controller.updateStatus(Project.ProjectStatus.ERROR);
                    }
                    
                    showError("Start Failed", 
                        "Failed to start project '" + project.getName() + "'.\nCheck the logs for more details.");
                }
            });
        }).start();
    }

    private void handleStopProject(Project project) {
        logger.info("Stopping project: {}", project.getName());
        
        new Thread(() -> {
            dockerService.stopProject(project);
            
            Platform.runLater(() -> {
                ProjectItemController controller = projectControllers.get(project.getId());
                if (controller != null) {
                    controller.updateStatus(Project.ProjectStatus.STOPPED);
                }
                
                showInfo("Project Stopped", "Project '" + project.getName() + "' has been stopped.");
            });
        }).start();
    }
    
    private void handleRebuildProject(Project project) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Rebuild Project");
        alert.setHeaderText("Rebuild Docker image?");
        alert.setContentText("This will rebuild the Docker image from scratch.\nThis may take a few minutes.");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                logger.info("Rebuilding project: {}", project.getName());
                
                // First delete the existing image
                dockerService.deleteProjectImage(project);
                
                // Then start the project (which will rebuild)
                handleRunProject(project);
            }
        });
    }
    
    private void handleViewLogs(Project project) {
        try {
            String logDir = System.getProperty("user.home") + "/.docker-project-manager/project-logs/" + 
                           project.getName().replaceAll("[^a-zA-Z0-9-_]", "_");
            
            File dir = new File(logDir);
            if (!dir.exists()) {
                showWarning("No Logs Found", "No logs found for this project yet.\nLogs are created when you run the project.");
                return;
            }
            
            // Open the log directory in file explorer
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                Runtime.getRuntime().exec("explorer " + logDir);
            } else if (System.getProperty("os.name").toLowerCase().contains("mac")) {
                Runtime.getRuntime().exec("open " + logDir);
            } else {
                Runtime.getRuntime().exec("xdg-open " + logDir);
            }
            
            showInfo("Opening Logs", "Opening log directory:\n" + logDir);
            
        } catch (Exception e) {
            logger.error("Failed to open logs directory", e);
            showError("Error", "Failed to open logs directory:\n" + e.getMessage());
        }
    }

    private void handleSelectProject(Project project) {
        selectedProject = project;
        
        // Update selection highlight
        for (Map.Entry<String, ProjectItemController> entry : projectControllers.entrySet()) {
            entry.getValue().setSelected(entry.getKey().equals(project.getId()));
        }
        
        // Show settings form
        showProjectSettings(project);
    }

    private void showNoProjectSelected() {
        Platform.runLater(() -> {
            noProjectSelectedBox.setVisible(true);
            noProjectSelectedBox.setManaged(true);
            settingsFormBox.setVisible(false);
            settingsFormBox.setManaged(false);
        });
    }

    private void showProjectSettings(Project project) {
        Platform.runLater(() -> {
            noProjectSelectedBox.setVisible(false);
            noProjectSelectedBox.setManaged(false);
            settingsFormBox.setVisible(true);
            settingsFormBox.setManaged(true);
            
            // Populate fields
            projectNameField.setText(project.getName());
            projectPathField.setText(project.getPath());
            
            ProjectType type = project.getType();
            projectTypeLabel.setText(type.getDisplayName());
            projectTypeLabel.getStyleClass().removeIf(s -> s.startsWith("type-"));
            projectTypeLabel.getStyleClass().add("type-" + type.name().toLowerCase());
            
            portField.setText(String.valueOf(project.getPort()));
            memoryLimitField.setText(String.valueOf(project.getDockerOptions().getMemoryLimit()));
            cpuLimitField.setText(String.valueOf(project.getDockerOptions().getCpuLimit()));
            
            // Load environment variables
            loadEnvVars(project);
            
            // Load volume mounts
            loadVolumeMounts(project);
        });
    }

    private void loadEnvVars(Project project) {
        envVarsContainer.getChildren().clear();
        
        for (Map.Entry<String, String> entry : project.getEnvironmentVariables().entrySet()) {
            addEnvVarRow(entry.getKey(), entry.getValue());
        }
    }

    private void loadVolumeMounts(Project project) {
        volumeMountsContainer.getChildren().clear();
        
        for (Map.Entry<String, String> entry : project.getDockerOptions().getVolumeMounts().entrySet()) {
            addVolumeMountRow(entry.getKey(), entry.getValue());
        }
    }

    @FXML
    private void handleAddEnvVar() {
        addEnvVarRow("", "");
    }

    private void addEnvVarRow(String key, String value) {
        HBox row = new HBox(10);
        row.getStyleClass().add("env-var-row");
        
        TextField keyField = new TextField(key);
        keyField.setPromptText("Key");
        keyField.setPrefWidth(150);
        
        TextField valueField = new TextField(value);
        valueField.setPromptText("Value");
        valueField.setPrefWidth(250);
        
        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("small-button");
        removeBtn.setOnAction(e -> envVarsContainer.getChildren().remove(row));
        
        row.getChildren().addAll(keyField, valueField, removeBtn);
        envVarsContainer.getChildren().add(row);
    }

    @FXML
    private void handleAddVolumeMount() {
        addVolumeMountRow("", "");
    }

    private void addVolumeMountRow(String host, String container) {
        HBox row = new HBox(10);
        row.getStyleClass().add("volume-mount-row");
        
        TextField hostField = new TextField(host);
        hostField.setPromptText("Host Path");
        hostField.setPrefWidth(200);
        
        Label arrow = new Label("→");
        
        TextField containerField = new TextField(container);
        containerField.setPromptText("Container Path");
        containerField.setPrefWidth(200);
        
        Button removeBtn = new Button("×");
        removeBtn.getStyleClass().add("small-button");
        removeBtn.setOnAction(e -> volumeMountsContainer.getChildren().remove(row));
        
        row.getChildren().addAll(hostField, arrow, containerField, removeBtn);
        volumeMountsContainer.getChildren().add(row);
    }

    @FXML
    private void handleSaveSettings() {
        if (selectedProject == null) return;
        
        try {
            // Update project from form
            selectedProject.setName(projectNameField.getText());
            
            // Update port
            int newPort = Integer.parseInt(portField.getText());
            if (newPort != selectedProject.getPort()) {
                if (portManager.updatePort(selectedProject.getPort(), newPort)) {
                    selectedProject.setPort(newPort);
                } else {
                    showWarning("Port Unavailable", "The specified port is not available.");
                    return;
                }
            }
            
            // Update Docker options
            selectedProject.getDockerOptions().setMemoryLimit(Long.parseLong(memoryLimitField.getText()));
            selectedProject.getDockerOptions().setCpuLimit(Double.parseDouble(cpuLimitField.getText()));
            
            // Update environment variables
            Map<String, String> envVars = new HashMap<>();
            for (javafx.scene.Node node : envVarsContainer.getChildren()) {
                if (node instanceof HBox row) {
                    TextField keyField = (TextField) row.getChildren().get(0);
                    TextField valueField = (TextField) row.getChildren().get(1);
                    String key = keyField.getText().trim();
                    String value = valueField.getText().trim();
                    if (!key.isEmpty()) {
                        envVars.put(key, value);
                    }
                }
            }
            selectedProject.setEnvironmentVariables(envVars);
            
            // Update volume mounts
            Map<String, String> volumes = new HashMap<>();
            for (javafx.scene.Node node : volumeMountsContainer.getChildren()) {
                if (node instanceof HBox row) {
                    TextField hostField = (TextField) row.getChildren().get(0);
                    TextField containerField = (TextField) row.getChildren().get(2);
                    String host = hostField.getText().trim();
                    String container = containerField.getText().trim();
                    if (!host.isEmpty() && !container.isEmpty()) {
                        volumes.put(host, container);
                    }
                }
            }
            selectedProject.getDockerOptions().setVolumeMounts(volumes);
            
            // Save to config
            configService.updateProject(selectedProject, projects);
            
            // Update UI
            ProjectItemController controller = projectControllers.get(selectedProject.getId());
            if (controller != null) {
                controller.updateUI();
            }
            
            showInfo("Settings Saved", "Project settings have been saved successfully!");
            
        } catch (NumberFormatException e) {
            showError("Invalid Input", "Please enter valid numbers for port, memory, and CPU limits.");
        }
    }

    @FXML
    private void handleDeleteProject() {
        if (selectedProject == null) return;
        
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Delete Project");
        alert.setHeaderText("Are you sure you want to delete this project?");
        alert.setContentText("Project: " + selectedProject.getName() + "\n\nThis will only remove it from the manager, not delete the actual files.");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                // Stop if running
                if (selectedProject.getStatus() == Project.ProjectStatus.RUNNING) {
                    handleStopProject(selectedProject);
                }
                
                // Release port
                portManager.releasePort(selectedProject.getPort());
                
                // Remove from list
                String projectId = selectedProject.getId();
                projects.removeIf(p -> p.getId().equals(projectId));
                configService.deleteProject(projectId, projects);
                
                // Remove from UI
                projectControllers.remove(projectId);
                
                // Clear selection
                selectedProject = null;
                showNoProjectSelected();
                refreshProjectsList();
                
                showInfo("Project Deleted", "Project has been removed from the manager.");
            }
        });
    }

    @FXML
    private void handleBrowseProjectPath() {
        // Not implemented - path shouldn't be changed after creation
        showWarning("Path Change", "Project path cannot be changed after creation.");
    }

    @FXML
    private void handleCheckDocker() {
        boolean available = dockerService.checkDockerInstallation();
        updateDockerStatus();
        
        if (available) {
            showInfo("Docker Status", "Docker is installed and running!");
        } else {
            showError("Docker Status", 
                "Docker is not installed or not running.\n\n" +
                "Please install Docker from: https://www.docker.com/get-started");
        }
    }

    @FXML
    private void handleStopAll() {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Stop All Containers");
        alert.setHeaderText("Stop all running containers?");
        alert.setContentText("This will stop all projects that are currently running.");
        
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                dockerService.stopAllContainers(projects);
                refreshProjectsList();
                showInfo("Containers Stopped", "All running containers have been stopped.");
            }
        });
    }

    @FXML
    private void handlePreferences() {
        showInfo("Preferences", "Preferences dialog - Coming soon!");
    }

    @FXML
    private void handleAbout() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("About");
        alert.setHeaderText("Docker Project Manager");
        alert.setContentText(
            "Version 1.0\n\n" +
            "A JavaFX application for managing and running projects with Docker.\n\n" +
            "Features:\n" +
            "• Auto-detect project types (HTML, Node.js, React, Full-stack)\n" +
            "• Generate Dockerfiles automatically\n" +
            "• Manage Docker containers\n" +
            "• Configure ports, environment variables, and more"
        );
        alert.showAndWait();
    }

    @FXML
    private void handleExit() {
        // Stop all containers before exit
        dockerService.stopAllContainers(projects);
        dockerService.close();
        Platform.exit();
    }

    // Utility methods for dialogs
    private void showInfo(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showWarning(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    private void showError(String title, String content) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(content);
            alert.showAndWait();
        });
    }

    public void shutdown() {
        logger.info("Shutting down application");
        dockerService.stopAllContainers(projects);
        dockerService.close();
    }
}

