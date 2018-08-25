package JGroupCommunication;

import org.jgroups.Message;

public interface MessageReceiveListener {
    void onMessageReceived(Message message) throws Exception;

}
