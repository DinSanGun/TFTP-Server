package bgu.spl.net.srv;

// import java.io.IOException;

public interface Connections<T> {

    boolean connect(int connectionId, ConnectionHandler<T> handler);

    boolean send(int connectionId, T msg);

    void disconnect(int connectionId);

    int getNewConnectionId();

    boolean login(int connectionId , String username);

    boolean isLoggedIn(int connectionId);

    void sendAll(T msg);


    // Integer[] getConnectionIds();
}
