//DELETE LATER
//To run main: run these commands in terminal
//First cd to FileServer folder with cd FileServer
//mvn -DskipTests package
//mvn exec:java -Dexec.mainClass="ca.concordia.Main"

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.File;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Objects;
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
                // Make sure parent folder existts
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

        if (fileName == null || fileName.isEmpty()) {
            throw new IllegalArgumentException("File name cannot be empty.");
        }

        globalLock.lock();
        try {
            for (FEntry entry : inodeTable) {
                if (entry != null && entry.getFilename().equals(fileName)) {
                    throw new Exception("File with that name already exists.");
                }
            }


        int freeIndex = -1;
        for (int i=0; i< inodeTable.length; i++) {
            if (inodeTable[i] == null) {
                freeIndex = i;
                break;
            }
        }

        if (freeIndex == -1) {
            throw new Exception("File system full. Maximum number of " + MAXFILES + " reached. Delete a file before creating a new one.");
        }

        int blockIndex = -1;
        for (int i=0; i< freeBlockList.length; i++) {
            if (freeBlockList[i]) {
                blockIndex = i;
                freeBlockList[i] = false;
                break;
            }
        }

        if (blockIndex == -1) {
            throw new Exception("No free space available to create new file. Delete some files to free up space.");
        }

        inodeTable[freeIndex] = new FEntry(fileName, (short) 0, (short) blockIndex);

        metaData();
        } finally {
            globalLock.unlock();
        }

    }

    public String[][] listFiles() {
        globalLock.lock();
        try{
            int length = 0;
            for (FEntry entry : inodeTable) {
                if (entry != null) {
                    length++;
                }
            }
            if (length == 0) {
                return new String[0][];
            }
            String[][] files = new String[length][3];
            int index = 0;
            for (int i = 0; i < inodeTable.length; i++) {
                if (inodeTable[i] != null) {
                    files[index][0] = inodeTable[i].getFilename();
                    files[index][1] = Short.toString(inodeTable[i].getFilesize());
                    files[index][2] = Short.toString(inodeTable[i].getFirstBlock());
                    index++;
                }
            }
            return files;
        } finally {
            globalLock.unlock();
        }
    }

    private void metaData(){

    }


    // TODO: Add readFile, writeFile and other required methods,
}
