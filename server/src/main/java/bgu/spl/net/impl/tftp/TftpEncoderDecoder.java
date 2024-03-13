package bgu.spl.net.impl.tftp;

import java.util.ArrayList;
import java.util.List;

import bgu.spl.net.api.MessageEncoderDecoder;

public class TftpEncoderDecoder implements MessageEncoderDecoder<byte[]> {

    private List<Byte> bytes = new ArrayList<Byte>();
    private byte op_code;
    private boolean messageEndsWithZero = true;
    private boolean messageEnded = false;
    private byte[] dataLengthInBytes = new byte[2];
    private short dataBytesLeft;

    @Override
    public byte[] decodeNextByte(byte nextByte) {

        bytes.add(nextByte);

        if(bytes.size() == 2) {
            op_code = nextByte;

            if( op_code == 3 || op_code == 4 )
                messageEndsWithZero = false;

            else if(op_code == 6 || op_code == 10) // DIRQ/DISC packets are 2-bytes long
                messageEnded = true;

            else
                messageEndsWithZero = true;
        }

        if(bytes.size() == 3 && op_code == 3) //First byte that represents the length of the data
            dataLengthInBytes[0] = nextByte;           // in a DATA packet

        if(bytes.size() == 4 && op_code == 3) { //Second byte that represents the length of the data
            dataLengthInBytes[1] = nextByte;       // in a DATA packet
            //2-Bytes to short convertion
            dataBytesLeft = (short) ( ( (short) dataLengthInBytes[0] ) << 8  | (short) (dataLengthInBytes[1]) );
        }

        if(bytes.size() == 4 && op_code == 4) // ACK packet is 4-bytes long
            messageEnded = true;

        if(bytes.size() > 6 && op_code == 3) { //Checking if DATA packet has ended, 
            dataBytesLeft--;                    // and keeps track of received data length
            if(dataBytesLeft == 0)
                messageEnded = true;                
        }

        if(nextByte == 0 && messageEndsWithZero) // May need to replace '0' with '\0'
            messageEnded = true;
        
        if(messageEnded) {
            
            byte[] decodedMessage = new byte[bytes.size()];
            for(int i = 0; i < bytes.size(); i++)
                decodedMessage[i] = (byte) bytes.get(i);
            
            bytes.clear();
            messageEnded = false;
            //Other class variables does not require reset between messages

            return decodedMessage;
        }

        return null;
    }

    @Override
    public byte[] encode(byte[] message) { 
        return message; //No conversion or modification needed
    }
}