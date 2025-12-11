package com.dockermanager.util;

import com.dockermanager.model.ProjectType;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;

public class DockerfileGenerator {
    private static final Logger logger = LoggerFactory.getLogger(DockerfileGenerator.class);

    public static String generateDockerfile(String projectPath, ProjectType type, int port) {
        return switch (type) {
            case HTML -> generateHtmlDockerfile(projectPath, port);
            case NODE -> generateNodeDockerfile(projectPath, port);
            case REACT -> generateReactDockerfile(projectPath, port);
            case FULLSTACK -> generateFullStackDockerfile(projectPath, port);
            default -> null;
        };
    }

    private static String generateHtmlDockerfile(String projectPath, int port) {
        return """
                FROM nginx:alpine
                
                # Copy all files to nginx html directory
                COPY . /usr/share/nginx/html
                
                # Copy custom nginx config if exists
                COPY nginx.conf /etc/nginx/nginx.conf 2>/dev/null || true
                
                EXPOSE 80
                
                CMD ["nginx", "-g", "daemon off;"]
                """;
    }

    private static String generateNodeDockerfile(String projectPath, int port) {
        String startCommand = detectNodeStartCommand(projectPath);
        String nodeVersion = detectNodeVersion(projectPath);
        
        return String.format("""
                FROM node:%s-alpine
                
                WORKDIR /app
                
                # Copy package files
                COPY package*.json ./
                
                # Install dependencies
                RUN npm install
                
                # Copy application files
                COPY . .
                
                # Expose the application port
                EXPOSE %d
                
                # Start the application
                CMD [%s]
                """, nodeVersion, port, startCommand);
    }

    private static String generateReactDockerfile(String projectPath, int port) {
        String nodeVersion = detectNodeVersion(projectPath);
        boolean isNextJs = isNextJsProject(projectPath);
        
        if (isNextJs) {
            // Next.js requires a running Node server, not static nginx
            return String.format("""
                    FROM node:%s-alpine
                    
                    WORKDIR /app
                    
                    # Copy package files
                    COPY package*.json ./
                    
                    # Install dependencies
                    RUN npm install
                    
                    # Copy source files
                    COPY . .
                    
                    # Build the Next.js application
                    RUN npm run build
                    
                    # Expose port
                    EXPOSE 3000
                    
                    # Start Next.js in production mode
                    CMD ["npm", "start"]
                    """, nodeVersion);
        } else {
            // Standard React (CRA, Vite, etc.) - use static nginx
            String buildCommand = detectReactBuildCommand(projectPath);
            
            return String.format("""
                    # Build stage
                    FROM node:%s-alpine AS build
                    
                    WORKDIR /app
                    
                    # Copy package files
                    COPY package*.json ./
                    
                    # Install dependencies
                    RUN npm install
                    
                    # Copy source files
                    COPY . .
                    
                    # Build the application
                    RUN %s
                    
                    # Production stage
                    FROM nginx:alpine
                    
                    # Copy built files from build stage
                    COPY --from=build /app/build /usr/share/nginx/html 2>/dev/null || true
                    COPY --from=build /app/dist /usr/share/nginx/html 2>/dev/null || true
                    
                    # Copy custom nginx config
                    RUN echo 'server { listen 80; location / { root /usr/share/nginx/html; index index.html; try_files $uri $uri/ /index.html; } }' > /etc/nginx/conf.d/default.conf
                    
                    EXPOSE 80
                    
                    CMD ["nginx", "-g", "daemon off;"]
                    """, nodeVersion, buildCommand);
        }
    }
    
    private static boolean isNextJsProject(String projectPath) {
        try {
            // Check for next.config.js or next.config.ts
            if (FileUtils.fileExists(projectPath, "next.config.js") ||
                FileUtils.fileExists(projectPath, "next.config.ts") ||
                FileUtils.fileExists(projectPath, "next.config.mjs")) {
                return true;
            }
            
            // Check package.json for Next.js dependency
            if (FileUtils.fileExists(projectPath, "package.json")) {
                String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
                JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();
                
                if (packageJson.has("dependencies")) {
                    JsonObject deps = packageJson.getAsJsonObject("dependencies");
                    if (deps.has("next")) {
                        return true;
                    }
                }
                
                if (packageJson.has("devDependencies")) {
                    JsonObject devDeps = packageJson.getAsJsonObject("devDependencies");
                    if (devDeps.has("next")) {
                        return true;
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error checking for Next.js: {}", e.getMessage());
        }
        
        return false;
    }

    private static String generateFullStackDockerfile(String projectPath, int port) {
        // For full-stack, we generate a docker-compose.yml instead
        return null; // Will be handled by generateDockerCompose
    }

    public static String generateDockerCompose(String projectPath, int frontendPort, int backendPort) {
        String backendPath = detectBackendPath(projectPath);
        String frontendPath = detectFrontendPath(projectPath);
        
        return String.format("""
                version: '3.8'
                
                services:
                  backend:
                    build:
                      context: ./%s
                      dockerfile: Dockerfile
                    ports:
                      - "%d:%d"
                    environment:
                      - NODE_ENV=development
                      - PORT=%d
                    volumes:
                      - ./%s:/app
                      - /app/node_modules
                    networks:
                      - app-network
                
                  frontend:
                    build:
                      context: ./%s
                      dockerfile: Dockerfile
                    ports:
                      - "%d:%d"
                    environment:
                      - REACT_APP_API_URL=http://localhost:%d
                    volumes:
                      - ./%s:/app
                      - /app/node_modules
                    networks:
                      - app-network
                    depends_on:
                      - backend
                
                networks:
                  app-network:
                    driver: bridge
                """, 
                backendPath, backendPort, backendPort, backendPort, backendPath,
                frontendPath, frontendPort, frontendPort, backendPort, frontendPath);
    }

    public static String generateBackendDockerfile(String backendPath) {
        String startCommand = detectNodeStartCommand(backendPath);
        String nodeVersion = detectNodeVersion(backendPath);
        
        return String.format("""
                FROM node:%s-alpine
                
                WORKDIR /app
                
                COPY package*.json ./
                RUN npm install
                
                COPY . .
                
                EXPOSE 3000
                
                CMD [%s]
                """, nodeVersion, startCommand);
    }

    public static String generateFrontendDockerfile(String frontendPath) {
        String nodeVersion = detectNodeVersion(frontendPath);
        
        return String.format("""
                FROM node:%s-alpine
                
                WORKDIR /app
                
                COPY package*.json ./
                RUN npm install
                
                COPY . .
                
                EXPOSE 3000
                
                CMD ["npm", "start"]
                """, nodeVersion);
    }

    private static String detectNodeStartCommand(String projectPath) {
        try {
            if (FileUtils.fileExists(projectPath, "package.json")) {
                String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
                JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();
                
                if (packageJson.has("scripts")) {
                    JsonObject scripts = packageJson.getAsJsonObject("scripts");
                    
                    // Priority order for start commands
                    if (scripts.has("start")) {
                        return "\"npm\", \"start\"";
                    } else if (scripts.has("dev")) {
                        return "\"npm\", \"run\", \"dev\"";
                    } else if (scripts.has("serve")) {
                        return "\"npm\", \"run\", \"serve\"";
                    }
                }
                
                // Check for main field
                if (packageJson.has("main")) {
                    String mainFile = packageJson.get("main").getAsString();
                    return String.format("\"node\", \"%s\"", mainFile);
                }
            }
            
            // Check for common entry files
            if (FileUtils.fileExists(projectPath, "index.js")) {
                return "\"node\", \"index.js\"";
            } else if (FileUtils.fileExists(projectPath, "server.js")) {
                return "\"node\", \"server.js\"";
            } else if (FileUtils.fileExists(projectPath, "app.js")) {
                return "\"node\", \"app.js\"";
            }
            
        } catch (Exception e) {
            logger.debug("Error detecting start command: {}", e.getMessage());
        }
        
        return "\"npm\", \"start\"";
    }

    private static String detectReactBuildCommand(String projectPath) {
        try {
            if (FileUtils.fileExists(projectPath, "package.json")) {
                String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
                JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();
                
                if (packageJson.has("scripts")) {
                    JsonObject scripts = packageJson.getAsJsonObject("scripts");
                    
                    if (scripts.has("build")) {
                        return "npm run build";
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error detecting build command: {}", e.getMessage());
        }
        
        return "npm run build";
    }

    private static String detectNodeVersion(String projectPath) {
        try {
            if (FileUtils.fileExists(projectPath, "package.json")) {
                String content = FileUtils.readFileContent(new File(projectPath, "package.json").getPath());
                JsonObject packageJson = JsonParser.parseString(content).getAsJsonObject();
                
                // Check for Next.js version - newer versions require Node 20+
                if (packageJson.has("dependencies")) {
                    JsonObject deps = packageJson.getAsJsonObject("dependencies");
                    if (deps.has("next")) {
                        String nextVersion = deps.get("next").getAsString();
                        // Extract major version
                        String versionNum = nextVersion.replaceAll("[^0-9.]", "");
                        if (versionNum.length() > 0) {
                            int majorVersion = Integer.parseInt(versionNum.split("\\.")[0]);
                            // Next.js 14+ requires Node 20+
                            if (majorVersion >= 14) {
                                return "20";
                            }
                        }
                    }
                }
                
                // Check engines field in package.json
                if (packageJson.has("engines")) {
                    JsonObject engines = packageJson.getAsJsonObject("engines");
                    if (engines.has("node")) {
                        String nodeVersion = engines.get("node").getAsString();
                        // Extract version number (e.g., ">=20.0.0" -> "20", ">=18.0.0" -> "18")
                        nodeVersion = nodeVersion.replaceAll("[^0-9.]", "");
                        if (nodeVersion.contains(".")) {
                            nodeVersion = nodeVersion.substring(0, nodeVersion.indexOf("."));
                        }
                        if (!nodeVersion.isEmpty()) {
                            int version = Integer.parseInt(nodeVersion);
                            // Use at least the requested version
                            return String.valueOf(version);
                        }
                    }
                }
            }
        } catch (Exception e) {
            logger.debug("Error detecting Node version: {}", e.getMessage());
        }
        
        return "20"; // Default to Node 20 LTS (more compatible with modern frameworks)
    }

    private static String detectBackendPath(String projectPath) {
        if (FileUtils.directoryExists(projectPath, "backend")) {
            return "backend";
        } else if (FileUtils.directoryExists(projectPath, "server")) {
            return "server";
        } else if (FileUtils.directoryExists(projectPath, "api")) {
            return "api";
        }
        return "backend";
    }

    private static String detectFrontendPath(String projectPath) {
        if (FileUtils.directoryExists(projectPath, "frontend")) {
            return "frontend";
        } else if (FileUtils.directoryExists(projectPath, "client")) {
            return "client";
        } else if (FileUtils.directoryExists(projectPath, "web")) {
            return "web";
        }
        return "frontend";
    }

    public static void writeDockerfile(String projectPath, String content) throws IOException {
        String dockerfilePath = new File(projectPath, "Dockerfile").getPath();
        FileUtils.writeFileContent(dockerfilePath, content);
        logger.info("Generated Dockerfile at: {}", dockerfilePath);
    }

    public static void writeDockerCompose(String projectPath, String content) throws IOException {
        String composePath = new File(projectPath, "docker-compose.yml").getPath();
        FileUtils.writeFileContent(composePath, content);
        logger.info("Generated docker-compose.yml at: {}", composePath);
    }
}

