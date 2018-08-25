package SessionManagement;

import JGroupCommunication.MessageType;
import JGroupCommunication.SSCMsgIDHeader;
import JGroupCommunication.SSCSessionIDHeader;
import SSLibrary.ExtendedSessionInfo;
import SSLibrary.SessionInfo;
import org.jgroups.Message;

public class AdversaryManager extends SessionManager {

  public AdversaryManager() {
    super();
    myChannel.setMessageReceiveListener(this);
    currentSessionId = null;
  }

  @Override
  public void onMessageReceived(Message message) {

    Integer msgID = ((SSCMsgIDHeader) message.getHeader((short) 1821)).getMsgID();
    Integer sessionID = ((SSCSessionIDHeader) message.getHeader((short) 1822)).getSessionID();

    String msgText = "Received a new broadcast message...\n";
    msgText += "Sender: " + message.getSrc().toString() + "\n";
    msgText += "Session ID: " + sessionID + "\n";
    msgText += "Message ID: " + MessageType.forValue(msgID) + "\n";

    if (message.getObject() != null) {

      if (message.getObject().getClass().equals(ExtendedSessionInfo.class)) {
        SessionInfo info = message.getObject();

        msgText += "Message object: session info \n";
        msgText += "Modulo: " + info.getModulo() + "\n";
        //msgText += "Commitment Triple: g = "+info.getCommitmentTriple().getgFactor() + "; h = "+info.getCommitmentTriple().gethFactor()+ "; modulo = "+info.getCommitmentTriple().getModulo()+"\n";
        msgText += "Number of players: " + info.getNumberOfPlayers() + "\n";
        msgText += "Number of combiners: " + info.getNumberOfCombiners() + "\n";
      } else {
        msgText += "Message object: " + message.getObject().toString() + "\n";
      }
    }

    informUser(msgText, false);
  }
}
