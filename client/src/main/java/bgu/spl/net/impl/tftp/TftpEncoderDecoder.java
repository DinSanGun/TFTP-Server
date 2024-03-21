package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessageEncoderDecoder;
import java.util.LinkedList;
import java.util.List;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {
    //list to store bytes of the incoming message
    private List<Byte> bytesList;
    //OpCode for message identification
    private short opCode;
    private int dataBytesLeft;

    public TftpEncoderDecoder() {
        this.bytesList = new LinkedList<>();
        dataBytesLeft = 0;
    }

    @Override
    public byte[] decodeNextByte(byte nextByte) {
        //if the bytes list is empty, add the next byte
        bytesList.add(nextByte);

        if(bytesList.size() == 1) //First byte does not provide us enough information
            return null;


        if (bytesList.size() == 2) {
            //extract OpCode from the first two bytes
            byte[] opCodeBytes = new byte[]{bytesList.get(0), bytesList.get(1)};
            this.opCode = (short) (((short) opCodeBytes[0]) << 8 | (short) (opCodeBytes[1] & 0xFF));
        }

        //decode message based on OpCode
        switch (opCode) {
            case (3): //data message
                if (bytesList.size() == 4) {
                    byte[] sizeBytes = new byte[] { bytesList.get(2), bytesList.get(3) };
                    int packetDataSize = (short) (((short) sizeBytes[0]) << 8 | (short) (sizeBytes[1] & 0xFF));
                    dataBytesLeft = packetDataSize; //Saving data section size
                }
                else if(bytesList.size() > 6) { //Reading the data section
                    dataBytesLeft--;
                    if(dataBytesLeft == 0)
                        return bytesToArray();
                } 
                return null;

            case (4): //acknowledgement message
                if(bytesList.size() == 4) 
                    return bytesToArray();
                break;

            case (5): //error message
                if (bytesList.size() > 3) { //3rd byte of error message might be zero-byte
                    if (nextByte == 0)
                        return bytesToArray(); //complete message received
                    else
                        return null; //incomplete message, continue collecting bytes
                }
                break;

            case (9): //broadcast message
                if (bytesList.size() > 3) { //3rd byte of error message might be zero-byte
                    if (nextByte == 0)
                        return bytesToArray(); //complete message received
                    else
                        return null; //incomplete message, continue collecting bytes
                }
                break;
            default: System.out.println("Some error has occured when encoding message from server"); break;
        }

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