
package fx.infra.network;
import java.nio.channels.SocketChannel;

class ServerDataEvent {
	public NIOServer server;
	public SocketChannel socket;
	public byte[] data;
	
	public ServerDataEvent(NIOServer server, SocketChannel socket, byte[] data) {
		this.server = server;
		this.socket = socket;
		this.data = data;
	}
}
