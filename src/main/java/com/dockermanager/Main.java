package com.dockermanager;

import com.dockermanager.controller.MainController;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Main extends Application {
    private static final Logger logger = LoggerFactory.getLogger(Main.class);
    private MainController mainController;

    @Override
    public void start(Stage primaryStage) {
        try {
            logger.info("Starting Docker Project Manager");

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/main.fxml"));
            Parent root = loader.load();
            mainController = loader.getController();

            // Create scene
            Scene scene = new Scene(root, 1200, 700);
            
            // Load CSS
            String css = getClass().getResource("/css/styles.css").toExternalForm();
            scene.getStylesheets().add(css);

            // Configure stage
            primaryStage.setTitle("Docker Project Manager");
            primaryStage.setScene(scene);
            primaryStage.setMinWidth(1000);
            primaryStage.setMinHeight(600);

            // Set application icon (if available)
            try {
                Image icon = new Image(getClass().getResourceAsStream("/icon.png"));
                primaryStage.getIcons().add(icon);
            } catch (Exception e) {
                logger.debug("Application icon not found, using default");
            }

            // Handle window close event
            primaryStage.setOnCloseRequest(event -> {
                logger.info("Application closing");
                if (mainController != null) {
                    mainController.shutdown();
                }
                Platform.exit();
                System.exit(0);
            });

            // Show the stage
            primaryStage.show();
            
            logger.info("Application started successfully");

        } catch (Exception e) {
            logger.error("Failed to start application", e);
            showStartupError(e);
            Platform.exit();
        }
    }

    private void showStartupError(Exception e) {
        javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
            javafx.scene.control.Alert.AlertType.ERROR
        );
        alert.setTitle("Startup Error");
        alert.setHeaderText("Failed to start Docker Project Manager");
        alert.setContentText(
            "An error occurred during startup:\n\n" + 
            e.getMessage() + "\n\n" +
            "Please check the logs for more details."
        );
        alert.showAndWait();
    }

    @Override
    public void stop() {
        logger.info("Application stopping");
        if (mainController != null) {
            mainController.shutdown();
        }
    }

    public static void main(String[] args) {
        // Set system properties for better logging
        System.setProperty("org.slf4j.simpleLogger.defaultLogLevel", "INFO");
        System.setProperty("org.slf4j.simpleLogger.showDateTime", "true");
        System.setProperty("org.slf4j.simpleLogger.dateTimeFormat", "yyyy-MM-dd HH:mm:ss");
        
        logger.info("Docker Project Manager starting...");
        
        // Launch JavaFX application
        launch(args);
    }
}

