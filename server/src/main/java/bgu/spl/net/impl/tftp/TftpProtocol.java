package bgu.spl.net.impl.tftp;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import bgu.spl.net.api.BidiMessagingProtocol;
import bgu.spl.net.srv.Connections;

public class TftpProtocol implements BidiMessagingProtocol<byte[]>  {

    private static final int DATA_PACKET_MAX_SIZE = 512;
    private static final int DATA_SECTION_BEGIN_INDEX = 6;

    private boolean shouldTerminate;
    private int connectionId;
    private Connections<byte[]> connections;
    private FileInputStream fileToDownloadFromServer;
    private FileOutputStream fileToUploadToServer;
    private short waitingForAckBlockNumber; //The ACK block number that the server is expecting.
    private List<Byte> directoryListingData;
    private boolean clientIsDownloading;


    // private final byte[] genericAckPacket = {0 , 4 , 0 , 0};

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        waitingForAckBlockNumber = -1;
        directoryListingData = new LinkedList<Byte>();
        clientIsDownloading = false;
    }

    @Override
    public void process(byte[] message) {

        byte op_code = message[1]; //The op code is represented by the first 2 bytes -
                                // first of them is zero according to the instructions

        switch (op_code) {
            case 1: clientDownloadRequest(message); break;
            case 2: clientUploadRequest(message); break;
            case 3: writeNextDataPacketIntoFile(message);
            case 4: ACKPacketHandling(message); break;
            case 5: break;
            case 6: directoryList(); break; //STILL NEED TO ADD ACKNOWLDEGMENT BETWEEN PACKETS
            case 7: loginUser(message); break;
            case 8: deleteFile(message); break;
            case 9: break;
            case 10: disconnectUser(); break;
            default: connections.send(connectionId, errorPacket(4)); break;
        } 
    }


    /**
     * Checks if the file 'message' exists - if it does breaks the file into DATA packets
     * and add them to the dataPacketsForClient. Upon receiving of ACK packet from client
     * the next DATA packet is sent.
     * @param message
     */
    private void clientDownloadRequest(byte[] message) {

        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);

        try{
            fileToDownloadFromServer = new FileInputStream("server\\Files" + filename);
        }
        catch(FileNotFoundException e){
            connections.send(connectionId, errorPacket(1));
            return;
        }
        
        waitingForAckBlockNumber = 0;
        clientIsDownloading = true;
        sendNextFilePacket();
    }


    private void clientUploadRequest(byte[] message){
        
        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);

        File fileToCreate = new File("server\\Files" + filename);

        if(fileToCreate.exists()) 
            connections.send(connectionId, errorPacket(5));
        else {
            try {
                fileToCreate.createNewFile();
                fileToUploadToServer = new FileOutputStream(fileToCreate);
            } catch(IOException e) { 
                e.printStackTrace(); 
            } 
            connections.send(connectionId, createACKPacket((short) 0));
        }
    }

    public void writeNextDataPacketIntoFile(byte[] packet) {

        int dataSectionSize = packet.length - DATA_SECTION_BEGIN_INDEX;

        byte[] dataToWriteToFile = new byte[dataSectionSize];

        System.arraycopy(packet, DATA_SECTION_BEGIN_INDEX, dataToWriteToFile, 0 , dataSectionSize);

        try {
            fileToUploadToServer.write(dataToWriteToFile);
            fileToUploadToServer.flush();
        } catch(IOException e) {
            e.printStackTrace();
            return;
        }

        short packetBlockNumber = (short) ( ((short) packet[2]) << 8 | (short) (packet[3]));
        connections.send(connectionId, createACKPacket(packetBlockNumber));

        if(dataSectionSize < DATA_PACKET_MAX_SIZE) {
            try {
                fileToUploadToServer.close();
            } catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void ACKPacketHandling(byte[] packet) {
        if(waitingForAckBlockNumber != -1) {
            short ACKBlockNumber = (short) ( ((short) packet[2]) << 8 | (short) (packet[3]));
            if(waitingForAckBlockNumber == ACKBlockNumber) {
                if(clientIsDownloading)
                    sendNextFilePacket();
                else
                    sendNextDirectoryListPackets();
            }
        }
    }

    public void sendNextFilePacket() {

        waitingForAckBlockNumber++;

        byte[] dataPacket = createEmptyDataPacket(waitingForAckBlockNumber, DATA_PACKET_MAX_SIZE);
        int numOfBytesRead = -1;
        try {
            numOfBytesRead = fileToDownloadFromServer.read(dataPacket, DATA_SECTION_BEGIN_INDEX, DATA_PACKET_MAX_SIZE);
        } catch(IOException e) {
            e.printStackTrace();
        }

        if( numOfBytesRead == DATA_PACKET_MAX_SIZE ) 
            connections.send(connectionId, dataPacket);
        
        else if(numOfBytesRead > 0) {
            byte[] lastDataPacket = createEmptyDataPacket(waitingForAckBlockNumber, numOfBytesRead);
            System.arraycopy(dataPacket, DATA_SECTION_BEGIN_INDEX, lastDataPacket, DATA_SECTION_BEGIN_INDEX , numOfBytesRead);
            connections.send(connectionId, lastDataPacket);
        }
        else {
            waitingForAckBlockNumber = -1;
            clientIsDownloading = false;
            // broadcast(filename, (byte) 1);

            try {
                fileToDownloadFromServer.close();
            } catch(IOException e) {
                e.printStackTrace();
            }
        }
    }


    /**
     * This method handles the packets of type DIRQ - lists the files in the server
     */
    private void directoryList() {
        List<String> filenames = listFilesInDirectory("server\\Files");
        String nextFilename;
        byte[] nextFilenameInBytes;
        byte separator = 0;

        while(!filenames.isEmpty()) {

            nextFilename = filenames.remove(0);
            nextFilenameInBytes = nextFilename.getBytes();

            for(int i = 0; i < nextFilenameInBytes.length; i++)
                directoryListingData.add(nextFilenameInBytes[i]);

            directoryListingData.add(separator);
        }

        waitingForAckBlockNumber = 0;
        sendNextDirectoryListPackets();
    }

    private void sendNextDirectoryListPackets() {

        waitingForAckBlockNumber++;

        if(!directoryListingData.isEmpty()) {

            int dataSectionSize;
            if(directoryListingData.size() < DATA_PACKET_MAX_SIZE)
                dataSectionSize = directoryListingData.size();
            else
                dataSectionSize = DATA_PACKET_MAX_SIZE;
    
            byte[] dataPacket = createEmptyDataPacket(waitingForAckBlockNumber, dataSectionSize);
            for(int i = DATA_SECTION_BEGIN_INDEX; i < dataPacket.length; i++)
                dataPacket[i] = directoryListingData.remove(0);
            
            connections.send(connectionId, dataPacket);
        }
        else
            waitingForAckBlockNumber = -1;
    }


    private void loginUser(byte[] message){

        int usernameLength = message.length - 3;
        byte[] usernameInBytes = new byte[usernameLength];

        for(int i = 0; i < usernameInBytes.length; i++)
            usernameInBytes[i] = message[i + 2]; //Skip the opcode bytes

        String username = new String(usernameInBytes, StandardCharsets.UTF_8);

        if( connections.login(connectionId , username) ) { //Login succeeded
            connections.send(connectionId, createACKPacket((short) 0));
        }
        else { //Create and send error packet
            byte[] errorPacket = errorPacket(7); //User already logged in
            connections.send(connectionId, errorPacket);
        }
    }

     
    private void deleteFile(byte[] message) {
        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);

        File fileToDelete = new File("server\\Files" + filename);

        if(!fileToDelete.exists())
            connections.send(connectionId, errorPacket(1));
        else {
            connections.send(connectionId, createACKPacket((short) 0));
            fileToDelete.delete();
            byte deleted = 0;
            broadcast(filename, deleted);
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
     * This method handles disconnect of the current client from the server
     */
    private void disconnectUser() {
        if(connections.isLoggedIn(connectionId)) {
            connections.send(connectionId, createACKPacket((short) 0));
            shouldTerminate = true; //NOT 100% valid here - check later
            connections.disconnect(connectionId);
        }
        else {                                                 //User has not logged in yet
            connections.send(connectionId, errorPacket(6));
        }
    }


    
    //=================================HELPER METHODS==================================

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
        byte[] errPacketBeginning = { 0 , 5 , 0 , errCode};
        byte[] errPacket = new byte[errMessageInBytes.length + errPacketBeginning.length + 1];
        System.arraycopy(errPacketBeginning, 0, errPacket, 0, 4);
        System.arraycopy(errMessageInBytes, 0, errPacket, 4, errMessageInBytes.length);
        errPacket[errPacket.length - 1] = 0;
        return errPacket;
    }

    private byte[] createEmptyDataPacket(int blockNumber, int dataSectionSize) {

        byte[] dataPacket = new byte[dataSectionSize + DATA_SECTION_BEGIN_INDEX];
        dataPacket[0] = 0;
        dataPacket[1] = 3; //DATA OP_CODE
        dataPacket[2] = (byte) (dataSectionSize >> 8); //Packet size - 2 bytes
        dataPacket[3] = (byte) (dataSectionSize & 0xff);
        dataPacket[4] = (byte) (blockNumber >> 8); //Block number - 2 bytes
        dataPacket[5] = (byte) (blockNumber & 0xff);
        return dataPacket;
    }

    private byte[] createACKPacket(short blockNumber) {
        byte[] blockNumberAsBytes = new byte[] { (byte) (blockNumber >> 8) , (byte) (blockNumber & 0xff) };
        byte[] ACKPacket = {0 , 3 , blockNumberAsBytes[0] , blockNumberAsBytes[1] };
        return ACKPacket;
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

    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

}
