//DELETE LATER
//To run main: run these commands in terminal
//First cd to FileServer folder with cd FileServer
//mvn -DskipTests package
//mvn exec:java -Dexec.mainClass="ca.concordia.Main"

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.concurrent.locks.ReentrantLock;

public class FileSystemManager {

    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null; // initially null, set in constructor
    private RandomAccessFile disk; // initialized in constructor
    private final ReentrantLock globalLock = new ReentrantLock();

    private static final int BLOCK_SIZE = 128; // Example block size

    private FEntry[] inodeTable; // Array of inodes
    private boolean[] freeBlockList; // Bitmap for free blocks

    public FileSystemManager(String filename, int totalSize) {
        // Initialize the file system manager with a file
        if (instance == null) {
            try {
                // Make sure parent folder exists
                File f = new File(filename);
                File parent = f.getParentFile();
                if (parent != null && !parent.exists()) {
                    parent.mkdirs();
                }

                // Open or create the backing file
                this.disk = new RandomAccessFile(f, "rw");

                // Initialize inode table and free block list
                this.inodeTable = new FEntry[MAXFILES];
                this.freeBlockList = new boolean[MAXBLOCKS];
                for (int i = 0; i < MAXBLOCKS; i++) {
                    freeBlockList[i] = true; // all blocks free initially
                }

                // Mark singleton instance
                instance = this;
            } catch (Exception e) {
                throw new RuntimeException("Failed to initialize FileSystemManager: " + e.getMessage(), e);
            }
        } else {
            throw new IllegalStateException("FileSystemManager is already initialized.");
        }

    }

    public void createFile(String fileName) throws Exception {
        // TODO
        throw new UnsupportedOperationException("Method not implemented yet.");
    }


    // TODO: Add readFile, writeFile and other required methods,
}
