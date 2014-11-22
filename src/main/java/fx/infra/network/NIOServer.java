package fx.infra.network;

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

/**
 * Author: Stephen van Beek
 * Creation date: 15 Nov 2014
 * Last modified: 22 Nov 2014
 * Basic NIOServer to begin with. Currently, not particularly general with references to specific worker classes.
 * 
 */
public class NIOServer implements Runnable {
    private String host;
    private int portNum;
    private Selector selector;
    private ServerSocketChannel serverSockChannel;
    private static final int BUFFER_SIZE = 8092;
    private ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
    private Worker worker;
    private List<ChangeRequest> changeRequests = new LinkedList<ChangeRequest>();
    private Map<SocketChannel, List<ByteBuffer>> pendingData = new HashMap<>();
    private boolean keepRunning = true;
    private CountDownLatch stillRunningLatch = new CountDownLatch(1);


    public NIOServer(String host, int port, Worker worker) {
        this.host = host;
        this.portNum = port;
        try {
            this.selector = initSelector();
        } catch (IOException e) {
            e.printStackTrace();
        }
        this.worker = worker;
        Thread workerThread = new Thread(this.worker);
        workerThread.start();
    }

    /**
     * Close method to signal to the server to shut down cleanly.
     */
    public void close() throws IOException {
        this.keepRunning = false;
        try {
            this.selector.wakeup();
            stillRunningLatch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        if(this.selector.isOpen()) {
            this.selector.close();
        }
    }

    /**
     * Method to build and configure the Selector.
     * @return the Selector
     */
    private Selector initSelector() throws IOException {
        Selector socketSelector = SelectorProvider.provider().openSelector();

        this.serverSockChannel = ServerSocketChannel.open();
        serverSockChannel.configureBlocking(false);
        InetSocketAddress inetSockAddr = 
            new InetSocketAddress(this.host, this.portNum);

        serverSockChannel.socket().bind(inetSockAddr);
        serverSockChannel.register(socketSelector, SelectionKey.OP_ACCEPT);
       
        return socketSelector;
     }

    /**
     * Method to accept a new connection. Adds a new SocketChannel and registers it with the selector.
     * @param key SelectionKey
     */
    private void accept(SelectionKey key) throws IOException {
        // For an accept to be pending the channel must be a ServerSocketChannel
        ServerSocketChannel serverSocketChannel = (ServerSocketChannel) key.channel();
        
        // Accept the connection and make it non-blocking
        SocketChannel socketChannel = serverSocketChannel.accept();
        socketChannel.configureBlocking(false);

        // Register the new Socketchannel with our Selector, indicating
        // we'd like to be notified when there's data waiting to be read
        socketChannel.register(this.selector, SelectionKey.OP_READ);
    }


    /**
     * Method to add a message to the send queue for the selector.
     */
    public void send(SocketChannel socket, byte[] data) {
        synchronized (this.changeRequests) {
            this.changeRequests.add(new ChangeRequest(socket, ChangeType.CHANGEOPS, SelectionKey.OP_WRITE));
            synchronized(this.pendingData) {
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


    /**
     * Method to read a message from the server and pass it off to a worker process to 
     * have it processed.
     * @param key SelectionKey used to give the socketChannel on which the message was received
     */
    private void read(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();

        // clear out the read buffer so that it's read for new data
        this.readBuffer.clear();

        // Attempt to read off the channel
        int numRead = 0;
        try {
            numRead = socketChannel.read(this.readBuffer);
        } catch (IOException e) {
            key.cancel();
            socketChannel.close();
            return;
        }

        if(numRead == -1) {
            key.channel().close();
            key.cancel();
            return;
        }

        this.worker.processData(this, socketChannel, this.readBuffer.array(), numRead);
    }

    // TODO: Tidy up the run() method to reduce complexity.
    public void run() {
        while(this.keepRunning) {
            try {
                // Wait for an event on a registered channel
                synchronized (this.changeRequests) {
                    Iterator<ChangeRequest> changes = this.changeRequests.iterator();
                    while (changes.hasNext()) {
                        ChangeRequest change = changes.next();
                        if(change.type == ChangeType.CHANGEOPS) {
                            SelectionKey key = change.socket.keyFor(this.selector);
                            key.interestOps(change.ops);
                        }
                    }
                    this.changeRequests.clear();
                }
                if(this.selector == null) {
                    System.exit(1);
                }
                this.selector.select();
                
                // Iterate over set of keys for which event are available
                Iterator<SelectionKey> selectedKeys = this.selector.selectedKeys().iterator();
                while (selectedKeys.hasNext()) {
                    SelectionKey key =  selectedKeys.next();
                    selectedKeys.remove();
                    
                    if(!key.isValid()) {
                        continue;
                    }
                    
                    // Check what event is available and deal with it
                    if(key.isAcceptable()) {
                        this.accept(key);
                    } else if(key.isReadable()) {
                        this.read(key); 
                    } else if(key.isWritable()) {
                        this.write(key);
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        stillRunningLatch.countDown();
    }


    public void write(SelectionKey key) throws IOException {
        SocketChannel socketChannel = (SocketChannel) key.channel();
        synchronized(this.pendingData) {
            List<ByteBuffer> queue = this.pendingData.get(socketChannel);
            // Write until there's no more data
            while(!queue.isEmpty()) {
                ByteBuffer buf = queue.get(0);
                socketChannel.write(buf);
                if (buf.remaining() >0 ) {
                    break;
                }
                queue.remove(0);
            }
            if (queue.isEmpty()) {
                key.interestOps(SelectionKey.OP_READ);
            }
        }
    }
}
