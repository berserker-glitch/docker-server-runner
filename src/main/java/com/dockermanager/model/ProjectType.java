package com.dockermanager.model;

public enum ProjectType {
    HTML("HTML/CSS/JS", "#FF6B6B"),
    NODE("Node.js", "#68A063"),
    REACT("React", "#61DAFB"),
    FULLSTACK("Full-stack", "#9B59B6"),
    UNKNOWN("Unknown", "#95A5A6");

    private final String displayName;
    private final String color;

    ProjectType(String displayName, String color) {
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

