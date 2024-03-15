package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    private ConcurrentHashMap<Integer, ConnectionHandler<T>> activeClients = new ConcurrentHashMap<>();
    private ConcurrentHashMap<Integer, String> loggedInUsers = new ConcurrentHashMap<>();
    private int connectionIdGenerator = 1;

    @Override
    public boolean connect(int connectionId, ConnectionHandler<T> handler){
        return activeClients.put( connectionId , handler ) == null;
    }

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

    @Override
    public void disconnect(int connectionId){
        activeClients.remove(connectionId);
        loggedInUsers.remove(connectionId);
    }

    @Override
    public int getNewConnectionId(){
        return ++connectionIdGenerator;
    }

    @Override
    public boolean login(int connectionId , String username) {
        if(loggedInUsers.contains(username))
            return false;

        loggedInUsers.put(connectionId , username);
        return true;
    }

    public boolean isLoggedIn(int connectionId) {
        return loggedInUsers.containsKey(connectionId);
    }

    public void sendAll(T msg) {

        for(Integer clientId : loggedInUsers.keySet()) {

            activeClients.get(clientId).send(msg);
        }
    }




    // /**
    //  * @return an array of the active clients' connection ids
    //  */
    // @Override
    // public Integer[] getConnectionIds(){
    //     return (Integer[]) activeClients.keySet().toArray();
    // }
}
