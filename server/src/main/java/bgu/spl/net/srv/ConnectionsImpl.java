package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> activeClients = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> loggedInUsers = new ConcurrentHashMap<>();
    private int connectionIdGenerator = 1;

    /**
     * A method that adds a client to the activeClient's map.
     * @return true iff client has been added succesfully.
     */
    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler){
        return activeClients.put( connectionId , handler ) == null;
    }

    /**
     * A method that sends a message from the server to a specific client.
     * @return true iff message was sent.
     */
    @Override
    public boolean send(int connectionId, T msg){ //USE ConnectionHandler.send method
        
        if(activeClients.containsKey(connectionId)) {
            ConnectionHandler<T> handler = activeClients.get(connectionId);
            
            if(handler == null)
                return false;
            
            handler.send(msg);
            return true;
        }

        return false;
    }

    /**
     * A method that disconnects a client from the server - removes
     * him from activeClients map, and from logged in users.
     */
    @Override
    public void disconnect(int connectionId){
        activeClients.remove(connectionId);
        loggedInUsers.remove(connectionId);
    }

    /**
     * A method that generates a new connectionId for a client.
     * @return a new unique connection id.
     */
    @Override
    public int getNewConnectionId(){
        return ++connectionIdGenerator;
    }


    /**
     * A method that implements a user login to the server.
     * @return true iff use has logged in succesfully.
     */
    @Override
    public boolean login(int connectionId , String username) {
        if(loggedInUsers.contains(username))
            return false;

        loggedInUsers.put(connectionId , username);
        return true;
    }

    /**
     * @return true iff Client with this connection id has already logged in to the server.
     */
    public boolean isLoggedIn(int connectionId) {
        return loggedInUsers.containsKey(connectionId);
    }

    /**
     * A method that is used for broadcasting messages from the server to all clients.
     */
    public void sendAll(T msg) {

        for(Integer clientId : loggedInUsers.keySet()) {

            activeClients.get(clientId).send(msg);
        }
    }
}
