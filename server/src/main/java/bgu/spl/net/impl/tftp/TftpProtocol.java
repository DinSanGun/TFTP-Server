package bgu.spl.net.impl.tftp;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private boolean shouldTerminate;
    private int connectionId;
    private Connections<byte[]> connections;

    private final byte[] genericAckPacket = {0 , 4 , 0 , 0};

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
    }

    @Override
    public void process(byte[] message) {

        byte op_code = message[1]; //The op code is represented by the first 2 bytes -
                                // first of them is zero according to the instructions

        switch (op_code) {
            case 1: clientDownloadRequest(message); break;
            case 2: clientUploadRequest(message); break;
            case 3: break;
            case 4: break;
            case 5: break;
            case 6: directoryList(); break; //STILL NEED TO ADD ACKNOWLDEGMENT BETWEEN PACKETS
            case 7: loginUser(message); break;
            case 8: deleteFile(message); break;
            case 9: break;
            case 10: disconnectUser(); break;
            default: connections.send(connectionId, errorPacket(4)); break;
        } 
    }


    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

    private void loginUser(byte[] message){

        int usernameLength = message.length - 3;
        byte[] usernameInBytes = new byte[usernameLength];

        for(int i = 0; i < usernameInBytes.length; i++)
            usernameInBytes[i] = message[i + 2]; //Skip the opcode bytes

        String username = new String(usernameInBytes, StandardCharsets.UTF_8);

        if( connections.login(connectionId , username) ) { //Login succeeded
            connections.send(connectionId, genericAckPacket);
        }
        else { //Create and send error packet
            byte[] errorPacket = errorPacket(7); //User already logged in
            connections.send(connectionId, errorPacket);
        }
    }

    private void clientUploadRequest(byte[] message){
        
    }

    private void clientDownloadRequest(byte[] message) {

        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);
        FileInputStream fileAsStream;        

        try{
            fileAsStream = new FileInputStream("server\\Files" + filename);
            
            int dataPacketSize = 512 + 6;
            int blockNumber = 1;
            //Creating new DATA packet
            byte[] dataPacket = createDataPacket(blockNumber , dataPacketSize);

            byte nextByte;
            int index = 6;
            while( (nextByte = (byte) fileAsStream.read()) >= 0) {

                dataPacket[index] = nextByte;
                index++;

                if(index == dataPacket.length - 1) {
                    dataPacket[index] = 0;
                    connections.send(connectionId, dataPacket);
                    blockNumber++;
                    dataPacket = createDataPacket(blockNumber, dataPacketSize);
                    index = 0;
                }
            }

            if(index != 0) {
                int lastDataPacketSize = index + 2; //+2 because 1 extra byte for termination byte zero, and another one because index starts with zero, hence size is + 2.
                byte[] lastDataPacket = createDataPacket(blockNumber, lastDataPacketSize); 
                System.arraycopy(dataPacket, 0, lastDataPacket, 0, index + 1);
                lastDataPacket[lastDataPacketSize - 1] = 0;
                connections.send(connectionId, lastDataPacket);
            }

        } catch(FileNotFoundException e) {
            connections.send(connectionId, errorPacket(1));
        } catch(IOException ex) {
            ex.printStackTrace();
        }
    }
    
    private void deleteFile(byte[] message) {
        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);

        File fileToDelete = new File("server\\Files" + filename);

        if(!fileToDelete.exists())
            connections.send(connectionId, errorPacket(1));
        else {
            connections.send(connectionId, genericAckPacket);
            fileToDelete.delete();
            byte deleted = 0;
            broadcast(filename, deleted);
        }
    }   

    /**
     * This method handles disconnect of the current client from the server
     */
    private void disconnectUser() {
        if(connections.isLoggedIn(connectionId)) {
            connections.send(connectionId, genericAckPacket);
            shouldTerminate = true; //NOT 100% valid here - check later
            connections.disconnect(connectionId);
        }
        else {                                                 //User has not logged in yet
            connections.send(connectionId, errorPacket(6));
        }
    }

    /**
     * This method handles the packets of type DIRQ - lists the files in the server
     */
    private void directoryList() {
        List<String> filenames = listFilesInDirectory("server\\Files");
        List<Byte> data = new LinkedList<Byte>();
        String nextFilename;
        byte[] nextFilenameInBytes;
        byte separator = 0;

        while(!filenames.isEmpty()) {

            nextFilename = filenames.remove(0);
            nextFilenameInBytes = nextFilename.getBytes();

            for(int i = 0; i < nextFilenameInBytes.length; i++)
                data.add(nextFilenameInBytes[i]);

            data.add(separator);
        }

        short blockNumber = 1;
        short nextPacketSize;
        while(!data.isEmpty()) {

            //Determining data section size in the next packet
            if(data.size() >= 512)
                nextPacketSize = 512;
            else
                nextPacketSize = (short) data.size();

            //Creating new DATA packet
            byte[] dataPacket = new byte[nextPacketSize + 6];
            dataPacket[0] = 0;
            dataPacket[1] = 3; //DATA OP_CODE
            dataPacket[2] = (byte) (nextPacketSize >> 8); //Packet size - 2 bytes
            dataPacket[3] = (byte) (nextPacketSize & 0xff);
            dataPacket[4] = (byte) (blockNumber >> 8); //Block number - 2 bytes
            dataPacket[5] = (byte) (blockNumber & 0xff);
            

            for(int i = 6; i < dataPacket.length; i++) //Data section starts at index 6
                dataPacket[i] = data.remove(0);
            
            connections.send(connectionId, dataPacket);
            blockNumber++;
        }
    }

    /**
     * This method notifies all logged in clients about file deleted/added.
     * @param filename
     * @param deleted_added - 0 indicates deleted file, 1 indicates added file
     */
    public void broadcast(String filename, byte deleted_added) {

        byte[] filenameAsBytes = filename.getBytes();
        byte[] BCASTPacket = new byte[filenameAsBytes.length + 4];
        BCASTPacket[0] = 0;
        BCASTPacket[1] = 9;
        BCASTPacket[2] = deleted_added;
        System.arraycopy(filenameAsBytes, 0, BCASTPacket, 3, filenameAsBytes.length);
        connections.sendAll(BCASTPacket);
    }

    /**
     * This method creates and returns an error packet of certain error code
     * @param errorCodeInt - the error code
     * @return a valid error packet
     */
    private byte[] errorPacket(int errorCodeInt){

        String errMessage;
        byte errCode = (byte) errorCodeInt;

        switch (errCode) {
            case 0: errMessage = "Not defined"; break;
            case 1: errMessage = "File not found"; break;
            case 2: errMessage = "Access voilation"; break;
            case 3: errMessage = "Disk full or allocation exceeded"; break;
            case 4: errMessage = "Illegal TFTP operation"; break;
            case 5: errMessage = "File already exists"; break;
            case 6: errMessage = "User not logged in"; break;
            case 7: errMessage = "User already logged in"; break;        
            default: errMessage = null; break;
        }

        byte[] errMessageInBytes = errMessage.getBytes();
        byte[] errPacketBeginning = {0 , 5 , 0 , errCode};
        byte[] errPacket = new byte[errMessageInBytes.length + errPacketBeginning.length + 1];
        System.arraycopy(errPacketBeginning, 0, errPacket, 0, 4);
        System.arraycopy(errMessageInBytes, 0, errPacket, 4, errMessageInBytes.length);
        errPacket[errPacket.length - 1] = 0;
        return errPacket;
    }

    /**
     * @param dir - the directory to lookup
     * @return a set of all the file names in the specified directory
     */
    private List<String> listFilesInDirectory(String dir) {
    return Stream.of(new File(dir).listFiles())
      .filter(file -> !file.isDirectory())
      .map(File::getName)
      .collect(Collectors.toList());
    }
    

    private byte[] createDataPacket(int blockNumber, int packetSize) {

        byte[] dataPacket = new byte[packetSize];
        dataPacket[0] = 0;
        dataPacket[1] = 3; //DATA OP_CODE
        dataPacket[2] = (byte) (packetSize >> 8); //Packet size - 2 bytes
        dataPacket[3] = (byte) (packetSize & 0xff);
        dataPacket[4] = (byte) (blockNumber >> 8); //Block number - 2 bytes
        dataPacket[5] = (byte) (blockNumber & 0xff);
        return dataPacket;
    }
}
