package fx.infra.network;

import java.nio.channels.SocketChannel;

public class ChangeRequest {
    private SocketChannel socket;
    private ChangeType type;
    private int ops;
    
    public int getOps() { return ops; }

    public ChangeType getType() { return type; }

    public SocketChannel getSocket() { return socket; }

    public boolean isType(ChangeType ct) { return type == ct; }

    public ChangeRequest(SocketChannel socket, ChangeType type, int ops) {
        this.socket = socket;
        this.type = type;
        this.ops = ops;
    }
} 
