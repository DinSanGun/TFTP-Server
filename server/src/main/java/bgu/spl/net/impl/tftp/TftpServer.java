package bgu.spl.net.impl.tftp;

import java.util.function.Supplier;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.srv.Server;

public class TftpServer {
    

        public static void main(String[] args) {
            
            Supplier< BidiMessagingProtocol <byte[]> > protocolFactory = () -> new TftpProtocol();
            Supplier< MessageEncoderDecoder <byte[]> > encdecFactory = TftpEncoderDecoder::new;

        // you can use any server... 
        Server.<byte[]>threadPerClient(
                7777, //port
                protocolFactory, //protocol factory
                encdecFactory //message encoder decoder factory
        ).serve();

    }
}
