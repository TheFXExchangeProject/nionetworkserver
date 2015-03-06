package fx.networking.nio;
import java.nio.channels.SocketChannel;

/**
 * A ServerDataEvent is used to provide a worker with the information required for it to process
 * outgoing data.
 */
class ServerDataEvent {
    private NIOServer server;
    private SocketChannel socket;
    private byte[] data;
    
    public ServerDataEvent(NIOServer server, SocketChannel socket, byte[] data) {
        this.server = server;
        this.socket = socket;
        this.data = data;
    }

    /**
     * Sends the data held by this event to the server listed.
     * @throws InterruptedException when the server is interrupted
     * while trying to send the data
     */
    public void sendToServer() throws InterruptedException {
        server.send(socket, data);
    }

}
