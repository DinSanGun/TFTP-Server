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
    private String uploadFileName;
    private String username;

    @Override
    public void start(int connectionId, Connections<byte[]> connections) {
        shouldTerminate = false;
        this.connectionId = connectionId;
        this.connections = connections;
        waitingForAckBlockNumber = -1;
        directoryListingData = new LinkedList<Byte>();
        clientIsDownloading = false;
        uploadFileName = null;
    }

    @Override
    public void process(byte[] message) {

        byte op_code = message[1]; //The op code is represented by the first 2 bytes -
                                // first of them is zero according to the instructions

        if(op_code != 7 && !connections.isLoggedIn(connectionId)) { //User not logged in and trying to make requests to server
                connections.send(connectionId, errorPacket(6));
                System.out.println("Unknown client is trying to reach the server (ERROR-6)");
        }

        else {

            switch (op_code) {
                case 1: clientDownloadRequest(message); break;
                case 2: clientUploadRequest(message); break;
                case 3: writeNextDataPacketIntoFile(message);
                case 4: ACKPacketHandling(message); break;
                case 5: break;
                case 6: directoryList(); break; 
                case 7: loginUser(message); break;
                case 8: deleteFile(message); break;
                case 9: break;
                case 10: disconnectUser(); break;
                default: connections.send(connectionId, errorPacket(4)); break;
            } 
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

        System.out.println("Client " + username + " asks to download: " + filename);

        try{
            fileToDownloadFromServer = new FileInputStream("Files/" + filename);
        }
        catch(FileNotFoundException e){
            connections.send(connectionId, errorPacket(1));
            return;
        }

        System.out.println("Client " + username + " starts downloading");
        
        waitingForAckBlockNumber = 0;
        clientIsDownloading = true;
        sendNextFilePacket();
    }


    private void clientUploadRequest(byte[] message){
        
        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);

        System.out.println("Client " + username + " asks to upload: " + filename);

        File fileToCreate = new File("Files/" + filename);

        if(fileToCreate.exists()) {
            connections.send(connectionId, errorPacket(5));
            System.out.println("File - " + filename + " - does not exist in the server");
        }
        else {
            try {
                fileToCreate.createNewFile();
                fileToUploadToServer = new FileOutputStream(fileToCreate);
            } catch(IOException e) { 
                e.printStackTrace(); 
            } 
            uploadFileName = filename;
            connections.send(connectionId, createACKPacket((short) 0));
            System.out.println("Client " + username + " is uploading the file: " + filename);
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

        short packetBlockNumber = (short) ( ((short) packet[4]) << 8 | (short) (packet[5]) & 0xff);
        System.out.println("Block number " + packetBlockNumber + " received");
        connections.send(connectionId, createACKPacket(packetBlockNumber));

        if(dataSectionSize < DATA_PACKET_MAX_SIZE) { //It means this is the last data packet
            System.out.println("Upload of" + uploadFileName + " has completed");
            broadcast(uploadFileName, (byte) 1);
            uploadFileName = null;
            try {
                fileToUploadToServer.close();
            } catch(IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public void ACKPacketHandling(byte[] packet) {
        if(waitingForAckBlockNumber != -1) {
            short ACKBlockNumber = (short) ( ((short) packet[2]) << 8 | (short) (packet[3]) & 0xff);
            if(waitingForAckBlockNumber == ACKBlockNumber) {
                System.out.println("Client " + username + " has received packet #" + ACKBlockNumber);
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
            System.out.println("Client " + username + " finished downloading file");

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
        List<String> filenames = listFilesInDirectory("Files");
        String nextFilename;
        byte[] nextFilenameInBytes;
        byte separator = 0;

        System.out.println("Client " + username + " asked for a directory list");

        while(!filenames.isEmpty()) {

            nextFilename = filenames.remove(0);
            nextFilenameInBytes = nextFilename.getBytes(StandardCharsets.UTF_8);

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

        System.arraycopy(message,2,usernameInBytes,0,usernameLength); //Skips the opcode bytes

        String username = new String(usernameInBytes, StandardCharsets.UTF_8);
        System.out.println(username + " is trying to connect");

        if( connections.login(connectionId , username) ) { //Login succeeded
            System.out.println(username + " is connected");
            connections.send(connectionId, createACKPacket((short) 0));
            this.username = username;
        }
        else { //Create and send error packet
            System.out.println(username + " failed to connect");
            connections.send(connectionId, errorPacket(7)); //User already logged in
        }
    }

     
    private void deleteFile(byte[] message) {
        byte[] filenameInBytes = Arrays.copyOfRange(message, 2, message.length - 1);
        String filename = new String(filenameInBytes , StandardCharsets.UTF_8);

        System.out.println("Client " + username + " requesting to delete: " + filename);

        File fileToDelete = new File("Files/" + filename);

        if(!fileToDelete.exists())
            connections.send(connectionId, errorPacket(1));
        else {
            connections.send(connectionId, createACKPacket((short) 0));
            fileToDelete.delete();
            byte deleted = 0;
            broadcast(filename, deleted);
            System.out.println("File " + filename + " was deleted");
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
        System.out.println("A broadcast message has been sent to all active clients");
    }

    /**
     * This method handles disconnect of the current client from the server
     */
    private void disconnectUser() {
        if(connections.isLoggedIn(connectionId)) {
            connections.send(connectionId, createACKPacket((short) 0));
            shouldTerminate = true;
            connections.disconnect(connectionId);
            System.out.println("Client " + username + " has disconnected");
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
        byte[] ACKPacket = new byte[4];
        ACKPacket[0] = (byte) 0;
        ACKPacket[1] = (byte) 4;
        ACKPacket[2] = blockNumberAsBytes[0];
        ACKPacket[3] = blockNumberAsBytes[1];

        return ACKPacket;
    }

    /**
     * @param dir - the directory to lookup
     * @return a set of all the file names in the specified directory
     */
    private List<String> listFilesInDirectory(String dir) {

        File folder = new File(dir);
        File[] listOfFiles = folder.listFiles();
        List<String> filenames = new LinkedList<String>();

        for (int i = 0; i < listOfFiles.length; i++) {
            if (listOfFiles[i].isFile()) 
                filenames.add( listOfFiles[i].getName() );
        }
        return filenames;
    }


    @Override
    public boolean shouldTerminate() {
        return shouldTerminate;
    } 

}
