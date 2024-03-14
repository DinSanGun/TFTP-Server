package bgu.spl.net.impl.echo;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class EchoClient {

    public static void main(String[] args) throws IOException {

        if (args.length == 0) {
            args = new String[]{"localhost", "hello"};
        }

        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, message");
            System.exit(1);
        }

        //BufferedReader and BufferedWriter automatically using UTF-8 encoding
        try (Socket sock = new Socket("localhost", 7777);
                BufferedInputStream in = new BufferedInputStream((sock.getInputStream()));
                BufferedOutputStream out = new BufferedOutputStream((sock.getOutputStream())) ) {

            System.out.println("sending message to server");
            String name = "Din";
            byte[] nameBytes = name.getBytes(StandardCharsets.UTF_8);
            byte[] packet = {0,7,nameBytes[0],nameBytes[1],nameBytes[2],0};
            out.write(packet);
            out.flush();
            
            System.out.println("awaiting response");
            byte[] result = new byte[4];
            in.read(result);
            System.out.print("[");
            for(int i = 0; i < result.length; i++)
                System.out.print(result[i] + " ");
            System.out.print("]");

        }
    }
}
