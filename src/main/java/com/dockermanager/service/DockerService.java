package com.dockermanager.service;

import com.dockermanager.model.Project;
import com.dockermanager.model.ProjectType;
import com.dockermanager.util.DockerfileGenerator;
import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.BuildImageResultCallback;
import com.github.dockerjava.api.command.CreateContainerResponse;
import com.github.dockerjava.api.model.*;
import com.github.dockerjava.api.command.InspectContainerResponse;
import com.github.dockerjava.core.DefaultDockerClientConfig;
import com.github.dockerjava.core.DockerClientConfig;
import com.github.dockerjava.core.DockerClientImpl;
import com.github.dockerjava.httpclient5.ApacheDockerHttpClient;
import com.github.dockerjava.transport.DockerHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DockerService {
    private static final Logger logger = LoggerFactory.getLogger(DockerService.class);
    private DockerClient dockerClient;
    private boolean dockerAvailable = false;

    public DockerService() {
        checkDockerInstallation();
        if (dockerAvailable) {
            initializeDockerClient();
        }
    }

    /**
     * Check if Docker is installed on the system
     * @return true if Docker is installed and running
     */
    public boolean checkDockerInstallation() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "--version");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line = reader.readLine();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0 && line != null && line.toLowerCase().contains("docker")) {
                logger.info("Docker detected: {}", line);
                dockerAvailable = true;
                
                // Also check if Docker daemon is running
                return checkDockerDaemon();
            } else {
                logger.warn("Docker is not installed or not in PATH");
                dockerAvailable = false;
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error checking Docker installation: {}", e.getMessage());
            dockerAvailable = false;
            return false;
        }
    }

    /**
     * Check if Docker daemon is running
     * @return true if daemon is running
     */
    private boolean checkDockerDaemon() {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder("docker", "info");
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            
            int exitCode = process.waitFor();
            
            if (exitCode == 0) {
                logger.info("Docker daemon is running");
                return true;
            } else {
                logger.warn("Docker daemon is not running");
                return false;
            }
            
        } catch (Exception e) {
            logger.error("Error checking Docker daemon: {}", e.getMessage());
            return false;
        }
    }

    /**
     * Initialize Docker client
     */
    private void initializeDockerClient() {
        try {
            DockerClientConfig config = DefaultDockerClientConfig.createDefaultConfigBuilder().build();
            
            DockerHttpClient httpClient = new ApacheDockerHttpClient.Builder()
                    .dockerHost(config.getDockerHost())
                    .sslConfig(config.getSSLConfig())
                    .maxConnections(100)
                    .connectionTimeout(Duration.ofSeconds(30))
                    .responseTimeout(Duration.ofSeconds(45))
                    .build();
            
            dockerClient = DockerClientImpl.getInstance(config, httpClient);
            
            // Test the connection
            dockerClient.pingCmd().exec();
            logger.info("Docker client initialized successfully");
            
        } catch (Exception e) {
            logger.error("Failed to initialize Docker client: {}", e.getMessage(), e);
            dockerAvailable = false;
        }
    }

    /**
     * Build and start a container for a project
     * @param project the project to run
     * @return container ID if successful, null otherwise
     */
    public String startProject(Project project) {
        if (!dockerAvailable) {
            logger.error("Docker is not available");
            return null;
        }

        try {
            logger.info("Starting project: {}", project.getName());
            project.setStatus(Project.ProjectStatus.STARTING);

            // Generate Dockerfile
            if (project.getType() == ProjectType.FULLSTACK) {
                return startFullStackProject(project);
            } else {
                return startSingleProject(project);
            }

        } catch (Exception e) {
            logger.error("Failed to start project: {}", e.getMessage(), e);
            project.setStatus(Project.ProjectStatus.ERROR);
            return null;
        }
    }

    private String startSingleProject(Project project) throws Exception {
        String imageName = sanitizeImageName(project.getName());
        String imageTag = imageName + ":" + project.getId().substring(0, 8);
        
        // Initialize project logs
        ProjectLogger projectLogger = new ProjectLogger(project);
        projectLogger.logInfo("Starting project: " + project.getName());
        
        // Check if image already exists
        boolean imageExists = checkImageExists(imageTag);
        
        if (!imageExists) {
            logger.info("Image not found, building new image: {}", imageTag);
            projectLogger.logInfo("Image not found, building new image: " + imageTag);
            
            // Generate Dockerfile
            String dockerfile = DockerfileGenerator.generateDockerfile(
                    project.getPath(), 
                    project.getType(), 
                    project.getPort()
            );
            
            if (dockerfile != null) {
                DockerfileGenerator.writeDockerfile(project.getPath(), dockerfile);
                projectLogger.logInfo("Generated Dockerfile");
            }

            // Build image with logging
            logger.info("Building Docker image: {}", imageTag);
            projectLogger.logInfo("Building Docker image: " + imageTag);
            
            BuildImageResultCallback callback = new BuildImageResultCallback() {
                @Override
                public void onNext(BuildResponseItem item) {
                    if (item.getStream() != null) {
                        String logLine = item.getStream().trim();
                        logger.debug("Build: {}", logLine);
                        projectLogger.logBuild(logLine);
                    }
                    super.onNext(item);
                }
            };
            
            String imageId = dockerClient.buildImageCmd()
                    .withDockerfile(new File(project.getPath(), "Dockerfile"))
                    .withBaseDirectory(new File(project.getPath()))
                    .withTags(new HashSet<>(Collections.singletonList(imageTag)))
                    .exec(callback)
                    .awaitImageId(5, TimeUnit.MINUTES);
            
            logger.info("Built image: {}", imageId);
            projectLogger.logInfo("Successfully built image: " + imageId);
        } else {
            logger.info("Using existing image: {}", imageTag);
            projectLogger.logInfo("Using existing image: " + imageTag);
        }

        // Create and start container
        ExposedPort exposedPort = project.getType() == ProjectType.HTML ? 
                ExposedPort.tcp(80) : ExposedPort.tcp(project.getPort());
        
        Ports portBindings = new Ports();
        portBindings.bind(exposedPort, Ports.Binding.bindPort(project.getPort()));

        HostConfig hostConfig = HostConfig.newHostConfig()
                .withPortBindings(portBindings)
                .withMemory(project.getDockerOptions().getMemoryLimit() * 1024 * 1024)
                .withNanoCPUs((long) (project.getDockerOptions().getCpuLimit() * 1_000_000_000))
                .withAutoRemove(true);

        // Add volume mounts if any
        if (!project.getDockerOptions().getVolumeMounts().isEmpty()) {
            List<Bind> binds = new ArrayList<>();
            for (Map.Entry<String, String> entry : project.getDockerOptions().getVolumeMounts().entrySet()) {
                binds.add(new Bind(entry.getKey(), new Volume(entry.getValue())));
            }
            hostConfig.withBinds(binds);
        }

        // Add environment variables
        List<String> envVars = new ArrayList<>();
        for (Map.Entry<String, String> entry : project.getEnvironmentVariables().entrySet()) {
            envVars.add(entry.getKey() + "=" + entry.getValue());
        }

        CreateContainerResponse container = dockerClient.createContainerCmd(imageTag)
                .withName(sanitizeContainerName(project.getName() + "-" + project.getId().substring(0, 8)))
                .withHostConfig(hostConfig)
                .withEnv(envVars)
                .exec();

        String containerId = container.getId();
        logger.info("Created container: {}", containerId);
        projectLogger.logInfo("Created container: " + containerId);

        dockerClient.startContainerCmd(containerId).exec();
        logger.info("Started container: {}", containerId);
        projectLogger.logInfo("Started container successfully");
        projectLogger.logInfo("Container is running on port: " + project.getPort());
        projectLogger.logInfo("Access URL: http://localhost:" + project.getPort());

        project.setStatus(Project.ProjectStatus.RUNNING);
        project.setContainerId(containerId);

        // Close the logger
        projectLogger.close();

        return containerId;
    }

    private String startFullStackProject(Project project) throws Exception {
        // For full-stack, use docker-compose
        int backendPort = project.getPort();
        int frontendPort = backendPort + 1;
        
        String composeContent = DockerfileGenerator.generateDockerCompose(
                project.getPath(), frontendPort, backendPort);
        DockerfileGenerator.writeDockerCompose(project.getPath(), composeContent);
        
        // Generate individual Dockerfiles
        String backendPath = new File(project.getPath(), detectBackendDir(project.getPath())).getPath();
        String frontendPath = new File(project.getPath(), detectFrontendDir(project.getPath())).getPath();
        
        DockerfileGenerator.writeDockerfile(backendPath, 
                DockerfileGenerator.generateBackendDockerfile(backendPath));
        DockerfileGenerator.writeDockerfile(frontendPath, 
                DockerfileGenerator.generateFrontendDockerfile(frontendPath));
        
        // Use docker-compose command
        ProcessBuilder processBuilder = new ProcessBuilder(
                "docker-compose", "-f", 
                new File(project.getPath(), "docker-compose.yml").getPath(),
                "up", "-d"
        );
        processBuilder.directory(new File(project.getPath()));
        processBuilder.redirectErrorStream(true);
        
        Process process = processBuilder.start();
        BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        
        String line;
        while ((line = reader.readLine()) != null) {
            logger.info("Docker Compose: {}", line);
        }
        
        int exitCode = process.waitFor();
        
        if (exitCode == 0) {
            project.setStatus(Project.ProjectStatus.RUNNING);
            project.setContainerId("compose-" + project.getId());
            return project.getContainerId();
        } else {
            throw new RuntimeException("Docker Compose failed with exit code: " + exitCode);
        }
    }

    /**
     * Stop a running container
     * @param project the project to stop
     */
    public void stopProject(Project project) {
        if (!dockerAvailable || project.getContainerId() == null) {
            return;
        }

        try {
            logger.info("Stopping project: {}", project.getName());
            
            if (project.getContainerId().startsWith("compose-")) {
                // Stop docker-compose
                ProcessBuilder processBuilder = new ProcessBuilder(
                        "docker-compose", "-f",
                        new File(project.getPath(), "docker-compose.yml").getPath(),
                        "down"
                );
                processBuilder.directory(new File(project.getPath()));
                Process process = processBuilder.start();
                process.waitFor();
            } else {
                // Stop single container
                dockerClient.stopContainerCmd(project.getContainerId())
                        .withTimeout(10)
                        .exec();
                
                logger.info("Stopped container: {}", project.getContainerId());
            }

            project.setStatus(Project.ProjectStatus.STOPPED);
            project.setContainerId(null);

        } catch (Exception e) {
            logger.error("Failed to stop project: {}", e.getMessage(), e);
        }
    }

    /**
     * Get container status
     * @param containerId the container ID
     * @return container status or null
     */
    public String getContainerStatus(String containerId) {
        if (!dockerAvailable || containerId == null || containerId.startsWith("compose-")) {
            return null;
        }

        try {
            InspectContainerResponse container = dockerClient.inspectContainerCmd(containerId).exec();
            return container.getState().getStatus();
        } catch (Exception e) {
            logger.debug("Failed to get container status: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Stop all running containers
     */
    public void stopAllContainers(List<Project> projects) {
        for (Project project : projects) {
            if (project.getStatus() == Project.ProjectStatus.RUNNING) {
                stopProject(project);
            }
        }
    }

    /**
     * Clean up resources
     */
    public void close() {
        if (dockerClient != null) {
            try {
                dockerClient.close();
                logger.info("Docker client closed");
            } catch (IOException e) {
                logger.error("Error closing Docker client", e);
            }
        }
    }

    public boolean isDockerAvailable() {
        return dockerAvailable;
    }
    
    /**
     * Check if a Docker image exists
     * @param imageTag the image tag to check
     * @return true if the image exists
     */
    private boolean checkImageExists(String imageTag) {
        if (!dockerAvailable) {
            return false;
        }
        
        try {
            dockerClient.inspectImageCmd(imageTag).exec();
            return true;
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Delete a project's Docker image
     * @param project the project whose image should be deleted
     */
    public void deleteProjectImage(Project project) {
        if (!dockerAvailable) {
            return;
        }
        
        try {
            String imageName = sanitizeImageName(project.getName());
            String imageTag = imageName + ":" + project.getId().substring(0, 8);
            
            dockerClient.removeImageCmd(imageTag).exec();
            logger.info("Deleted image: {}", imageTag);
        } catch (Exception e) {
            logger.debug("Failed to delete image (may not exist): {}", e.getMessage());
        }
    }

    private String sanitizeImageName(String name) {
        return name.toLowerCase().replaceAll("[^a-z0-9-_.]", "-");
    }

    private String sanitizeContainerName(String name) {
        return name.replaceAll("[^a-zA-Z0-9-_.]", "-");
    }

    private String detectBackendDir(String projectPath) {
        if (new File(projectPath, "backend").exists()) return "backend";
        if (new File(projectPath, "server").exists()) return "server";
        if (new File(projectPath, "api").exists()) return "api";
        return "backend";
    }

    private String detectFrontendDir(String projectPath) {
        if (new File(projectPath, "frontend").exists()) return "frontend";
        if (new File(projectPath, "client").exists()) return "client";
        if (new File(projectPath, "web").exists()) return "web";
        return "frontend";
    }
}

