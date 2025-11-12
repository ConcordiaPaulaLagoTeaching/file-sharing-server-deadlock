package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

public class FileServer {

    private FileSystemManager fsManager;
    private int port;
    public FileServer(int port, String fileSystemName, int totalSize){
        // Initialize the FileSystemManager
        FileSystemManager fsManager = new FileSystemManager(fileSystemName,
                10*128 );
        this.fsManager = fsManager;
        this.port = port;
    }

    public void start(){
        try (ServerSocket serverSocket = new ServerSocket(12345)) {
            System.out.println("Server started. Listening on port 12345...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
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
                                    e.printStackTrace();
                                }
                                writer.flush();
                                break;
                            case "READ":
                                return;
                            case "DELETE":
                                break;
                            case "WRITE":
                                if (parts.length < 2) {
                                    writer.println("ERROR: Missing filename or content.");
                                    writer.flush();
                                    break;
                                }
                                try {
                                    // filename is parts[1]; content is the rest of the original line (preserves spaces)
                                    String filename = parts[1];
                                    int idx = line.indexOf(filename);
                                    String contentStr = "";
                                    if (idx >= 0) {
                                        contentStr = line.substring(idx + filename.length()).trim();
                                    }

                                    if (contentStr.isEmpty()) {
                                        writer.println("ERROR: No content provided to write.");
                                        writer.flush();
                                        break;
                                    }

                                    byte[] data;
                                    // support optional BASE64: prefix for binary data
                                    if (contentStr.startsWith("BASE64:")) {
                                        String b64 = contentStr.substring("BASE64:".length());
                                        data = java.util.Base64.getDecoder().decode(b64);
                                    } else {
                                        data = contentStr.getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                    }

                                    try {
                                        fsManager.writeFile(filename, data);
                                        writer.println("SUCCESS: Wrote " + data.length + " bytes to '" + filename + "'.");
                                    } catch (Exception e) {
                                        writer.println("ERROR: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                } finally {
                                    writer.flush();
                                }
                                break;
                            case "LIST":
                                String[][] files = fsManager.listFiles();
                                if (files.length == 0) {
                                    writer.println("No files found.");
                                    writer.flush();
                                } else {
                                    StringBuilder sb = new StringBuilder();
                                    for (int i = 0; i < files.length; i++) {
                                        if (i > 0) sb.append(" | ");
                                        String[] file = files[i];
                                        sb.append("File Name: ").append(file[0])
                                                .append(", File Size: ").append(file[1])
                                                .append(", First Block: ").append(file[2]);
                                    }
                                    writer.println(sb.toString());
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
                    e.printStackTrace();
                } finally {
                    try {
                        clientSocket.close();
                    } catch (Exception e) {
                        // Ignore
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Could not start server on port " + port);
        }
    }

}
