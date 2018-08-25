package SessionManagement;

import JGroupCommunication.MessageType;
import JGroupCommunication.SSCMsgIDHeader;
import JGroupCommunication.SSCSessionIDHeader;
import SSLibrary.MultipartyOperationObject;
import SSLibrary.Operation;
import SSLibrary.Participant;
import SSLibrary.SSLibException;
import SSLibrary.SessionInfo;
import TimerHelper.SSCTimer;
import TimerHelper.TimeoutListener;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.util.Util;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.math.BigInteger;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class ParticipatingManager extends SessionManager implements TimeoutListener {

  private       ParticipatingSessionState currentSessionState;
  private       Participant               myself;
  private       SessionInfo               currentSessionInfo;
  private       SSCTimer                  timer;
  private       int                       receivedShareCount;
  private       int                       receivedAckCount;
  private       Message                   lastMessageToAcknowledge;
  private       Operation                 currentOperation;
  private       BigInteger                secretId;
  private final long                      timeoutValue = 120; // sekunden

  public ParticipatingManager() {
    super();
    myChannel.setMessageReceiveListener(this);
    currentSessionState = ParticipatingSessionState.WaitForNewSession;
    currentSessionId = null;
  }

  @Override
  public void onMessageReceived(Message message) throws Exception {

    Integer msgID = ((SSCMsgIDHeader) message.getHeader((short) 1821)).getMsgID();
    Integer sessionID = ((SSCSessionIDHeader) message.getHeader((short) 1822)).getSessionID();

    //Ignore messages from other sessions
    if (currentSessionId != null && !sessionID.equals(currentSessionId)) {
      return;
    }

    switch (MessageType.forValue(msgID)) {
      case NewSession:
        if (currentSessionState == ParticipatingSessionState.WaitForNewSession) {
          processNewSessionMsg(message);
        }
        break;
      case IDTransfer:
        receivedID(message);
        break;
      case SessionObjectTransfer:
        receivedSessionInfo(message);
        break;
      case MultipartyOperationTransfer:
        receivedShareObject(message);
        break;
      case CommitmentObjectsTransfer:
        receivedCommitments(message);
        break;
      case IntermediateAddressTransfer:
        receivedPlayerAddress(message);
        break;
      case IntermediateCalculationFlag:
        startIntermediateSharingBetweenPlayers(message);
        break;
      case IntermediateOthersShare:
        addOthersIntermediateSecretShare(message);
        if (receivedAckCount == participantsAddresses.size() - 1
            && receivedShareCount == participantsAddresses.size() - 1) {
          timer.startTimerTaskImmediately();
        }
        break;
      case CombinerAddressTransfer:
        receivedCombinerAddress(message);
        if (receivedAckCount == combinerAddresses.size() - 1
            && receivedShareCount == combinerAddresses.size() - 1) {
          timer.startTimerTaskImmediately();
        }
        break;
      case ReconstructionFlag:
        if (!myself.IsCombiner()) {
          currentSessionState = ParticipatingSessionState.Finished;
          stopTimerAndResetAck();
          finishSessionAsPlayer();
        } else {
          startSharingBetweenCombiners(message);
        }
        break;
      case OthersShare:
        addOthersSecretShare(message);
        if (receivedAckCount == combinerAddresses.size() - 1
            && receivedShareCount == combinerAddresses.size() - 1) {
          timer.startTimerTaskImmediately();
        }
        break;
      case SessionFinish:
        finishSessionOnReconstruction(false, null);
        break;
      case CancelSession:
        cancelSessionOnException(message.getObject().toString(), false);
        break;
      case Acknowledge:
        // informUser("received ACK...",false);
        receivedAckCount++;

        if (currentSessionState == ParticipatingSessionState.WaitForIntermediateOthersShare) {
          if (receivedAckCount == participantsAddresses.size() - 1
              && receivedShareCount == participantsAddresses.size() - 1) {
            timer.startTimerTaskImmediately();
          }
        } else if (receivedAckCount == combinerAddresses.size() - 1
                   && receivedShareCount == combinerAddresses.size() - 1) {
          timer.startTimerTaskImmediately();
        }
        break;
      default:
        throw new Exception("Unknown message received: " + MessageType.forValue(msgID));
    }
  }

  private void addOthersIntermediateSecretShare(Message message) throws Exception {

    String[] splits = message.getObject().toString().split(";");
    BigInteger id = BigInteger.valueOf(Long.parseLong(splits[0]));
    BigInteger secretShare = BigInteger.valueOf(Long.parseLong(splits[1]));

    myself.receiveShare(id, secretShare);
    informUser(String.format("Received intermediate share from player %s...", id), false);
    acknowledgeMessage(message, "");

    receivedShareCount++;
  }

  private void addOthersSecretShare(Message message) throws Exception {

    acknowledgeMessage(message, "");
    String[] splits = message.getObject().toString().split(";");
    BigInteger id = BigInteger.valueOf(Long.parseLong(splits[0]));
    BigInteger secretShare = BigInteger.valueOf(Long.parseLong(splits[1]));

    if (splits.length == 2) {
      myself.receiveShare(id, secretShare);
    } else {
      String rndString = splits[2];

      if (rndString.equals("null")) {
        myself.receiveShare(id, secretShare, null);
      } else {
        BigInteger rndShare = BigInteger.valueOf(Long.parseLong(rndString));
        myself.receiveShare(id, secretShare, rndShare);
      }
    }

    informUser(String.format("Received share from combiner %s ...", id), false);
    receivedShareCount++;
  }

  private void startSharingBetweenCombiners(Message message) throws Exception {

    startTimer(ParticipatingSessionState.WaitForOthersShare);
    lastMessageToAcknowledge = message;
    informUser("Start sharing between combiners...", false);
    message = new Message();

    String messageText;
    if (currentOperation == Operation.Product) {
      messageText = myself.getSecretId() + ";" + myself.distributeSecretShare();
    } else {
      messageText = myself.getSecretId() + ";" + myself.distributeSecretShare() + ";"
                    + myself.distributeRndShare();
    }

    for (Map.Entry<BigInteger, Address> dest : combinerAddresses.entrySet()) {

      //Do not send messages to client itself
      if (myChannel.getMyAddress().compareTo(dest.getValue()) != 0) {
        message.setObject(messageText);
        message.dest(dest.getValue());
        myChannel.sendMessage(message, MessageType.OthersShare.getMessageID(), currentSessionId);
        informUser("Send share to combiner...", false);
      }
    }
  }

  private void startIntermediateSharingBetweenPlayers(Message message) throws Exception {

    startTimer(ParticipatingSessionState.WaitForIntermediateOthersShare);
    lastMessageToAcknowledge = message;

    readKeysToHashMap(message.getObject());

    informUser("Start intermediate sharing between players...", false);
    message = new Message();

    for (Map.Entry<BigInteger, Address> dest : participantsAddresses.entrySet()) {

      //Do not send messages to client itself
      if (myChannel.getMyAddress().compareTo(dest.getValue()) != 0) {

        String messageText = myself.getSecretId().toString() + ";"
                             + myself.distributeSecretShareForSpecificParticipant(dest.getKey())
                                 .toString();
        message.setObject(messageText);
        message.dest(dest.getValue());
        myChannel.sendMessage(
            message,
            MessageType.IntermediateOthersShare.getMessageID(),
            currentSessionId);
        informUser("Send intermediate share to player...", false);
      }
    }
  }

  private void receivedCombinerAddress(Message message) throws Exception {

    byte[] input = message.getBuffer();

    Address[] addresses = readAddressesFromByte(input);

    combinerAddresses = new HashMap<>();
    for (Address address : addresses) {
      BigInteger id = BigInteger.valueOf(combinerAddresses.size() + 1);
      combinerAddresses.put(id, address);
    }

    informUser("Received combiner addresses from dealer...", false);
    acknowledgeMessage(message, "");

    startTimer(ParticipatingSessionState.WaitForReconstruction);
    informUser("Wait for reconstruction flag from dealer...", false);
  }

  private void receivedPlayerAddress(Message message) throws Exception {

    byte[] input = message.getBuffer();

    Address[] addresses = readAddressesFromByte(input);

    participantsAddresses = new HashMap<>();
    for (Address address : addresses) {
      BigInteger id = BigInteger.valueOf(participantsAddresses.size() + 1);
      participantsAddresses.put(id, address);
    }

    informUser("Received player addresses from dealer...", false);
    acknowledgeMessage(message, "");

    startTimer(ParticipatingSessionState.WaitForIntermediateCalculation);
    informUser("Wait for intermediate calculation flag from dealer...", false);
  }

  private Address[] readAddressesFromByte(byte[] input) {
    try {
      final ByteArrayInputStream baos = new ByteArrayInputStream(input);
      final DataInputStream dos = new DataInputStream(baos);
      return Util.readAddresses(dos);
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private SessionInfo byteArrayToSessionInfo(byte[] bytes)
      throws IOException, ClassNotFoundException {
    ByteArrayInputStream byteStream = new ByteArrayInputStream(bytes);
    ObjectInputStream objStream = new ObjectInputStream(byteStream);

    return (SessionInfo) objStream.readObject();
  }

  private void readKeysToHashMap(String keyString) {

    HashMap<BigInteger, Address> newParticipantAddresses = new HashMap<>();
    String[] keys = keyString.split(";");
    for (int i = 0; i < participantsAddresses.size(); i++) {
      newParticipantAddresses.put(
          new BigInteger(keys[i]),
          participantsAddresses.get(BigInteger.valueOf(i + 1)));
    }
    participantsAddresses = newParticipantAddresses;
  }

  private void receivedCommitments(Message message) throws Exception {
    try {
      informUser("Received commitments from dealer...", false);
      HashMap<BigInteger, BigInteger> commitments = message.getObject();
      //informUser("commitments retrieved:" + Collections.singletonList(commitments), false);

      myself.addCommitmentShares(commitments);

      acknowledgeMessage(message, "");

      startTimer(ParticipatingSessionState.WaitForShare);
      informUser("Wait for next share or distribution addresses from dealer...", false);
    } catch (SSLibException ex) {
      cancelSessionOnException(
          "Commitment values retrieved from dealer are invalid! " + ex.getMessage(),
          true);
      stopTimerAndResetAck();
    }
  }

  private void receivedID(Message message) throws Exception {
    secretId = message.getObject();
    acknowledgeMessage(message, secretId);
    startTimer(ParticipatingSessionState.WaitForSessionObject);
    informUser("Received ID from dealer...", false);
    informUser("Wait for session object from dealer...", false);
  }

  private void receivedSessionInfo(Message message) throws Exception {

    currentSessionInfo = message.getObject();
    myself.addSessionInfo(secretId, currentSessionInfo);
    acknowledgeMessage(message, "");
    startTimer(ParticipatingSessionState.WaitForShare);
    informUser("Received session object from dealer...", false);
    informUser("Wait for share from dealer...", false);
  }

  private void receivedShareObject(Message message) throws Exception {

    acknowledgeMessage(message, "");
    informUser("Received share from dealer...", false);

    MultipartyOperationObject share = message.getObject();
    currentOperation = share.getShareType();

    switch (currentOperation) {
      case None:
        myself.addSimpleLocalShare(share);
        break;
      case Sum:
        myself.addLocalSharesOfSums(share);
        break;
      case Product:
        myself.addLocalSharesOfProducts(share);
        break;
    }

    if (currentOperation == Operation.Product) {
      startTimer(ParticipatingSessionState.WaitForIntermediateDistribution);
      informUser("Wait for addresses to perform intermediate calculation...", false);
    } else {
      informUser("Wait for commitments from dealer...", false);
      startTimer(ParticipatingSessionState.WaitForShare);
    }
  }

  private void processNewSessionMsg(Message message) throws Exception {

    //Return new session message, when currently part of a session
    if (currentSessionId != null) {
      return;
    }

    String[] messages = message.getObject().toString().split(";");
    String sharingReason = messages[0];
    currentSessionId = Integer.valueOf(messages[1]);

    String userInfo =
        String.format(
            "A new session has been created. Reason for sharing: %s.\n"
            + "            - Enter 'C' to join as combiner.\n"
            + "            - Enter 'P' to join as player.\n"
            + "            - Enter 'X', if you do not want to join.",
            sharingReason);

    String result = informUser(userInfo, true).toUpperCase();

    switch (result) {
      case "C":
        myself = new Participant(true);
        break;
      case "P":
        myself = new Participant(false);
        break;
      case "X":
        currentSessionId = null;
        startTimer(ParticipatingSessionState.WaitForNewSession);
        informUser("Waiting for new sessions in cluster...", false);
        return;
    }

    acknowledgeMessage(message, myself);
    startTimer(ParticipatingSessionState.WaitForID);
    informUser("Wait for ID from dealer...", false);
  }

  private void acknowledgeMessage(Message message, Object additionalInfo) throws Exception {
    Message msg = new Message();
    msg.setObject(additionalInfo);
    msg.setDest(message.getSrc());
    myChannel.sendMessage(msg, MessageType.Acknowledge.getMessageID(), currentSessionId);
  }

  private void startTimer(ParticipatingSessionState nextSessionState) {
    currentSessionState = nextSessionState;
    timer = new SSCTimer(timeoutValue, this);
    timer.startTimer();
  }

  @Override
  public void onTimeoutExceeded() throws Exception {

    switch (currentSessionState) {
      case WaitForID:
        cancelSessionOnException("Did not receive ID from dealer in time.", true);
        stopTimerAndResetAck();
        break;
      case WaitForSessionObject:
        cancelSessionOnException("Did not receive session object from dealer in time.", true);
        stopTimerAndResetAck();
        break;
      case WaitForShare:
        if (!myself.IsCombiner()) {
          stopTimerAndResetAck();
          currentSessionState = ParticipatingSessionState.Finished;
          finishSessionAsPlayer();
          return;
        }
        cancelSessionOnException("Did not receive next share object from dealer in time.", true);
        stopTimerAndResetAck();
        break;
      case WaitForIntermediateDistribution:
        cancelSessionOnException(
            "Did not receive addresses of other participants from dealer in time.",
            true);
        stopTimerAndResetAck();
        break;
      case WaitForIntermediateCalculation:
        cancelSessionOnException(
            "Did not receive flag for intermediate calculation from dealer in time.",
            true);
        stopTimerAndResetAck();
        return;
      case WaitForIntermediateOthersShare:
        if (receivedShareCount < currentSessionInfo.getNumberOfPlayers() - 1) {
          cancelSessionOnException("Did not receive shares of all players in time.", true);
          stopTimerAndResetAck();
          return;
        }
        if (receivedAckCount < currentSessionInfo.getNumberOfPlayers() - 1) {
          cancelSessionOnException("Did not receive acknowledge of all players in time.", true);
          stopTimerAndResetAck();
          return;
        }
        myself.calculateReducedPolynomial();
        acknowledgeMessage(lastMessageToAcknowledge, "");
        informUser("Wait for next share or distribution addresses from dealer...", false);
        stopTimerAndResetAck();
        startTimer(ParticipatingSessionState.WaitForShare);
        break;
      case WaitForOthersShare:
        if (receivedShareCount < currentSessionInfo.getNumberOfCombiners() - 1) {
          cancelSessionOnException("Did not receive shares of all combiners in time.", true);
          stopTimerAndResetAck();
          return;
        }
        if (receivedAckCount < currentSessionInfo.getNumberOfCombiners() - 1) {
          cancelSessionOnException("Did not receive acknowledge of all combiners in time.", true);
          stopTimerAndResetAck();
          return;
        }
        BigInteger result = myself.reconstructFinalResult();
        currentSessionState = ParticipatingSessionState.Finished;
        acknowledgeMessage(lastMessageToAcknowledge, "");
        stopTimerAndResetAck();
        finishSessionOnReconstruction(myself.IsCombiner(), result);
        break;
    }
  }

  private void stopTimerAndResetAck() {
    timer.stopTimer();
    receivedAckCount = 0;
    receivedShareCount = 0;
    // informUser("resetAckCounter",false);
  }
}
