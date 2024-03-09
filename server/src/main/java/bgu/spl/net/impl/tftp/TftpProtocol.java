package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate;
    private Connections<byte[]> connections;
    private int connectionId;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        shouldTerminate = false;
        this.connections = connections;
        this.connectionId = connectionId;
    }

    @Override
    public void process(byte[] message) {
        shouldTerminate = message.equals("SOMETHING_TO_CHECK");
        // Integer[] ids = connections.getConnectionIds();
        connections.send(connectionId, message); // returns false
    }

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 


    
}
