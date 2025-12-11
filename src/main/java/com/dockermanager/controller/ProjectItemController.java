package com.dockermanager.controller;

import com.dockermanager.model.Project;
import com.dockermanager.model.ProjectType;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.VBox;

import java.util.function.Consumer;

public class ProjectItemController {
    
    @FXML private VBox projectCard;
    @FXML private Label projectNameLabel;
    @FXML private Label projectTypeLabel;
    @FXML private Label portLabel;
    @FXML private Label statusIndicator;
    @FXML private Button runButton;
    @FXML private Button stopButton;
    
    private Project project;
    private Consumer<Project> onRunCallback;
    private Consumer<Project> onStopCallback;
    private Consumer<Project> onSelectCallback;
    private Consumer<Project> onRebuildCallback;
    private Consumer<Project> onViewLogsCallback;

    public void initialize() {
        // Click handler will be set up after project is set
    }
    
    private void setupClickHandler() {
        // Set up click handler for project selection
        if (projectCard != null) {
            projectCard.setOnMouseClicked(event -> {
                if (onSelectCallback != null && project != null) {
                    onSelectCallback.accept(project);
                }
            });
        }
    }

    public void setProject(Project project) {
        this.project = project;
        updateUI();
    }

    public void updateUI() {
        if (project == null) return;

        Platform.runLater(() -> {
            projectNameLabel.setText(project.getName());
            
            // Set type badge
            ProjectType type = project.getType();
            projectTypeLabel.setText(type.getDisplayName());
            projectTypeLabel.getStyleClass().removeIf(s -> s.startsWith("type-"));
            projectTypeLabel.getStyleClass().add("type-" + type.name().toLowerCase());
            
            // Set port label
            if (project.getPort() > 0) {
                portLabel.setText("Port: " + project.getPort());
            } else {
                portLabel.setText("Port: Not assigned");
            }
            
            // Update status indicator
            updateStatus(project.getStatus());
        });
    }

    public void updateStatus(Project.ProjectStatus status) {
        Platform.runLater(() -> {
            statusIndicator.getStyleClass().removeIf(s -> s.startsWith("status-"));
            statusIndicator.getStyleClass().add("status-" + status.name().toLowerCase());
            
            // Show/hide buttons based on status
            switch (status) {
                case STOPPED, ERROR -> {
                    runButton.setVisible(true);
                    runButton.setManaged(true);
                    stopButton.setVisible(false);
                    stopButton.setManaged(false);
                    runButton.setDisable(false);
                }
                case STARTING -> {
                    runButton.setVisible(true);
                    runButton.setManaged(true);
                    stopButton.setVisible(false);
                    stopButton.setManaged(false);
                    runButton.setDisable(true);
                }
                case RUNNING -> {
                    runButton.setVisible(false);
                    runButton.setManaged(false);
                    stopButton.setVisible(true);
                    stopButton.setManaged(true);
                    stopButton.setDisable(false);
                }
            }
        });
    }

    @FXML
    private void handleRun() {
        if (onRunCallback != null && project != null) {
            onRunCallback.accept(project);
        }
    }

    @FXML
    private void handleStop() {
        if (onStopCallback != null && project != null) {
            onStopCallback.accept(project);
        }
    }

    public void setOnRun(Consumer<Project> callback) {
        this.onRunCallback = callback;
    }

    public void setOnStop(Consumer<Project> callback) {
        this.onStopCallback = callback;
    }

    public void setOnSelect(Consumer<Project> callback) {
        this.onSelectCallback = callback;
        setupClickHandler(); // Set up click handler when callback is set
    }
    
    public void setOnRebuild(Consumer<Project> callback) {
        this.onRebuildCallback = callback;
        setupContextMenu(); // Set up context menu with rebuild option
    }
    
    public void setOnViewLogs(Consumer<Project> callback) {
        this.onViewLogsCallback = callback;
        setupContextMenu(); // Update context menu
    }
    
    private void setupContextMenu() {
        if (projectCard != null) {
            javafx.scene.control.ContextMenu contextMenu = new javafx.scene.control.ContextMenu();
            
            // View Logs menu item
            if (onViewLogsCallback != null) {
                javafx.scene.control.MenuItem viewLogsItem = new javafx.scene.control.MenuItem("📋 View Logs");
                viewLogsItem.setOnAction(e -> {
                    if (project != null) {
                        onViewLogsCallback.accept(project);
                    }
                });
                contextMenu.getItems().add(viewLogsItem);
            }
            
            // Rebuild menu item
            if (onRebuildCallback != null) {
                javafx.scene.control.MenuItem rebuildItem = new javafx.scene.control.MenuItem("🔄 Rebuild Image");
                rebuildItem.setOnAction(e -> {
                    if (project != null) {
                        onRebuildCallback.accept(project);
                    }
                });
                contextMenu.getItems().add(rebuildItem);
            }
            
            projectCard.setOnContextMenuRequested(e -> contextMenu.show(projectCard, e.getScreenX(), e.getScreenY()));
        }
    }

    public void setSelected(boolean selected) {
        Platform.runLater(() -> {
            if (selected) {
                projectCard.getStyleClass().add("project-card-selected");
            } else {
                projectCard.getStyleClass().remove("project-card-selected");
            }
        });
    }

    public Project getProject() {
        return project;
    }
}

