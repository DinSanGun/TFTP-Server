package bgu.spl.net.impl.tftp;

import java.io.IOException;
import java.net.Socket;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

public class TftpClient {
    public static void main(String[] args) {
        // If no arguments are provided, use default values for host and port
        if (args.length == 0)
            args = new String[] { "localhost", "7777" };

        // Ensure that exactly two arguments are provided: host and port
        if (args.length < 2) {
            System.out.println("you must supply two arguments: host, port");
            System.exit(1);
        }

        // Extract host and port from the arguments
        String host = args[0];
        int port = Integer.parseInt(args[1]);

        Socket sock;

        try {
            // Establish a socket connection to the specified host and port
            sock = new Socket(host, port);

            // Create an instance of the TFTP messaging protocol
            MessagingProtocol<byte[]> protocol = new TftpMessagingProtocol();

            // Create an instance of the TFTP message encoder/decoder
            MessageEncoderDecoder<byte[]> encdec = new TftpEncoderDecoder();

            // Create an instance of the keyboard input handler
            TftpKeyboardHandler inputHandler = new TftpKeyboardHandler(sock, protocol);
            Thread keyboardThread = new Thread(inputHandler);

            // Create a listener for incoming messages from the server
            Runnable listener = new ServerListener(sock, encdec, protocol, inputHandler);
            Thread listenerThread = new Thread(listener);

            // Start the keyboard input handler and the listener threads
            keyboardThread.start();
            listenerThread.start();
        } catch (IOException e) {
            // Print the stack trace if an IOException occurs during socket creation
            e.printStackTrace();
        }
    }
}