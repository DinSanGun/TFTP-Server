package bgu.spl.net.srv;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.BidiMessagingProtocol;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.net.Socket;

public class BlockingConnectionHandler<T> implements Runnable, ConnectionHandler<T> {

    private final BidiMessagingProtocol<T> protocol;
    private final MessageEncoderDecoder<T> encdec;
    private final Socket sock;
    private BufferedInputStream in;
    private BufferedOutputStream out;
    protected volatile boolean connected;
    private final Connections<T> connections;

    public BlockingConnectionHandler(Socket sock, MessageEncoderDecoder<T> reader, BidiMessagingProtocol<T> protocol, Connections<T> connections) {
        this.sock = sock;
        this.encdec = reader;
        this.protocol = protocol;
        this.connections = connections;
        connected = false;
    }

    
    @Override
    public void run() {

        try (Socket sock = this.sock) { //just for automatic closing
            int read;

            in = new BufferedInputStream(sock.getInputStream());
            out = new BufferedOutputStream(sock.getOutputStream());

            int connectionId = connections.getNewConnectionId();

            protocol.start( connectionId , connections);
            connected = connections.connect(connectionId, this);

            while (!protocol.shouldTerminate() && connected && (read = in.read()) >= 0) {

                T nextMessage = encdec.decodeNextByte((byte) read);
                if (nextMessage != null) 
                    protocol.process(nextMessage);
            }

        } catch (IOException ex) {
            ex.printStackTrace();
        } 

        connected = false;

        try {
            close();
        } catch(IOException ex) {
            ex.printStackTrace();
        }

    }

    /**
     * A method that closes the connection between the server and the represented client.
     */
    @Override
    public void close() throws IOException {
        connected = false;
        sock.close();
    }

    /**
     * A method that sends a message 'msg' to the represented client.
     */
    @Override
    public void send(T msg) {
        if (msg != null) {
            try{
                out.write(encdec.encode(msg));
                out.flush(); 
            } catch(IOException ex){
                ex.printStackTrace();
            }
        }
    }
}
