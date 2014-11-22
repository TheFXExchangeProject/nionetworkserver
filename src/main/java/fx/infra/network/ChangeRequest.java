package fx.infra.network;

import java.nio.channels.SocketChannel;

public class ChangeRequest {
    public SocketChannel socket;
    public ChangeType type;
    public int ops;
    
    public ChangeRequest(SocketChannel socket, ChangeType type, int ops) {
        this.socket = socket;
        this.type = type;
        this.ops = ops;
    }
} 
