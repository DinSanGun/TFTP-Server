package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.LinkedList;
import java.util.List;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //list to store bytes of the incoming message
    private List<Byte> bytesList;
    //OpCode for message identification
    private short opCode;

    public TftpEncoderDecoder() {
        this.bytesList = new LinkedList<>();
    }

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        //if the bytes list is empty, add the next byte
        if (bytesList.isEmpty()) {
            bytesList.add(nextByte);
            return null; //Continue collecting bytes
        } else
            bytesList.add(nextByte);

        if (bytesList.size() == 2) {
            //extract OpCode from the first two bytes
            byte[] opCodeBytes = new byte[]{bytesList.get(0), bytesList.get(1)};
            this.opCode = (short) (((short) opCodeBytes[0]) << 8 | (short) (opCodeBytes[1] & 0xFF));
        }

        //decode message based on OpCode
        switch (opCode) {
            case (3): //data message
                if (bytesList.size() > 3) {
                    byte[] sizeBytes = new byte[]{bytesList.get(2), bytesList.get(3)};
                    int packetSize = (short) (((short) sizeBytes[0] & 0xFF) << 8 | (short) (sizeBytes[1] & 0xFF));
                    if (bytesList.size() == packetSize + 6)
                        return bytesToArray(); //complete message received
                    else
                        return null; //incomplete message, continue collecting bytes
                }
            case (4): //acknowledgement message
            case (10): //disconnect message
            case (6): //directory inquiry message
                if (bytesList.size() == 2)
                    return bytesToArray(); //complete message received
                else
                    return null; //incomplete message, continue collecting bytes
            case (9): //broadcast message
            case (5): //error message
                if (bytesList.size() > 3) {
                    if (nextByte == 0)
                        return bytesToArray(); //complete message received
                    else
                        return null; //incomplete message, continue collecting bytes
                }
        }

        // If the next byte is zero, it indicates the end of a message
        if (nextByte == 0)
            return bytesToArray(); // Complete message received

        return null; //incomplete message, continue collecting bytes
    }

    //---------------------- ENCODER ----------------------//
    @Override
    public byte[] encode(byte[] message) {
        return message; 
    }

    //------------------ HELPER FUNCTION ------------------//
    // Convert bytes list to byte array and clear list for next message
    private byte[] bytesToArray() {
        byte[] result = new byte[bytesList.size()];
        int i = 0;
        for (Byte b : bytesList) {
            result[i] = b;
            i++;
        }

        bytesList.clear(); //clear the bytes list for the next message
        this.opCode = -1; //reset OpCode
        return result;
    }
}