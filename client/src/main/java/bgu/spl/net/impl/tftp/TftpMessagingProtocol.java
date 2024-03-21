package bgu.spl.net.impl.tftp;

import bgu.spl.net.api.MessagingProtocol;

import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

public class TftpMessagingProtocol implements MessagingProtocol<byte[]> {
    private boolean terminate = false;
    private short lastBlock = 0;
    private Queue<byte[]> packets = new ConcurrentLinkedQueue<>();
    private Opcode lastOpcode = Opcode.UNKNOWN;
    private final String directoryPath = "";
    private String fileTransferred = "";
    private ArrayDeque<Byte> currentDir = new ArrayDeque<>();
    String lastArg;

    //-----------------------------------ENUMS---------------------------------//

    public enum Opcode { // Enum representing supported TFTP opcodes
        RRQ(1), 
        WRQ(2), 
        DATA(3), 
        ACK(4), 
        ERROR(5), 
        DIRQ(6), 
        LOGRQ(7), 
        DELRQ(8), 
        BCAST(9), 
        DISC(10), 
        UNKNOWN(-1);
        
        private final short value;

        Opcode(int value) {
            this.value = (short) value;
        }

        public byte[] toBytes() { 
            return new byte[]{(byte) (value >> 8), (byte) (value & 0xff)};
        }

        public static Opcode fromInt(int value) {
            for (Opcode opcode : Opcode.values()) {
                if (opcode.value == value) {
                    return opcode;
                }
            }
            return UNKNOWN;
        }

        public static Opcode fromString(String value) {

            switch(value) {
                case "RRQ":     return RRQ; 
                case "WRQ":     return WRQ;
                case "LOGRQ":   return LOGRQ;
                case "DELRQ":   return DELRQ;
                case "DISC":    return DISC;
                case "DIRQ":    return DIRQ;
                default:        return UNKNOWN;
            }
        }

        public static Opcode fromBytes(byte a, byte b) { 
            return fromInt((short) (((short) a) << 8 | (short) (b) & 0x00ff));
        }

        public static Opcode extract(byte[] msg) { 
            return fromBytes(msg[0], msg[1]); 
        }
    }

    public enum TftpError {
        NOT_DEFINED("Not defined"),
        FILE_NOT_FOUND("File not found"),
        ACCESS_VIOLATION("Access violation"),
        DISC_FULL("Disk full or allocation exceeded"),
        ILLEGAL_OP("Illegal TFTP operation"),
        FILE_EXISTS("File already exists"),
        NOT_LOGGED_IN("User not logged in"),
        ALR_LOGGED_IN("User already logged in");

        private final byte[] messageBytes;

        TftpError(String message) {
            this.messageBytes = message.getBytes(StandardCharsets.UTF_8);
        }

        public byte[] getMessageBytes() {
            return messageBytes;
        }
    }


    @Override
    public byte[] process(byte[] message) {

        Opcode opcode = Opcode.extract(message);
        byte[] response = null;

        switch (opcode) {
            case DATA:
                response = handleData(message);
                break;
            case ACK:
                response = handleAck(message);
                break;
            case ERROR:
                handleError(message);
                break;
            case BCAST:
                handleBroadcast(message);
                break;
            case UNKNOWN:
                response = createErrorMessage(TftpError.NOT_DEFINED);
                break;
            case RRQ:
            case WRQ:
                lastArg = new String(message, 2, message.length - 3, StandardCharsets.UTF_8);                
        }

        lastOpcode = opcode; //Might be a problem

        return response;
    }

    @Override
    public boolean shouldTerminate() { 
        return terminate; 
    }

    private byte[] createErrorMessage(TftpError error) {
        byte[] pac = new byte[5 + error.getMessageBytes().length];

        pac[0] = Opcode.ERROR.toBytes()[0];
        pac[1] = Opcode.ERROR.toBytes()[1];
        pac[2] = TftpError.NOT_DEFINED.getMessageBytes()[0];
        pac[3] = TftpError.NOT_DEFINED.getMessageBytes()[1];

        System.arraycopy(error.getMessageBytes(), 0, pac, 4, error.getMessageBytes().length);
        pac[pac.length - 1] = 0;

        return pac;
    }


    private byte[] handleAck(byte[] message) {
        short ackBlock = (short) ((message[2] << 8) | (message[3] & 0xFF));
        Opcode cmdOpcode;
        byte[] lastPacket = null;

        if (ackBlock == 0)
            cmdOpcode = lastOpcode;

        else if (packets.isEmpty())
            return createErrorMessage(TftpError.NOT_DEFINED);
        else {
            lastPacket = packets.peek();
            cmdOpcode = Opcode.extract(lastPacket);
        }

        //extract block number from DATA packet if exists
        short packetBlock = cmdOpcode == Opcode.DATA
                ? (short) ((lastPacket[4] << 8) | (lastPacket[5] & 0xFF))
                : 0;
        byte[] response = null;

        if (packetBlock != ackBlock)
            return createErrorMessage(TftpError.NOT_DEFINED);

        String filename;

        switch (cmdOpcode) {
            case WRQ:
                filename = lastArg;
                addPackets(filename);
                fileTransferred = filename;
                response = packets.peek();
                break;
            case DATA:
                packets.remove();
                response = packets.peek();

                if (response == null) {
                    System.out.println("WRQ " + fileTransferred + " complete");
                    fileTransferred = "";
                }

                break;
            case LOGRQ:
            case DELRQ:
                break;
            case DISC:
                terminate = true;
                break;
            default:
                response = createErrorMessage(TftpError.ILLEGAL_OP);
                break;
        }

        return response;
    }

    // Add packets from file to the queue
    private void addPackets(String filename) {
        File fileToSend = new File(directoryPath + File.separator + filename);

        if (!fileToSend.exists())
        System.out.println(new String(TftpError.FILE_NOT_FOUND.getMessageBytes(), StandardCharsets.UTF_8));

        try (FileInputStream fstream = new FileInputStream(fileToSend)) {
            ArrayDeque<Byte> packetData = new ArrayDeque<>();
            int nextByte;
            byte[] packet;

            lastBlock = 0;

            while ((nextByte = fstream.read()) != -1) {
                packetData.add((byte) nextByte);

                // Assuming MAX_DATA_PACKET is 512
                if (packetData.size() == 512) {
                    packet = buildPacket(packetData, ++lastBlock);
                    packets.add(packet);
                    packetData.clear();
                }
            }

            packet = buildPacket(packetData, ++lastBlock);
            packets.add(packet);
        } catch (IOException ignored) {
            System.out.println(ignored.getMessage());
        }
    }

    //handle incoming DATA packet
    private byte[] handleData(byte[] packet) {
        short block = (short) ((packet[4] << 8) | (packet[5] & 0xFF));
        short packetSize = (short) ((packet[2] << 8) | (packet[3] & 0xFF));
        byte[] bytes;
        File file;

        if (lastOpcode == Opcode.RRQ) {
            file = new File(directoryPath + File.separator + lastArg);

            try (FileOutputStream fStream = new FileOutputStream(file, true)) {
                fStream.write(packet, 6, packet.length - 6);
            } catch (IOException ignored) {
            } finally {
                // Assuming MAX_DATA_PACKET is 512
                if (packetSize < 512) {
                    System.out.println("RRQ " + file.getName() + " complete");
                    file = null;
                }
            }
        } else {
            for (int i = 6; i < packet.length; i++) {
                if (packet[i] == 0) {
                    bytes = new byte[currentDir.size()];

                    for (int j = 0; j < bytes.length; j++) {
                        bytes[j] = (byte) currentDir.removeFirst();
                    }

                    System.out.println(new String(bytes, StandardCharsets.UTF_8));
                    currentDir.clear();
                } else
                    currentDir.add(packet[i]);
            }

            //assuming MAX_DATA_PACKET is 512
            if (packetSize < 512) {
                bytes = new byte[currentDir.size()];

                for (int j = 0; j < bytes.length; j++) {
                    bytes[j] = (byte) currentDir.removeFirst();
                }

                System.out.println(new String(bytes, StandardCharsets.UTF_8));
                currentDir.clear();
            }
        }

        return buildAckPacket(block);
    }

    private void handleError(byte[] packet) {
        short errNum = (short) ((packet[2] << 8) | (packet[3] & 0xFF));
        String msg = new String(packet, 4, packet.length - 5, StandardCharsets.UTF_8);
        System.out.println("Error " + errNum + " (" + msg + ")");
    }

    private void handleBroadcast(byte[] message) {
        boolean added = message[2] == 1;
        String filename = new String(message, 3, message.length - 4, StandardCharsets.UTF_8);
        System.out.print("BCAST ");

        if (added)
            System.out.print("add ");
        else
            System.out.print("del ");

        System.out.println(filename);
    }

    //build DATA packet
    private byte[] buildPacket(ArrayDeque<Byte> bytes, short block) {
        byte[] packet = new byte[6 + bytes.size()];
        int bytesSize = bytes.size();

        packet[0] = Opcode.DATA.toBytes()[0];
        packet[1] = Opcode.DATA.toBytes()[1];
        packet[2] = (byte) (bytesSize >> 8);
        packet[3] = (byte) (bytesSize & 0xFF);
        packet[4] = (byte) (block >> 8);
        packet[5] = (byte) (block & 0xFF);

        for (int j = 0; j < bytesSize; j++)
            packet[6 + j] = bytes.removeFirst();

        return packet;
    }

    //build ACK packet
    private byte[] buildAckPacket(short block) {
        byte[] packet = new byte[4];
        packet[0] = Opcode.ACK.toBytes()[0];
        packet[1] = Opcode.ACK.toBytes()[1];
        packet[2] = (byte) (block >> 8);
        packet[3] = (byte) (block & 0xFF);
        return packet;
    }
}