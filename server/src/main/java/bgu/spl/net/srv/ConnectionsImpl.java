package bgu.spl.net.srv;

import java.util.concurrent.ConcurrentHashMap;

public class ConnectionsImpl<T> implements Connections<T> {

    // private List<ConnectionHandler<T>> activeClients = new LinkedList<ConnectionHandler<T>>();
    private ConcurrentHashMap<Integer, ConnectionHandler<T>> activeClients = new ConcurrentHashMap<>();


    @Override
    public void connect(int connectionId, ConnectionHandler<T> handler){
        activeClients.put( connectionId , handler );
    }

    @Override
    public boolean send(int connectionId, T msg){ //USE ConnectionHandler.send method
        
        ConnectionHandler<T> handler = activeClients.get(connectionId);
        if(handler == null)
            return false;

        handler.send(msg);
        return true;
    }

    @Override
    public void disconnect(int connectionId){
        activeClients.remove(connectionId);
    }
    
}
