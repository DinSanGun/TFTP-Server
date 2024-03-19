package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.net.Socket;

import bgu.spl.net.api.MessageEncoderDecoder;
import bgu.spl.net.api.MessagingProtocol;

public class ServerListener implements Runnable {
    private BufferedInputStream inputStream;
    private MessageEncoderDecoder<byte[]> messageEncoderDecoder;
    private MessagingProtocol<byte[]> protocol;
    private TftpKeyboardHandler keyboardHandler;

    public ServerListener(Socket socket, MessageEncoderDecoder<byte[]> messageEncoderDecoder,
                          MessagingProtocol<byte[]> protocol, TftpKeyboardHandler keyboardHandler) {
        try {
            this.inputStream = new BufferedInputStream(socket.getInputStream());
        } catch (IOException ignored) {
            // Handle exception
        }

        this.messageEncoderDecoder = messageEncoderDecoder;
        this.protocol = protocol;
        this.keyboardHandler = keyboardHandler;
    }

    @Override
    public void run() {
        int readByte; // Current byte read from the input stream
        byte[] nextMessage, response;

        try {
            while (!protocol.shouldTerminate() && (readByte = inputStream.read()) >= 0) {
                nextMessage = messageEncoderDecoder.decodeNextByte((byte) readByte);

                if (nextMessage != null) {
                    response = protocol.process(nextMessage);

                    if (response != null)
                        keyboardHandler.send(response);

                    synchronized (keyboardHandler.discLock) {
                        keyboardHandler.discLock.notify();
                    }
                }
            }
        } catch (IOException ignored) {
            // Handle exception
        }
    }
}