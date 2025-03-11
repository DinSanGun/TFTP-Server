# TFTP Server and Client

## Overview
This project is an implementation of an extended Trivial File Transfer Protocol (TFTP) server and client. The TFTP server allows multiple users to upload, download, and manage files while providing real-time notifications when files are added or deleted. The communication between the server and clients follows a binary protocol over TCP.

## Features
- Supports file upload and download
- Directory listing of server files
- User authentication with unique usernames
- Broadcast notifications for file additions and deletions
- Error handling with descriptive messages
- Uses a Thread-Per-Client (TPC) server pattern

## Installation
### Prerequisites
- Java 8 or later
- Maven

### Build Instructions
#### Server
```sh
cd server/
mvn compile
```
#### Client
```sh
cd client/
mvn compile
```

## Usage
### Running the Server
```sh
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpServer" -Dexec.args="<port>"
```

### Running the Client
```sh
mvn exec:java -Dexec.mainClass="bgu.spl.net.impl.tftp.TftpClient" -Dexec.args="<ip> <port>"
```

### Available Commands
#### 1. Login
```sh
LOGRQ <Username>
```
Logs in the user to the server.

#### 2. Upload a File
```sh
WRQ <Filename>
```
Uploads a file from the client to the server.

#### 3. Download a File
```sh
RRQ <Filename>
```
Downloads a file from the server to the client.

#### 4. List Directory
```sh
DIRQ
```
Lists all files stored on the server.

#### 5. Delete a File
```sh
DELRQ <Filename>
```
Deletes a file from the server.

#### 6. Disconnect
```sh
DISC
```
Disconnects the client from the server.

## Packet Structure
The protocol supports the following packet types:
- `RRQ` (Read Request)
- `WRQ` (Write Request)
- `DATA` (File Data Transfer)
- `ACK` (Acknowledgment)
- `ERROR` (Error Message)
- `DIRQ` (Directory Query)
- `LOGRQ` (Login Request)
- `DELRQ` (Delete Request)
- `BCAST` (Broadcast Notification)
- `DISC` (Disconnect Request)

## Example Usage
### 1. File Download
```
< LOGRQ John
> ACK 0
< RRQ example.txt
> ACK 1
> ACK 2
> RRQ example.txt complete
< DISC
> ACK 0
```

### 2. File Upload
```
< LOGRQ Alice
> ACK 0
< WRQ newfile.txt
> ACK 0
> ACK 1
> ACK 2
> WRQ newfile.txt complete
> BCAST add newfile.txt
< DISC
> ACK 0
```

## Error Handling
The protocol provides error messages for various conditions:
- File not found
- Access violation
- Disk full
- Unknown opcode
- File already exists
- User not logged in
- User already logged in

## Project Structure
```
TFTP-Server/
├── client/
│   ├── src/
│   ├── pom.xml
├── server/
│   ├── src/
│   ├── Files/  # Storage folder
│   ├── pom.xml
```

## Authors
- Din Yair Sadot

## License
This project is for educational purposes and follows BGU course assignment guidelines.

