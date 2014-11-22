package fx.infra.network;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ArrayBlockingQueue;
import java.nio.channels.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Author: Stephen van Beek
 * Creation: 15 Nov 2014
 * A simple echo class which takes in a message from the server and sends
 * it back to the client as is.
 */
public class EchoWorker implements Runnable, Worker {
    private static final Logger LOGGER = LoggerFactory.getLogger(EchoWorker.class);
    private static final int QUEUE_SIZE = 1000000;
    private BlockingQueue<ServerDataEvent> queue = new ArrayBlockingQueue<>(QUEUE_SIZE, false);

    @Override
    public void processData(NIOServer server, SocketChannel socket, byte[] data, int count) {
        byte[] dataCopy = new byte[count];
        System.arraycopy(data, 0, dataCopy, 0, count);
        queue.add(new ServerDataEvent(server, socket, data));
    }

    /**
     * Main method which runs in a loop through the lifetime of the object instance.
     * Method simply waits until the queue has a message to be sent, then sends it.
     */
    public void run() {
        ServerDataEvent dataEvent;
        while(true) {
            try {
                // Wait for data to become available
                dataEvent = queue.take(); 
                // Send data to server
                dataEvent.sendToServer();
            } catch (InterruptedException e) {
                LOGGER.info("Interrupted while waiting on the queue {}", e);
            }
        }
    }
}

