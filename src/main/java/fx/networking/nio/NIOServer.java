package fx.networking.nio;

import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;
import java.io.IOException;
import java.nio.channels.spi.SelectorProvider;
import java.net.InetSocketAddress;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.nio.channels.SocketChannel;
import java.net.Socket;
import java.util.List;
import java.util.LinkedList;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Author: Stephen van Beek
 * Creation date: 15 Nov 2014
 * Last modified: 22 Nov 2014
 * Basic NIOServer to begin with. Currently, not particularly general with references to specific worker classes.
 */
public class NIOServer implements Runnable {
    private static final Logger LOGGER = LoggerFactory.getLogger(NIOServer.class);
    private String host;
    private int portNum;
    private Selector selector;
    private ServerSocketChannel serverSockChannel;
    private static final int BUFFER_SIZE = 8092;
    private final ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private Worker worker;
    private final List<ChangeRequest> changeRequests = new LinkedList<>();
    private final Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<>();
    private final AtomicBoolean keepRunning = new AtomicBoolean(true);
    private final CountDownLatch stillRunningLatch = new CountDownLatch(1);

    public NIOServer(String host, Worker worker) {
        LOGGER.info("Creating new NIOServer...");
        this.host = host;
        this.portNum = 0;
        try {
            this.selector = initSelector();
        } catch (IOException e) {
            LOGGER.info("Failed to construct NIOServer: {}", e);
        }
        this.worker = worker;
        Thread workerThread = new Thread(this.worker);
        workerThread.start();
        addShutdownHook();
    }

    public NIOServer(String host, int port, Worker worker) {
        LOGGER.info("Creating new NIOServer...");
        this.host = host;
        this.portNum = port;
        try {
            this.selector = initSelector();
        } catch (IOException e) {
            LOGGER.info("Failed to construct NIOServer: {}", e);
        }
        this.worker = worker;
        Thread workerThread = new Thread(this.worker);
        workerThread.start();
        addShutdownHook();
    }

    public int getPortNum() {
        return portNum;
    }

    /**
     * Method to add a message to the send queue for the selector.
     */
    public void send(SocketChannel socket, byte[] data) {
        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socket, ChangeType.CHANGEOPS, SelectionKey.OP_WRITE));
            synchronized (this.pendingData) {
                List<ByteBuffer> queue = this.pendingData.get(socket);
                if (queue == null) {
                    queue = new ArrayList<>();
                    this.pendingData.put(socket, queue);
                }
                queue.add(ByteBuffer.wrap(data));
            }
        }
        this.selector.wakeup();
    }

    public void run() {
        LOGGER.info("Starting up the NIOServer main run loop.");
        try {
            while (this.keepRunning.get()) {
                eventLoopLogic();
            }
        } catch (NullPointerException e) {
            LOGGER.info("The selector was null! {}", e);
        } finally {
            stillRunningLatch.countDown();
        }
    }

    /**
     * Close method to signal to the server to shut down cleanly.
     */
    public void close() throws IOException {
        this.keepRunning.set(false);
        try {
            this.selector.wakeup();
            stillRunningLatch.await();
        } catch (InterruptedException e) {
            LOGGER.info("interrupted while closing the NIOServer: {}", e);
        }
        if (this.selector.isOpen()) {
            this.selector.close();
        }
    }


    /**
     * Method to build and configure the Selector.
     *
     * @return the Selector
     */
    private Selector initSelector() throws IOException {
        LOGGER.info("Initialising the Selector.");
        Selector socketSelector = SelectorProvider.provider().openSelector();

        serverSockChannel = ServerSocketChannel.open();
        serverSockChannel.configureBlocking(false);
        InetSocketAddress inetSockAddr =
                new InetSocketAddress(this.host, this.portNum);

        serverSockChannel.socket().bind(inetSockAddr);
        serverSockChannel.register(socketSelector, SelectionKey.OP_ACCEPT);

        portNum = serverSockChannel.socket().getLocalPort();

        return socketSelector;
    }


    /**
     * Method to accept a new connection. Adds a new SocketChannel and registers it with the selector.
     *
     * @param key SelectionKey
     */
    private void accept(SelectionKey key) throws IOException {
        // For an accept to be pending the channel must be a ServerSocketChannel
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();

        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        // Register the new SocketChannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }


    /**
     * Method to read a message from the server and pass it off to a worker process to
     * have it processed.
     *
     * @param key SelectionKey used to give the socketChannel on which the message was received
     */
    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // clear out the read buffer so that it's read for new data
        this.readBuffer.clear();

        // Attempt to read off the channel
        int numRead;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            LOGGER.error("Problem reading the key ({}), exception thrown: {}", key, e);
            key.cancel();
            socketChannel.close();
            return;
        }

        if (numRead == -1) {
            key.channel().close();
            key.cancel();
            return;
        }

        this.worker.processData(this, socketChannel, this.readBuffer.array(), numRead);
    }


    /**
     * The main event loop logic used to continuously check for incoming messages
     * before passing them off to the server's Worker.
     */
    private void eventLoopLogic() {
        try {
            // Wait for an event on a registered channel
            processChanges();
            if (this.selector == null) {
                throw new NullPointerException();
            }

            this.selector.select();
            processSelectionKeys();
        } catch (IOException e) {
            LOGGER.info("Exception thrown in main run loop: {}", e);
        }
    }


    /**
     * Loops through the current change requests and passes the corresponding SelectionKeys
     * to the selector.
     */
    private void processChanges() {
        synchronized (this.changeRequests) {
            Iterator<ChangeRequest> changes = this.changeRequests.iterator();
            while (changes.hasNext()) {
                ChangeRequest change = changes.next();
                if (change.isType(ChangeType.CHANGEOPS)) {
                    SelectionKey key = change.getSocket().keyFor(this.selector);
                    key.interestOps(change.getOps());
                }
            }
            this.changeRequests.clear();
        }
    }

    /**
     * Loops through the selected keys and processes each accordingly.
     */
    private void processSelectionKeys() throws IOException {
        Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
        while (selectedKeys.hasNext()) {
            SelectionKey key = selectedKeys.next();
            selectedKeys.remove();

            if (!key.isValid()) {
                continue;
            }

            // Check what event is available and deal with it
            if (key.isAcceptable()) {
                this.accept(key);
            } else if (key.isReadable()) {
                this.read(key);
            } else if (key.isWritable()) {
                this.write(key);
            }
        }
    }

    public void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized (this.pendingData) {
            List<ByteBuffer> queue = this.pendingData.get(socketChannel);
            // Write until there's no more data
            while (!queue.isEmpty()) {
                ByteBuffer buf = queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() > 0) {
                    break;
                }
                queue.remove(0);
            }
            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }

    private void addShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
            @Override
            public void run() {

            }
        }));
    }
}
