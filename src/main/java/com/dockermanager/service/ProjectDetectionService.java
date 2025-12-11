package com.dockermanager.service;

import com.dockermanager.model.ProjectType;
import com.dockermanager.util.FileUtils;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class ProjectDetectionService {
    private static final Logger logger = LoggerFactory.getLogger(ProjectDetectionService.class);

    public ProjectType detectProjectType(String projectPath) {
        if (!FileUtils.isValidDirectory(projectPath)) {
            logger.warn("Invalid project path: {}", projectPath);
            return ProjectType.UNKNOWN;
        }

        try {
            // Priority 1: Check for full-stack structure
            if (isFullStackProject(projectPath)) {
                logger.info("Detected full-stack project at: {}", projectPath);
                return ProjectType.FULLSTACK;
            }

            // Priority 2: Check for React project
            if (isReactProject(projectPath)) {
                logger.info("Detected React project at: {}", projectPath);
                return ProjectType.REACT;
            }

            // Priority 3: Check for Node.js project
            if (isNodeProject(projectPath)) {
                logger.info("Detected Node.js project at: {}", projectPath);
                return ProjectType.NODE;
            }

            // Priority 4: Check for static HTML/CSS/JS
            if (isStaticHtmlProject(projectPath)) {
                logger.info("Detected HTML/CSS/JS project at: {}", projectPath);
                return ProjectType.HTML;
            }

            logger.warn("Unable to detect project type for: {}", projectPath);
            return ProjectType.UNKNOWN;

        } catch (Exception e) {
            logger.error("Error detecting project type: {}", e.getMessage(), e);
            return ProjectType.UNKNOWN;
        }
    }

    private boolean isFullStackProject(String projectPath) {
        // Check for separate backend and frontend directories
        boolean hasBackend = FileUtils.directoryExists(projectPath, "backend") ||
                            FileUtils.directoryExists(projectPath, "server");
        boolean hasFrontend = FileUtils.directoryExists(projectPath, "frontend") ||
                             FileUtils.directoryExists(projectPath, "client");

        if (hasBackend && hasFrontend) {
            return true;
        }

        // Check for monorepo with both server and client code
        boolean hasPackageJson = FileUtils.fileExists(projectPath, "package.json");
        if (hasPackageJson) {
            try {
                String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
                JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();

                // Check for workspaces (monorepo pattern)
                if (packageJson.has("workspaces")) {
                    return true;
                }

                // Check if scripts suggest both backend and frontend
                if (packageJson.has("scripts")) {
                    JsonObject scripts = packageJson.getAsJsonObject("scripts");
                    boolean hasServerScript = scripts.has("server") || scripts.has("start:server") || 
                                             scripts.has("backend") || scripts.has("start:backend");
                    boolean hasClientScript = scripts.has("client") || scripts.has("start:client") || 
                                             scripts.has("frontend") || scripts.has("start:frontend");
                    if (hasServerScript && hasClientScript) {
                        return true;
                    }
                }
            } catch (Exception e) {
                logger.debug("Error reading package.json for full-stack detection: {}", e.getMessage());
            }
        }

        return false;
    }

    private boolean isReactProject(String projectPath) {
        if (!FileUtils.fileExists(projectPath, "package.json")) {
            return false;
        }

        try {
            // Check for React component files
            if (FileUtils.directoryExists(projectPath, "src")) {
                String srcPath = new File(projectPath, "src").getPath();
                if (FileUtils.fileExists(srcPath, "App.jsx") || 
                    FileUtils.fileExists(srcPath, "App.tsx") ||
                    FileUtils.fileExists(srcPath, "App.js")) {
                    
                    // Read package.json to confirm React dependency
                    String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
                    JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();
                    
                    if (hasReactDependency(packageJson)) {
                        return true;
                    }
                }
            }

            // Check package.json for React dependencies
            String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
            JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();

            if (hasReactDependency(packageJson)) {
                return true;
            }

            // Check for React-specific config files
            if (FileUtils.fileExists(projectPath, "vite.config.js") ||
                FileUtils.fileExists(projectPath, "vite.config.ts") ||
                FileUtils.fileExists(projectPath, ".next") ||
                FileUtils.fileExists(projectPath, "next.config.js")) {
                return true;
            }

        } catch (Exception e) {
            logger.debug("Error checking for React project: {}", e.getMessage());
        }

        return false;
    }

    private boolean hasReactDependency(JsonObject packageJson) {
        if (packageJson.has("dependencies")) {
            JsonObject deps = packageJson.getAsJsonObject("dependencies");
            if (deps.has("react") || deps.has("react-dom")) {
                return true;
            }
        }
        if (packageJson.has("devDependencies")) {
            JsonObject devDeps = packageJson.getAsJsonObject("devDependencies");
            if (devDeps.has("react") || devDeps.has("react-dom")) {
                return true;
            }
        }
        return false;
    }

    private boolean isNodeProject(String projectPath) {
        if (!FileUtils.fileExists(projectPath, "package.json")) {
            return false;
        }

        try {
            String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
            JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();

            // Check for common Node.js patterns
            if (packageJson.has("scripts")) {
                JsonObject scripts = packageJson.getAsJsonObject("scripts");
                // Look for server-related scripts
                if (scripts.has("start") || scripts.has("dev") || 
                    scripts.has("serve") || scripts.has("server")) {
                    return true;
                }
            }

            // Check for type: module or main field
            if (packageJson.has("type") || packageJson.has("main")) {
                return true;
            }

            // Check for common Node.js dependencies
            if (packageJson.has("dependencies")) {
                JsonObject deps = packageJson.getAsJsonObject("dependencies");
                if (deps.has("express") || deps.has("fastify") || 
                    deps.has("koa") || deps.has("nestjs")) {
                    return true;
                }
            }

            // Check for common Node.js files
            if (FileUtils.fileExists(projectPath, "server.js") ||
                FileUtils.fileExists(projectPath, "app.js") ||
                FileUtils.fileExists(projectPath, "index.js")) {
                return true;
            }

        } catch (Exception e) {
            logger.debug("Error checking for Node.js project: {}", e.getMessage());
        }

        return false;
    }

    private boolean isStaticHtmlProject(String projectPath) {
        // Check for index.html without package.json
        boolean hasIndexHtml = FileUtils.fileExists(projectPath, "index.html");
        boolean hasPackageJson = FileUtils.fileExists(projectPath, "package.json");

        if (hasIndexHtml && !hasPackageJson) {
            return true;
        }

        // Check for multiple HTML files suggesting a static site
        if (hasIndexHtml) {
            File dir = new File(projectPath);
            File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".html"));
            if (files != null && files.length > 0) {
                return true;
            }
        }

        return false;
    }

    public int detectDefaultPort(String projectPath, ProjectType type) {
        if (type == ProjectType.HTML) {
            return 80;
        }

        if (!FileUtils.fileExists(projectPath, "package.json")) {
            return 3000; // default
        }

        try {
            String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
            JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();

            // Check for port in scripts
            if (packageJson.has("scripts")) {
                JsonObject scripts = packageJson.getAsJsonObject("scripts");
                for (String key : scripts.keySet()) {
                    String script = scripts.get(key).getAsString();
                    // Look for PORT=xxxx or --port xxxx
                    if (script.contains("PORT=")) {
                        String portStr = script.substring(script.indexOf("PORT=") + 5);
                        portStr = portStr.split("\\s")[0];
                        try {
                            return Integer.parseInt(portStr);
                        } catch (NumberFormatException e) {
                            // continue searching
                        }
                    }
                    if (script.contains("--port")) {
                        String[] parts = script.split("--port\\s+");
                        if (parts.length > 1) {
                            String portStr = parts[1].split("\\s")[0];
                            try {
                                return Integer.parseInt(portStr);
                            } catch (NumberFormatException e) {
                                // continue searching
                            }
                        }
                    }
                }
            }

            // Check for config field
            if (packageJson.has("config")) {
                JsonObject config = packageJson.getAsJsonObject("config");
                if (config.has("port")) {
                    return config.get("port").getAsInt();
                }
            }

        } catch (Exception e) {
            logger.debug("Error detecting default port: {}", e.getMessage());
        }

        // Return defaults based on type
        return switch (type) {
            case REACT -> 3000;
            case NODE -> 3000;
            case FULLSTACK -> 3000;
            default -> 8080;
        };
    }
}

