package fx.networking.nio;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import java.nio.channels.SocketChannel;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

@RunWith(MockitoJUnitRunner.class)
public class EchoWorkerTest {
    @Mock
    private NIOServer nioServer;
    @Mock
    private SocketChannel socketChannel;


    @Test
    public void testProcessData() throws Exception {
        EchoWorker echoWorker = new EchoWorker();
        Thread thread = new Thread(echoWorker);
        thread.start();

        final byte[] message = new byte[] {1,2,3,4,5};

        final int TEST_SIZE = 10000;

        for (int i = 0; i < TEST_SIZE; i++) {
            echoWorker.processData(nioServer, socketChannel, message, 5);
        }

        verify(nioServer, timeout(10000).times(TEST_SIZE)).send(socketChannel, message);
    }
}