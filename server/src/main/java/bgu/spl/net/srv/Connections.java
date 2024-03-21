package bgu.spl.net.srv;

public interface Connections<T> {

    /**
     * A method that adds a client to the active client's mapping.
     * @return true iff client was added to active client's map succesfully.
     */
    boolean connect(int connectionId, ConnectionHandler<T> handler);

    /**
     * A method that sends a message from the server to a specific client.
     * @return true iff message was sent.
     */
    boolean send(int connectionId, T msg);

    /**
     * A method that disconnects a client from the server - removes
     * it from active clients map, and from logged in users.
     */
    void disconnect(int connectionId);


    /**
     * A method that generates a new connectionId for a client.
     * @return a new unique connection id.
     */
    int getNewConnectionId();


    /**
     * A method that implements a user login to the server.
     * @return true iff use has logged in succesfully.
     */
    boolean login(int connectionId , String username);

    /**
     * @return true iff Client with this connection id has already logged in to the server.
     */
    boolean isLoggedIn(int connectionId);

    /**
     * A method that is used for broadcasting messages from the server 
     * to all active logged-in clients.
     */
    void sendAll(T msg);
}
