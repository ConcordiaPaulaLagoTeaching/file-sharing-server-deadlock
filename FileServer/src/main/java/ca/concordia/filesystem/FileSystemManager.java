//DELETE LATER
//To run main: run these commands in terminal
//First cd to FileServer folder with cd FileServer
//mvn -DskipTests package
//mvn exec:java -Dexec.mainClass="ca.concordia.Main"

package ca.concordia.filesystem;

import ca.concordia.filesystem.datastructures.FEntry;

import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;
import ca.concordia.filesystem.datastructures.FNode;

public class FileSystemManager {

    private final long dataAreaSize; // size reserved for block data (from constructor totalSize)
    private final int METADATA_MAGIC = 0x46535953; // 'FSYS'
    private final int METADATA_VERSION = 1;
    private final int FILENAME_BYTES = 11;
    private final int INODE_RECORD_BYTES = FILENAME_BYTES + 2 + 2; // filename + short filesize + short firstBlock
    private final int metadataSize;
    private final int MAXFILES = 5;
    private final int MAXBLOCKS = 10;
    private static FileSystemManager instance = null; // initially null, set in constructor
    private RandomAccessFile disk; // initialized in constructor
    private final ReentrantLock globalLock = new ReentrantLock();
    private FNode[] blockTable;

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

                this.dataAreaSize = totalSize;
                this.metadataSize = 4 + 4 + (MAXFILES * INODE_RECORD_BYTES) + (MAXBLOCKS * 1) + (MAXBLOCKS * 4); // magic + ver + inodes + freeBitmap + block nexts
                long minLen = this.dataAreaSize + this.metadataSize;
                if (this.disk.length() < minLen) {
                    this.disk.setLength(minLen);
                }

                // Initialize inode table and free block list
                this.inodeTable = new FEntry[MAXFILES];
                this.freeBlockList = new boolean[MAXBLOCKS];
                this.blockTable = new FNode[MAXBLOCKS];
                for (int i = 0; i < MAXBLOCKS; i++) {
                    freeBlockList[i] = true; // all blocks free initially
                    blockTable[i] = null;
                }

                try {
                    loadMetaData();
                } catch (IOException ignored) {
                    // No existing metadata or corrupt -> keep defaults and persist later when metaData() is called.
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

    public String[][] listFiles(){
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

    public void deleteFile(String filename){}

    public void writeFile(String filename, byte[] content) throws Exception {

        if (filename == null || filename.isEmpty()) {
            throw new IllegalArgumentException("Make sure to enter a valid filename.");
        }
        if (content.length == 0) {
            throw new IllegalArgumentException("Make sure to enter valid content to write.");
        }

        globalLock.lock();
        boolean[] oldFree = null;
        FNode[] oldBlockTable = null;
        short oldFileSize = 0;
        short oldFirstBlock = 0;
        int inodeIndex = -1;
        try {
            for (int i=0; i<inodeTable.length; i++) {
                if (inodeTable[i] != null && inodeTable[i].getFilename().equals(filename)) {
                    inodeIndex = i;
                    break;
                }
            }
            if (inodeIndex == -1) {
                throw new Exception("File not found. Verify the filename and try again.");
            }

            FEntry entry = inodeTable[inodeIndex];

            java.util.List<Integer> currentBlocks = new java.util.ArrayList<>();
            short first = entry.getFirstBlock();
            if (first >= 0 && first < MAXBLOCKS) {
                int cur = first;
                while (cur != -1) {
                    currentBlocks.add(cur);
                    FNode node = blockTable[cur];
                    if (node == null) break;
                    cur = node.getNext();
                }
            }

            int requiredBlocks = (content.length == 0) ? 0 : ((content.length + BLOCK_SIZE - 1) / BLOCK_SIZE);

            // count available free blocks (excluding blocks already used by this file)
            java.util.List<Integer> availableFree = new java.util.ArrayList<>();
            for (int i = 0; i < freeBlockList.length; i++) {
                if (freeBlockList[i]) availableFree.add(i);
            }

            int availableTotal = availableFree.size() + currentBlocks.size();
            if (requiredBlocks > availableTotal) {
                throw new Exception("Not enough free space: need " + requiredBlocks + " blocks, available " + availableTotal);
            }

            // plan target blocks: reuse current blocks first, then allocate from free list
            java.util.List<Integer> targetBlocks = new java.util.ArrayList<>();
            int toReuse = Math.min(currentBlocks.size(), requiredBlocks);
            for (int i = 0; i < toReuse; i++) targetBlocks.add(currentBlocks.get(i));
            int needMore = requiredBlocks - targetBlocks.size();
            for (int i = 0; i < needMore; i++) {
                targetBlocks.add(availableFree.get(i));
            }

            // snapshot metadata for rollback
            oldFree = Arrays.copyOf(freeBlockList, freeBlockList.length);
            oldBlockTable = Arrays.copyOf(blockTable, blockTable.length);
            oldFileSize = entry.getFilesize();
            oldFirstBlock = entry.getFirstBlock();

            // Apply metadata changes:
            // free blocks that were in currentBlocks but not in targetBlocks
            for (int b : currentBlocks) {
                if (!targetBlocks.contains(b)) {
                    freeBlockList[b] = true;
                    blockTable[b] = null;
                }
            }
            // allocate new blocks (mark used and create FNode) for blocks in targetBlocks not in currentBlocks
            for (int b : targetBlocks) {
                if (!currentBlocks.contains(b)) {
                    freeBlockList[b] = false;
                    blockTable[b] = new FNode(b);
                }
            }
            // link the chain
            for (int i = 0; i < targetBlocks.size(); i++) {
                int idx = targetBlocks.get(i);
                FNode node = blockTable[idx];
                if (node == null) {
                    node = new FNode(idx);
                    blockTable[idx] = node;
                }
                if (i + 1 < targetBlocks.size()) {
                    node.setNext(targetBlocks.get(i + 1));
                } else {
                    node.setNext(-1);
                }
            }

            // update inode metadata
            short newFirstBlock = (targetBlocks.size() > 0) ? (short) (int) targetBlocks.get(0) : (short) -1;
            entry.setFilesize((short) content.length);
            entry.setFirstBlock(newFirstBlock);

            // write data to disk block-by-block
            try {
                int bytesWritten = 0;
                for (int i = 0; i < targetBlocks.size(); i++) {
                    int blockIdx = targetBlocks.get(i);
                    long offset = (long) blockIdx * BLOCK_SIZE;
                    disk.seek(offset);
                    int remaining = content.length - bytesWritten;
                    int toWrite = Math.min(remaining, BLOCK_SIZE);
                    if (toWrite > 0) {
                        disk.write(content, bytesWritten, toWrite);
                        bytesWritten += toWrite;
                        if (toWrite < BLOCK_SIZE) {
                            // zero the rest of the block
                            byte[] zeros = new byte[BLOCK_SIZE - toWrite];
                            disk.write(zeros);
                        }
                    } else {
                        // content ended; zero full block
                        byte[] zeros = new byte[BLOCK_SIZE];
                        disk.write(zeros);
                    }
                }
                // persist metadata (if you have a metadata writer, call it here)
                metaData();
            } catch (Exception ioEx) {
                // rollback metadata on write failure
                freeBlockList = oldFree;
                blockTable = oldBlockTable;
                inodeTable[inodeIndex].setFilesize(oldFileSize);
                inodeTable[inodeIndex].setFirstBlock(oldFirstBlock);
                throw new Exception("Failed to write file data: " + ioEx.getMessage(), ioEx);
            }

        } finally {
            globalLock.unlock();
        }

    }

    private byte[] fixedBytes(String s, int len) {
        byte[] b = new byte[len];
        if (s == null) s = "";
        byte[] src = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        int copy = Math.min(src.length, len);
        System.arraycopy(src, 0, b, 0, copy);
        // remaining bytes are zeros
        return b;
    }

    private void metaData() throws IOException {
        // write metadata atomically to reserved metadata area after dataAreaSize
        synchronized (disk) {
            disk.seek(dataAreaSize);
            disk.writeInt(METADATA_MAGIC);
            disk.writeInt(METADATA_VERSION);
            // write inode table (fixed-size records)
            for (int i = 0; i < MAXFILES; i++) {
                FEntry e = inodeTable[i];
                if (e != null) {
                    disk.write(fixedBytes(e.getFilename(), FILENAME_BYTES));
                    disk.writeShort(e.getFilesize());
                    disk.writeShort(e.getFirstBlock());
                } else {
                    disk.write(new byte[INODE_RECORD_BYTES]);
                }
            }
            // write free block list as bytes (0/1)
            for (int i = 0; i < MAXBLOCKS; i++) {
                disk.writeByte(freeBlockList[i] ? 1 : 0);
            }
            // write blockTable next pointers (-1 if null)
            for (int i = 0; i < MAXBLOCKS; i++) {
                FNode node = blockTable[i];
                int next = (node == null) ? -1 : node.getNext();
                disk.writeInt(next);
            }
            disk.getFD().sync();
        }
    }

    private void loadMetaData() throws IOException {
        synchronized (disk) {
            if (disk.length() < dataAreaSize + 8) return; // no metadata
            disk.seek(dataAreaSize);
            int magic = disk.readInt();
            int version = disk.readInt();
            if (magic != METADATA_MAGIC || version != METADATA_VERSION) {
                // incompatible or no metadata, skip loading
                return;
            }
            // read inodes
            for (int i = 0; i < MAXFILES; i++) {
                byte[] nameBytes = new byte[FILENAME_BYTES];
                disk.readFully(nameBytes);
                String name = new String(nameBytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                short filesize = disk.readShort();
                short firstBlock = disk.readShort();
                if (!name.isEmpty()) {
                    inodeTable[i] = new FEntry(name, filesize, firstBlock);
                } else {
                    inodeTable[i] = null;
                }
            }
            // free list
            for (int i = 0; i < MAXBLOCKS; i++) {
                int b = disk.readByte();
                freeBlockList[i] = (b != 0);
            }
            // blockTable next pointers
            for (int i = 0; i < MAXBLOCKS; i++) {
                int next = disk.readInt();
                if (next == -1 && freeBlockList[i]) {
                    blockTable[i] = null;
                } else {
                    // ensure node exists and set next
                    if (blockTable[i] == null) blockTable[i] = new FNode(i);
                    blockTable[i].setNext(next);
                }
            }
        }
    }


    // TODO: Add readFile, writeFile and other required methods,
}
