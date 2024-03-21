package bgu.spl.net.impl.tftp;

import java.util.function.Supplier;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.srv.Connections;
import bgu.spl.net.srv.ConnectionsImpl;
import bgu.spl.net.srv.Server;

public class TftpServer {
    
        public static void main(String[] args) {
            
            Supplier< BidiMessagingProtocol <byte[]> > protocolFactory = () -> new TftpProtocol();
            Supplier< MessageEncoderDecoder <byte[]> > encdecFactory = TftpEncoderDecoder::new;
            Connections<byte[]> connections = new ConnectionsImpl<byte[]>();
            int port;

            if(args.length != 0)
                port = Integer.parseInt(args[0]);
            else
                port = 7777; //Default port

        // you can use any server... 
        Server.<byte[]>threadPerClient(
                port, //port
                protocolFactory, //protocol factory
                encdecFactory, //message encoder decoder factory
                connections //An object to manage and handle the multiple clients' connections to the server.
        ).serve();

    }
}
