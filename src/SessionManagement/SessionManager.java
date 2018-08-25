package SessionManagement;

import JGroupCommunication.JGroupChannel;
import JGroupCommunication.MessageReceiveListener;
import JGroupCommunication.MessageType;
import JGroupCommunication.SSCMsgIDHeader;
import JGroupCommunication.SSCSessionIDHeader;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.conf.ClassConfigurator;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;

public class SessionManager implements MessageReceiveListener {

  JGroupChannel                myChannel;
  HashMap<BigInteger, Address> participantsAddresses;
  HashMap<BigInteger, Address> combinerAddresses;
  private SessionRestartListener sessionRestartListener;
  Integer currentSessionId;

  SessionManager() {
    String _initialClusterName = "SSC:Cluster";

      myChannel = new JGroupChannel(_initialClusterName);
      myChannel.createChannel();


    participantsAddresses = new HashMap<>();
    combinerAddresses = new HashMap<>();
    currentSessionId = null;

    try {
      if (ClassConfigurator.getMagicNumber(SSCMsgIDHeader.class) != (short) 1821) {
        ClassConfigurator.add((short) 1821, SSCMsgIDHeader.class);
      }

      if (ClassConfigurator.getMagicNumber(SSCSessionIDHeader.class) != (short) 1821) {
        ClassConfigurator.add((short) 1822, SSCSessionIDHeader.class);
      }
    } catch (Exception exception) {
      System.out.println("Strange exception happens here!");
    }
  }

  public void setSessionRestartListener(SessionRestartListener sessionRestartListener) {
    this.sessionRestartListener = sessionRestartListener;
  }

  private void removeSessionRestartListener() {
    sessionRestartListener = null;
  }

  String informUser(String infoText, boolean responseExpected) {
    if (!responseExpected) {
      System.out.println(LocalDateTime.now() + " SSC-INFO: " + infoText);
      return null;
    }

    System.out.println("SSC-QUESTION: " + infoText);
    Scanner _scanner = new Scanner(System.in);
    return _scanner.nextLine();
  }

  void cancelSessionOnException(String reason, boolean sendMessage) throws Exception {
    String msgText = String.format("Session cancelled! Reason: %s", reason);
    informUser(msgText, false);

    if (sendMessage) {
      myChannel.sendMessage(
          reason,
          null,
          MessageType.CancelSession.getMessageID(),
          currentSessionId);
    }

    Thread.sleep(1000);
    myChannel.closeConnection();

    if (sessionRestartListener != null) {
      sessionRestartListener.onSessionCancelled();
    }

    removeSessionRestartListener();
  }

  void finishSessionOnReconstruction(boolean isCombiner, BigInteger reconstructedValue)
      throws Exception {
    String msgText = "Session ended, because reconstruction succeeded!";
    informUser(msgText, false);

    if (isCombiner && reconstructedValue != null) {
      msgText = String.format("Reconstructed secret: %s", reconstructedValue);
      informUser(msgText, false);
    }

    TimeUnit.SECONDS.sleep(2);
    myChannel.closeConnection();

    if (sessionRestartListener != null) {
      sessionRestartListener.onSessionCancelled();
    }

    removeSessionRestartListener();
  }

  void finishSessionAsPlayer() throws Exception {
    String msgText =
        "Session ended, because this player is not needed anymore! Reconstruction phase of combiners starts...";
    informUser(msgText, false);

    TimeUnit.SECONDS.sleep(2);
    myChannel.closeConnection();

    if (sessionRestartListener != null) {
      sessionRestartListener.onSessionCancelled();
    }

    removeSessionRestartListener();
  }

  @Override
  public void onMessageReceived(Message message) throws Exception {

  }
}
