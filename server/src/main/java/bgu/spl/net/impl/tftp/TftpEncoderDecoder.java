package bgu.spl.net.impl.tftp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private List<Byte> bytes = new ArrayList<Byte>();

    @Override
    public byte[] decodeNextByte(byte nextByte) {

        if(nextByte == 0) {
            byte[] decodedMessage = new byte[bytes.size()];
            for(int i = 0; i < bytes.size(); i++)
                decodedMessage[i] = (byte) bytes.get(i);

            return decodedMessage;
        }
        bytes.add(nextByte);
        return null;
    }

    @Override
    //Since the message input and output are of the same type - 
    // solely adds a separator 0-byte to the end of the message, and returns it.
    public byte[] encode(byte[] message) { 
        //This method copies the values of the array into a new array with new size - 
        //padded with zeros if size is greater - zero byte is exactly our postfix separator.
        return Arrays.copyOf(message, message.length + 1);
    }
}