package fx.networking.nio;

import java.io.IOException;

import org.junit.*;

import java.net.UnknownHostException;
import java.net.Socket;

import static org.junit.Assert.assertTrue;

import java.io.InputStreamReader;
import java.io.PrintWriter;

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
    private NIOServer nioServer;
    private Thread serverThread;
    private int portNum;

    @Before
    public void setUp() {
        nioServer = new NIOServer("localhost", new EchoWorker());
        serverThread = new Thread(nioServer);
        serverThread.start();
        portNum = nioServer.getPortNum();
    }

    @After
    public void tearDown() throws IOException {
        nioServer.close();
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
        for (int i = 0; i < 1000; i++) {
            Socket newConnection = new Socket("localhost", portNum);
            socketList.add(newConnection);
            buffReadList.add(new BufferedReader(new InputStreamReader(newConnection.getInputStream())));
            PrintWriter printWriter = new PrintWriter(newConnection.getOutputStream());
            printList.add(printWriter);
        }

        // Send 1000 messages per socket connection
        for (PrintWriter pw : printList) {
            for (int j = 0; j < 7000; j++) {
                pw.println("Hello");
            }
        }

        // Send 7000 messages at a time
        for (PrintWriter pw : printList) {
            pw.flush();
        }


        for (BufferedReader buf : buffReadList) {
            for (int i = 0; i < 1000; i++) {
                assertEquals("Hello", buf.readLine());
            }
        }

        for (Socket socket : socketList) {
            socket.close();
        }
    }
}
