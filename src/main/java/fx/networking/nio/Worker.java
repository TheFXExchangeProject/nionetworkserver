package fx.networking.nio;

import java.nio.channels.SocketChannel;

/**
 * This is the worker interface to be implemented by any class,
 * intended to be used by any class which is used as a worker by the
 * nioserver.
 * Author: Stephen van Beek
 */
public interface Worker extends Runnable {
    /**
     * Method used by the nioserver to handle any incoming data.
     */
    public void processData(NIOServer server, SocketChannel socket, byte[] data, int count);

}
