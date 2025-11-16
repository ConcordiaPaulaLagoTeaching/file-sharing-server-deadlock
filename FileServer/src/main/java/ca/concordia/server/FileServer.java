package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FileServer {

    private final FileSystemManager fsManager;
    private final int port;
    private final ExecutorService executor;

    public FileServer(int port, String fileSystemName, int totalSize) {
        // Use provided totalSize (no hardcoding)
        this.fsManager = new FileSystemManager(fileSystemName, totalSize);
        this.port = port;
        // Thread pool: grows on demand, reuses idle workers
        this.executor = Executors.newCachedThreadPool();
    }

    public void start() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> executor.shutdownNow()));
        try (ServerSocket serverSocket = new ServerSocket(this.port)) {
            System.out.println("Server started. Listening on port " + this.port + "...");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                // Submit client handling to the pool (this is the thread pool usage)
                executor.submit(() -> handleClient(clientSocket));
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        } finally {
            executor.shutdown();
        }
    }

    // All command handling remains here
    void handleClient(Socket clientSocket) {
        System.out.println("Thread [" + Thread.currentThread().getName() + "] handling client: " + clientSocket);
        System.out.println("Handling client: " + clientSocket);
        try (
                BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter writer = new PrintWriter(clientSocket.getOutputStream(), true)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                System.out.println("Received from client: " + line);
                String[] parts = line.split(" ");
                if (parts.length == 0) {
                    writer.println("ERROR: Empty command.");
                    writer.flush();
                    continue;
                }
                String command = parts[0].toUpperCase();

                switch (command) {
                    case "CREATE":
                        if (parts.length < 2) {
                            writer.println("ERROR: Missing filename.");
                            writer.flush();
                            break;
                        }
                        try {
                            fsManager.createFile(parts[1]);
                            writer.println("SUCCESS: File '" + parts[1] + "' created.");
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        writer.flush();
                        break;

                    case "LIST":
                        String[][] files = fsManager.listFiles();
                        if (files.length == 0) {
                            writer.println("No files found.");
                        } else {
                            StringBuilder sb = new StringBuilder();
                            for (int i = 0; i < files.length; i++) {
                                if (i > 0) sb.append(" | ");
                                String[] file = files[i];
                                sb.append("File Name: ").append(file[0])
                                        .append(", File Size: ").append(file[1])
                                        .append(", First Block: ").append(file[2]);
                            }
                            writer.println(sb);
                        }
                        writer.flush();
                        break;

                    case "DELETE":
                        parts = line.split("\\s+", 2);
                        if (parts.length < 2 || parts[1].trim().isEmpty()) {
                            writer.println("ERROR: Missing filename");
                            writer.flush();
                            break;
                        }
                        try {
                            fsManager.deleteFile(parts[1].trim());
                            writer.println("File deleted");
                        } catch (Exception e) {
                            writer.println("ERROR " + e.getMessage());
                        }
                        writer.flush();
                        break;

                    case "WRITE":
                        if (parts.length < 2) {
                            writer.println("ERROR: Missing filename or content.");
                            writer.flush();
                            break;
                        }
                        try {
                            String filename = parts[1];
                            int idx = line.indexOf(filename);
                            String contentStr = (idx >= 0) ? line.substring(idx + filename.length()).trim() : "";
                            if (contentStr.isEmpty()) {
                                writer.println("ERROR: No content provided to write.");
                                writer.flush();
                                break;
                            }

                            byte[] data = contentStr.getBytes(StandardCharsets.UTF_8);

                            try {
                                fsManager.writeFile(filename, data);
                                writer.println("SUCCESS: " + filename + " is now " + data.length + " bytes.");
                            } catch (Exception e) {
                                writer.println("ERROR: " + e.getMessage());
                            }

                        } finally {
                            writer.flush();
                        }
                        break;

                    case "READ":
                        if (parts.length < 2) {
                            writer.println("ERROR: Missing filename.");
                            writer.flush();
                            break;
                        }
                        try {
                            String filename = parts[1];
                            byte[] data = fsManager.readFile(filename);
                            String content = new String(data, StandardCharsets.UTF_8);
                            writer.println("SUCCESS: READ " + data.length + " bytes. CONTENT: " + content);
                        } catch (Exception e) {
                            writer.println("ERROR: " + e.getMessage());
                        }
                        writer.flush();
                        break;

                    case "QUIT":
                        writer.println("SUCCESS: Disconnecting.");
                        writer.flush();
                        return;

                    default:
                        writer.println("ERROR: Unknown command.");
                        writer.flush();
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Client error: " + e.getMessage());
        } finally {
            try { clientSocket.close(); } catch (Exception ignore) {}
        }
    }
}

