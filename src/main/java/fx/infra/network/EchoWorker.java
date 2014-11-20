
//
package fx.infra.network;

import java.util.List;
import java.util.LinkedList;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.Queue;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Author: Stephen van Beek
 * Creation: 15 Nov 2014
 *
 * A simple echo class which takes in a message from the server and sends
 * it back to the client as is.
 */
public class EchoWorker implements Runnable {
    private BlockingQueue<ServerDataEvent> queue = new ArrayBlockingQueue<>(1000000, false);
    private AtomicLong atom = new AtomicLong(0);

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
                dataEvent.server.send(dataEvent.socket, dataEvent.data);
            } catch (InterruptedException e) {
            }
        }
    }
}

