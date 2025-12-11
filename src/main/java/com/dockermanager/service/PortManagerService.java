package com.dockermanager.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.HashSet;
import java.util.Set;

public class PortManagerService {
    private static final Logger logger = LoggerFactory.getLogger(PortManagerService.class);
    private static final int PORT_RANGE_START = 3000;
    private static final int PORT_RANGE_END = 9000;
    
    private final Set<Integer> assignedPorts;

    public PortManagerService() {
        this.assignedPorts = new HashSet<>();
    }

    /**
     * Find an available port starting from the default port range
     * @return an available port number
     */
    public int findAvailablePort() {
        return findAvailablePort(PORT_RANGE_START);
    }

    /**
     * Find an available port starting from the specified port
     * @param preferredPort the preferred starting port
     * @return an available port number
     */
    public int findAvailablePort(int preferredPort) {
        // First try the preferred port
        if (isPortAvailable(preferredPort) && !assignedPorts.contains(preferredPort)) {
            assignedPorts.add(preferredPort);
            logger.info("Assigned preferred port: {}", preferredPort);
            return preferredPort;
        }

        // Search for an available port in the range
        for (int port = PORT_RANGE_START; port <= PORT_RANGE_END; port++) {
            if (isPortAvailable(port) && !assignedPorts.contains(port)) {
                assignedPorts.add(port);
                logger.info("Assigned port: {}", port);
                return port;
            }
        }

        // If no port found in range, try a random available port
        try (ServerSocket socket = new ServerSocket(0)) {
            int port = socket.getLocalPort();
            assignedPorts.add(port);
            logger.info("Assigned random port: {}", port);
            return port;
        } catch (IOException e) {
            logger.error("Failed to find any available port", e);
            throw new RuntimeException("No available ports found", e);
        }
    }

    /**
     * Check if a specific port is available
     * @param port the port to check
     * @return true if the port is available
     */
    public boolean isPortAvailable(int port) {
        if (port < 1 || port > 65535) {
            return false;
        }

        try (ServerSocket socket = new ServerSocket(port)) {
            socket.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    /**
     * Release a port back to the available pool
     * @param port the port to release
     */
    public void releasePort(int port) {
        if (assignedPorts.remove(port)) {
            logger.info("Released port: {}", port);
        }
    }

    /**
     * Reserve a specific port
     * @param port the port to reserve
     * @return true if the port was successfully reserved
     */
    public boolean reservePort(int port) {
        if (!isPortAvailable(port)) {
            logger.warn("Port {} is not available", port);
            return false;
        }

        if (assignedPorts.contains(port)) {
            logger.warn("Port {} is already assigned", port);
            return false;
        }

        assignedPorts.add(port);
        logger.info("Reserved port: {}", port);
        return true;
    }

    /**
     * Update port assignment (release old, assign new)
     * @param oldPort the old port to release
     * @param newPort the new port to assign
     * @return true if successful
     */
    public boolean updatePort(int oldPort, int newPort) {
        if (oldPort == newPort) {
            return true;
        }

        if (!isPortAvailable(newPort)) {
            logger.warn("New port {} is not available", newPort);
            return false;
        }

        releasePort(oldPort);
        return reservePort(newPort);
    }

    /**
     * Get all currently assigned ports
     * @return set of assigned ports
     */
    public Set<Integer> getAssignedPorts() {
        return new HashSet<>(assignedPorts);
    }

    /**
     * Clear all port assignments
     */
    public void clearAllPorts() {
        assignedPorts.clear();
        logger.info("Cleared all port assignments");
    }
}

