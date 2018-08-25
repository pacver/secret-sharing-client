package JGroupCommunication;

import org.jgroups.Address;
import org.jgroups.JChannel;
import org.jgroups.Message;
import org.jgroups.Receiver;
import org.jgroups.protocols.*;
import org.jgroups.protocols.pbcast.GMS;
import org.jgroups.protocols.pbcast.NAKACK2;
import org.jgroups.protocols.pbcast.STABLE;
import org.jgroups.stack.Protocol;
import java.net.InetAddress;
import java.net.UnknownHostException;

public class JGroupChannel implements Receiver {

    private Protocol[] _properties;
    private final String _clusterName;
    private JChannel _channel;
    private Address _myAddress;
    private MessageReceiveListener _messageReceiveListener;

    public JGroupChannel(String clusterName) {
        _clusterName = clusterName;
      try {
        _properties = new Protocol[]{
           new UDP().setValue("bind_addr", InetAddress.getByName("127.0.0.1")), // ersetzen durch "new UDP()" um's im gesamten Netzwerk betreiben zu können
           new PING(),
           new MERGE3().setMinInterval(3000).setMaxInterval(5000),
           new FD_SOCK(),
           new FD_ALL(),
           new VERIFY_SUSPECT(),
           new BARRIER(),
           new NAKACK2(),
           new UNICAST3(),
           new STABLE(),
           new GMS(),
           new UFC(),
           new MFC(),
           new FRAG2()};
      } catch (UnknownHostException e) {
        System.out.println("Unknown Host in JGroupChannel constructor");
        e.printStackTrace();
      }
    }

    public Address getMyAddress() {
        return _myAddress;
    }

    public void setMessageReceiveListener(MessageReceiveListener listener)
    {
        _messageReceiveListener = listener;
    }

    public void createChannel(){
      try {
        _channel = new JChannel(_properties);
      } catch (Exception e) {
        try {
          Thread.sleep(100);
          _channel = new JChannel(_properties);
        } catch (Exception e1) {
          System.out.println("JGroupChannel.createChannel().new() failed");
          e1.printStackTrace();
        }
      }
      try {
        _channel.connect(_clusterName);
        Thread.sleep(100);
      } catch (Exception e) {
        try {
          _channel.connect(_clusterName);
        } catch (Exception e1) {
          System.out.println("JGroupChannel.createChannel().connect failed");
          e1.printStackTrace();
        }
      }
      _myAddress = _channel.getAddress();
        _channel.setReceiver(this);
    }

    public void sendMessage(String messageText, Address destAddress, Integer msgID, Integer sessionID) throws Exception {
        Message message = new Message(destAddress,messageText);
        message.setSrc(_channel.address());
        message.putHeader((short)1821, new SSCMsgIDHeader(msgID));
        message.putHeader((short)1822,new SSCSessionIDHeader(sessionID));
        _channel.send(message);
    }

    public void sendMessage(Message message, Integer msgID, Integer sessionID) throws Exception {
        message.setSrc(_myAddress);
        message.putHeader((short) 1821,new SSCMsgIDHeader(msgID));
        message.putHeader((short)1822,new SSCSessionIDHeader(sessionID));
        message.setObject(MessageEncryption.getEncryptedMessageObject(message.getObject()));
        _channel.send(message);
    }

    public void receive(Message message) {
        //Ignore self send messages
        if(message.getSrc() == _myAddress)
            return;

        message.setObject(MessageEncryption.getDecryptedMessage(message.getObject()));

        try {
            if(_messageReceiveListener != null)
                _messageReceiveListener.onMessageReceived(message);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void removeMessageReceiveListener()
    {
        _messageReceiveListener = null;
    }

    public void closeConnection()
    {
        removeMessageReceiveListener();
        _channel.disconnect();
        _channel.close();
    }
}
