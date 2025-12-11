package com.dockermanager.model;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class Project {
    private String id;
    private String name;
    private String path;
    private ProjectType type;
    private int port;
    private Map<String, String> environmentVariables;
    private DockerOptions dockerOptions;
    private String containerId;
    private ProjectStatus status;

    public Project() {
        this.id = UUID.randomUUID().toString();
        this.environmentVariables = new HashMap<>();
        this.dockerOptions = new DockerOptions();
        this.status = ProjectStatus.STOPPED;
    }

    public Project(String name, String path, ProjectType type) {
        this();
        this.name = name;
        this.path = path;
        this.type = type;
    }

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public ProjectType getType() {
        return type;
    }

    public void setType(ProjectType type) {
        this.type = type;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public Map<String, String> getEnvironmentVariables() {
        return environmentVariables;
    }

    public void setEnvironmentVariables(Map<String, String> environmentVariables) {
        this.environmentVariables = environmentVariables;
    }

    public DockerOptions getDockerOptions() {
        return dockerOptions;
    }

    public void setDockerOptions(DockerOptions dockerOptions) {
        this.dockerOptions = dockerOptions;
    }

    public String getContainerId() {
        return containerId;
    }

    public void setContainerId(String containerId) {
        this.containerId = containerId;
    }

    public ProjectStatus getStatus() {
        return status;
    }

    public void setStatus(ProjectStatus status) {
        this.status = status;
    }

    public enum ProjectStatus {
        STOPPED("Stopped", "#95A5A6"),
        STARTING("Starting", "#F39C12"),
        RUNNING("Running", "#2ECC71"),
        ERROR("Error", "#E74C3C");

        private final String displayName;
        private final String color;

        ProjectStatus(String displayName, String color) {
            this.displayName = displayName;
            this.color = color;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getColor() {
            return color;
        }
    }

    public static class DockerOptions {
        private long memoryLimit; // in MB
        private double cpuLimit; // number of CPUs
        private Map<String, String> volumeMounts;

        public DockerOptions() {
            this.memoryLimit = 512; // default 512MB
            this.cpuLimit = 1.0; // default 1 CPU
            this.volumeMounts = new HashMap<>();
        }

        public long getMemoryLimit() {
            return memoryLimit;
        }

        public void setMemoryLimit(long memoryLimit) {
            this.memoryLimit = memoryLimit;
        }

        public double getCpuLimit() {
            return cpuLimit;
        }

        public void setCpuLimit(double cpuLimit) {
            this.cpuLimit = cpuLimit;
        }

        public Map<String, String> getVolumeMounts() {
            return volumeMounts;
        }

        public void setVolumeMounts(Map<String, String> volumeMounts) {
            this.volumeMounts = volumeMounts;
        }
    }
}

