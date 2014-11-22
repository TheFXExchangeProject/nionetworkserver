package fx.infra.network;

import java.io.IOException;
import org.junit.BeforeClass;
import java.net.UnknownHostException;
import java.net.Socket;
import org.junit.Test;
import static org.junit.Assert.assertTrue;
import org.junit.AfterClass;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import org.junit.Assert;
import java.io.BufferedReader;
import static org.junit.Assert.assertEquals;
import java.util.List;
import java.util.ArrayList;

/**
 * Author: S. van Beek
 * Creation date: 13 Nov 2014
 * Last Modified by:
 * Last Modified date: 
 */

public class NIOServerTest {
    private static NIOServer nioServer;
    private static int portNum = 10000;
    private static Thread serverThread;

    @BeforeClass
    public static void setUp() {
        nioServer = new NIOServer("localhost", portNum, new EchoWorker());
        serverThread = new Thread(nioServer);
        serverThread.start();
    }

    @Test
    public void testConnectToServerSuccessfully() throws UnknownHostException, IOException {
        Socket newConnection = new Socket("localhost", portNum);
        assertTrue("Assert that the socket registers a connection", 
                   newConnection.isConnected());
        
    }
    

    @Test
    public void testGetsEchoFromServer() throws Exception {
        Socket newConnection = new Socket("localhost", portNum);
        BufferedReader bufferReader = new BufferedReader(new InputStreamReader(newConnection.getInputStream()));
        PrintWriter printWriter = new PrintWriter(newConnection.getOutputStream());
        printWriter.println("Hello World!\n");
        printWriter.flush();
        String message = bufferReader.readLine();
        while (message == null) {
            Thread.sleep(100);
            message = bufferReader.readLine();
            System.out.printf("%s\n", message);
        }
        assertEquals("Message was echoed.", "Hello World!", message);
    }

    @Test
    public void testHandlingHundredSocketsThousandMessages() throws Exception {
        List<Socket> socketList = new ArrayList<>(1000);
        List<BufferedReader> buffReadList = new ArrayList<>(1000);
        List<PrintWriter> printList = new ArrayList<>(1000);
        // Connect 100 sockets to the server
        for(int i = 0; i< 1000; i++) {
            Socket newConnection = new Socket("localhost", portNum);
            socketList.add(newConnection);
            buffReadList.add(new BufferedReader(new InputStreamReader(newConnection.getInputStream())));
            PrintWriter printWriter = new PrintWriter(newConnection.getOutputStream());
            printList.add(printWriter);
        }

        long startTime = System.nanoTime();
        // Send 1000 messages per socket connection
        for (PrintWriter pw : printList) {
            for(int j = 0; j < 7000; j++) {
                pw.println("Hello");
             }
        }
        
        // Send 7000 messages at a time
        for (PrintWriter pw : printList) {
            pw.flush();
        }

        int count = 0;
        for (BufferedReader buf : buffReadList) {
            for (int i = 0; i < 1000; i++) {
                buf.readLine();
            }
        }
    }


    @AfterClass
    public static void tearDown() throws IOException {
        nioServer.close();
    }
}
