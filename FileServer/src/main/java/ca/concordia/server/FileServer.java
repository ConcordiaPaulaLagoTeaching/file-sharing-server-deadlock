package ca.concordia.server;
import ca.concordia.filesystem.FileSystemManager;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;

import java.nio.charset.StandardCharsets;


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

                            case "DELETE":
                                parts = line.split("\\s+", 2);
                                if (parts.length < 2 || parts[1].trim().isEmpty()) {
                                    writer.println("ERROR: Missing filename");
                                    break;
                                }
                                String filename1 = parts[1].trim();
                                try {
                                    fsManager.deleteFile(filename1);
                                    writer.println("File deleted");
                                } catch (Exception e) {
                                    writer.println("ERROR " + e.getMessage());
                                }
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
                                    data = contentStr.getBytes(StandardCharsets.UTF_8);
                                    final byte[] existing;

                                    try{
                                        existing = fsManager.readFile(filename);
                                    } catch (Exception e){
                                        writer.println("ERROR: File not found.");
                                        writer.flush();
                                        break;
                                    }

                                    byte[] existingSafe;
                                    if (existing == null){
                                        existingSafe = new byte[0];
                                    } else {
                                        existingSafe = existing;
                                    }
                                    byte[] combined = new byte[existingSafe.length + data.length];
                                    System.arraycopy(existingSafe, 0, combined, 0, existingSafe.length);
                                    System.arraycopy(data, 0, combined, existingSafe.length, data.length);

                                    try {
                                        fsManager.writeFile(filename, combined);
                                        writer.println("SUCCESS: " + filename + " is now " + combined.length + " bytes.");
                                    } catch (Exception e) {
                                        writer.println("ERROR: " + e.getMessage());
                                        e.printStackTrace();
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
                                    e.printStackTrace();
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
