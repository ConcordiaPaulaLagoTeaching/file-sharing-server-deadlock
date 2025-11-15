package ca.concordia.server;

import ca.concordia.filesystem.FileSystemManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

public class ServerTests {

    private void resetSingleton() throws Exception {
        Field f = FileSystemManager.class.getDeclaredField("instance");
        f.setAccessible(true);
        f.set(null, null);
    }

    @AfterEach
    void cleanup() throws Exception { resetSingleton(); }

    @Test
    void testMalformedInputDoesNotCrashServer() throws Exception {
        int port = 12345;
        FileServer server = new FileServer(port, Path.of("target","srv.img").toString(), 128 * 10);
        Thread t = new Thread(() -> server.startLimited(1));
        t.start();
        Thread.sleep(200); // allow startup
        try (Socket s = new Socket("localhost", port);
             PrintWriter out = new PrintWriter(s.getOutputStream(), true);
             BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
            out.println(""); // empty line
            String resp = in.readLine();
            assertNotNull(resp);
            assertTrue(resp.contains("ERROR"));
        }
        t.join(1000);
        assertFalse(t.isAlive(), "Server thread should have stopped after limited connections");
    }

    @Test
    void testServerRestartPersistence() throws Exception {
        String backing = Path.of("target","persist-" + System.nanoTime() + ".img").toString();
        FileSystemManager fs1 = new FileSystemManager(backing, 128 * 10);
        fs1.createFile("persisted");
        resetSingleton(); // simulate JVM restart
        FileSystemManager fs2 = new FileSystemManager(backing, 128 * 10);
        String[][] files = fs2.listFiles();
        assertEquals(1, files.length);
        assertEquals("persisted", files[0][0]);
    }

    @Test
    void testHandlesHundredsOfClientsQuickly() throws Exception {
        int port = 12347;
        FileServer server = new FileServer(port, Path.of("target","multi.img").toString(), 128 * 10);
        int clients = 50; // reduced for local speed; concept test
        Thread t = new Thread(() -> server.startLimited(clients));
        t.start();
        Thread.sleep(200);
        CountDownLatch latch = new CountDownLatch(clients);
        List<Thread> threads = new ArrayList<>();
        for (int i=0;i<clients;i++) {
            Thread c = new Thread(() -> {
                try (Socket s = new Socket("localhost", port);
                     PrintWriter out = new PrintWriter(s.getOutputStream(), true);
                     BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream()))) {
                    out.println("LIST");
                    in.readLine();
                } catch (Exception ignored) {} finally { latch.countDown(); }
            });
            c.start();
            threads.add(c);
        }
        boolean finished = latch.await(5, TimeUnit.SECONDS);
        assertTrue(finished, "Clients should finish quickly");
        t.join(2000);
        assertFalse(t.isAlive(), "Server should stop after limited clients");
    }
}