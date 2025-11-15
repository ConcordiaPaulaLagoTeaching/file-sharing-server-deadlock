package ca.concordia.filesystem;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

public class FileSystemTests {

    private void resetSingleton() throws Exception {
        Field f = FileSystemManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @AfterEach
    void cleanup() throws Exception {
        resetSingleton();
    }

    private FileSystemManager newFs() {
        Path p = Path.of("target","fs-test-" + System.nanoTime() + ".img");
        return new FileSystemManager(p.toString(), 128 * 10);
    }

    @Test
    void testCreateFile() throws Exception {
        FileSystemManager fs = newFs();
        fs.createFile("file1");
        String[][] files = fs.listFiles();
        assertEquals(1, files.length);
        assertEquals("file1", files[0][0]);
        assertEquals("0", files[0][1]);
    }

    @Test
    void testWriteAndReadFile() throws Exception {
        FileSystemManager fs = newFs();
        fs.createFile("f");
        byte[] data = "HelloWorld".getBytes();
        fs.writeFile("f", data);
        byte[] read = fs.readFile("f");
        assertArrayEquals(data, read);
    }

    @Test
    void testWriteAndReadLongFile() throws Exception {
        FileSystemManager fs = newFs();
        fs.createFile("longf");
        byte[] data = new byte[300]; // spans multiple 128-byte blocks
        for (int i=0;i<data.length;i++) data[i] = (byte)(i % 251);
        fs.writeFile("longf", data);
        byte[] read = fs.readFile("longf");
        assertArrayEquals(data, read);
    }

    @Test
    void testTooLongFilename() throws Exception {
        FileSystemManager fs = newFs();
        Exception ex = assertThrows(Exception.class, () -> fs.createFile("thisFileNameIsWayTooLong"));
        assertTrue(ex.getMessage().toLowerCase().contains("long"));
    }

    @Test
    void testDeleteFile() throws Exception {
        FileSystemManager fs = newFs();
        fs.createFile("todel");
        fs.deleteFile("todel");
        assertEquals(0, fs.listFiles().length);
    }
}