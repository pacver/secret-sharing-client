package SessionManagement;

import JGroupCommunication.MessageType;
import JGroupCommunication.SSCMsgIDHeader;
import JGroupCommunication.SSCSessionIDHeader;
import SSLibrary.CommitmentType;
import SSLibrary.Dealer;
import SSLibrary.MultipartyOperation;
import SSLibrary.MultipartyOperationObjectList;
import SSLibrary.Operation;
import SSLibrary.Participant;
import SSLibrary.SSLibException;
import SSLibrary.SessionInfo;
import TimerHelper.SSCTimer;
import TimerHelper.TimeoutListener;
import org.jgroups.Address;
import org.jgroups.Message;
import org.jgroups.util.Util;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class DealingManager extends SessionManager implements TimeoutListener {

  private       DealingSessionState           currentSessionState;
  private       Dealer                        myself;
  private       SessionInfo                   currentSessionInfo;
  private       int                           currentAckCounter = 0;
  private       SSCTimer                      timer;
  private       MultipartyOperationObjectList currentQueryShareList;
  private final long                          timeoutValue      = 30; //sekunden

  public DealingManager() {

    super();
    myChannel.setMessageReceiveListener(this);
  }

  @Override
  public void onMessageReceived(Message message) throws Exception {

    Integer msgID = ((SSCMsgIDHeader) message.getHeader((short) 1821)).getMsgID();
    Integer sessionID = ((SSCSessionIDHeader) message.getHeader((short) 1822)).getSessionID();

    if (!sessionID.equals(currentSessionId)) {
      return;
    }

    Object msgObject = message.getObject();
    switch (MessageType.forValue(msgID)) {
      case Acknowledge:
        currentAckCounter++;
        //System.out.println("ACK received: " + currentAckCounter);

        if (currentSessionState == DealingSessionState.WaitForJoiningACK) {
          processNewParticipantMsg(message.getSrc(), msgObject);
        } else if (currentSessionState == DealingSessionState.WaitForShareACK) {
          if (participantsAddresses.size() > 0
              && currentAckCounter == participantsAddresses.size()) {
            timer.startTimerTaskImmediately();
          }
        } else if (currentSessionState == DealingSessionState.WaitForDistributionACK
                   || currentSessionState == DealingSessionState.WaitForReconstructionACK) {
          if (currentAckCounter == combinerAddresses.size()) {
            timer.startTimerTaskImmediately();
          }
        } else if (participantsAddresses.size() > 0
                   && currentAckCounter == participantsAddresses.size()) {
          timer.startTimerTaskImmediately();
        }
        break;
      case CancelSession:
        informUser("Session cancelled by participant! Reason: " + msgObject.toString(), false);
        break;
      default:
        throw new Exception("Unknown message received");
    }
  }

  private void processNewParticipantMsg(Address src, Object messageObject) throws SSLibException {

    if (messageObject == null || !messageObject.getClass().equals(Participant.class)) {
      return;
    }

    Participant participant = (Participant) messageObject;

    BigInteger secretId = BigInteger.valueOf(participantsAddresses.size() + 1);

    if (participant.IsCombiner()) {
      combinerAddresses.put(secretId, src);
    }

    participantsAddresses.put(secretId, src);
    myself.addParticipantToSession(secretId, participant);
    informUser("A participant joined your session...", false);
  }

  /**
   * Create shares and querydefinitions, create dealer object, create session object and inform
   * other participants about new session
   *
   * @param inputKey
   * @throws Exception
   */
  public void triggerSessionStart(
      ArrayList<MultipartyOperation> mpcProgram,
      HashMap<String, BigInteger> secretDefinitions,
      String inputKey) throws Exception {
    myself = new Dealer();
    myself.defineSecrets(secretDefinitions, mpcProgram, CommitmentType.Pedersen);

    currentSessionId = ThreadLocalRandom.current().nextInt(1, 1822);

    //Send initial session message to all clients in cluster
    String messageText = String.format("%s;%s", inputKey, currentSessionId);
    myChannel.sendMessage(
        messageText,
        null,
        MessageType.NewSession.getMessageID(),
        currentSessionId);

    startTimer(DealingSessionState.WaitForJoiningACK);
  }

  private void startTimer(DealingSessionState nextSessionState) {
    currentSessionState = nextSessionState;
    timer = new SSCTimer(timeoutValue, this);
    timer.startTimer();
  }

  @Override
  public void onTimeoutExceeded() throws Exception {

    int ackCounter = this.currentAckCounter;
    resetAckCounterAndTimer();

    switch (currentSessionState) {
      case WaitForJoiningACK:
        try {
          sendIdToParticipant();
        } catch (SSLibException ex) {
          cancelSessionOnException(ex.getMessage(), true);
        }
        break;
      case WaitForIDACK:
        if (ackCounter < currentSessionInfo.getNumberOfPlayers()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a participant", false);
          return;
        }
        sendSessionInfoToParticipants();
        break;
      case WaitForSessionObjectACK:
        if (ackCounter < currentSessionInfo.getNumberOfPlayers()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a participant", false);
        }
        sendNextSharesToParticipants();
        break;
      case WaitForShareACK:
        if (ackCounter < currentSessionInfo.getNumberOfPlayers()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a participant", false);
          return;
        }
        if (currentQueryShareList.getOperationType() == Operation.Product) {
          sendAddressesForIntermediateCalculation();
        } else {
          sendCommitmentValues();
        }
        break;
      case WaitForCommitmentACK:
        if (ackCounter < currentSessionInfo.getNumberOfPlayers()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a participant", false);
          return;
        }

        //When another query share is available, send this. Otherwise send addresses for reconstruction to participants.
        if (myself.isNextShareAvailable()) {
          sendNextSharesToParticipants();
        } else {
          sendDistributionAddresses();
        }
        break;
      case WaitForIntermediateDistributionACK:
        if (ackCounter < currentSessionInfo.getNumberOfPlayers()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a participant", false);
          return;
        }
        sendIntermediateCalculationFlag();
        break;
      case WaitForIntermediateCalculationACK:
        if (ackCounter < currentSessionInfo.getNumberOfPlayers()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a participant", false);
          return;
        }
        if (myself.isNextShareAvailable()) {
          sendNextSharesToParticipants();
        } else {
          sendDistributionAddresses();
        }
        break;
      case WaitForDistributionACK:
        if (ackCounter < currentSessionInfo.getNumberOfCombiners()) {
          cancelSessionOnException("ACK Timeout reached. Missing ACK from a combiner", false);
        }
        sendReconstructionFlag();
        break;
      case WaitForReconstructionACK:
        if (ackCounter < currentSessionInfo.getNumberOfCombiners()) {
          cancelSessionOnException("Not all combiners finished reconstruction successfully", false);
          return;
        }
        finishSessionOnReconstruction(false, null);
        break;
      default:
        cancelSessionOnException("This should not happen!", false);
        break;
    }
  }

  private void sendAddressesForIntermediateCalculation() throws Exception {
    Message msg = new Message();

    startTimer(DealingSessionState.WaitForIntermediateDistributionACK);
    msg.setObject(writeAddressesToByte(participantsAddresses));

    participantsAddresses.entrySet().stream().forEach(destination -> {
      msg.setDest(destination.getValue());
      try {
        myChannel.sendMessage(
            msg,
            MessageType.IntermediateAddressTransfer.getMessageID(),
            currentSessionId);
      } catch (Exception e) {
        e.printStackTrace();
      }
      informUser("Send addresses to participants...", false);
    });
    informUser("Wait for ACK of addresses for intermediate calculation...", false);
  }

  private void resetAckCounterAndTimer() {
    timer.stopTimer();
    timer = null;
    currentAckCounter = 0;
    //System.out.println("Reset ACK Counter");
  }

  private void sendReconstructionFlag() throws Exception {
    Message msg = new Message();
    informUser("Wait for reconstruction...", false);
    myChannel.sendMessage(msg, MessageType.ReconstructionFlag.getMessageID(), currentSessionId);
    startTimer(DealingSessionState.WaitForReconstructionACK);
  }

  private void sendIntermediateCalculationFlag() throws Exception {
    Message msg = new Message();
    String keys = writeKeysToString(participantsAddresses);

    for (Map.Entry<BigInteger, Address> dest : participantsAddresses.entrySet()) {
      msg.setObject(keys);
      msg.setDest(dest.getValue());
      informUser("Send intermediate calculation flag and keys to player...", false);
      myChannel.sendMessage(
          msg,
          MessageType.IntermediateCalculationFlag.getMessageID(),
          currentSessionId);

      TimeUnit.MILLISECONDS.sleep(10);
    }

    startTimer(DealingSessionState.WaitForIntermediateCalculationACK);
    informUser("Wait for finishing intermediate calculation by players...", false);
  }

  private void sendCommitmentValues() throws Exception {
    //informUser("commitments send:"
      //         + Collections.singletonList(currentQueryShareList.getCommitmentValues()), false);

    startTimer(DealingSessionState.WaitForCommitmentACK);

    Message msg = new Message();
    msg.setObject(currentQueryShareList.getCommitmentValues());
    informUser("Send commitments to participants...", false);
    myChannel.sendMessage(
        msg,
        MessageType.CommitmentObjectsTransfer.getMessageID(),
        currentSessionId);

  }

  private void sendDistributionAddresses() throws Exception {

    Message msg = new Message();
    msg.setObject(writeAddressesToByte(combinerAddresses));

    startTimer(DealingSessionState.WaitForDistributionACK);

    combinerAddresses.entrySet().stream().forEach(destination -> {
      msg.setDest(destination.getValue());
      informUser("Send addresses to combiner...", false);
      try {
        myChannel.sendMessage(
            msg,
            MessageType.CombinerAddressTransfer.getMessageID(),
            currentSessionId);
        TimeUnit.MILLISECONDS.sleep(10);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private void sendSessionInfoToParticipants() throws Exception {
    Message msg = new Message();

    startTimer(DealingSessionState.WaitForSessionObjectACK);
    informUser("Sending session object to participants...", false);

    msg.setObject(currentSessionInfo);
    myChannel.sendMessage(msg, MessageType.SessionObjectTransfer.getMessageID(), currentSessionId);
    informUser("Send session object to participants...", false);
  }

  private byte[] writeAddressesToByte(HashMap<BigInteger, Address> addressHashMap) {
    try {
      final ByteArrayOutputStream baos = new ByteArrayOutputStream();
      final DataOutputStream dos = new DataOutputStream(baos);
      Util.writeAddresses(addressHashMap.values(), dos);
      dos.flush();
      return baos.toByteArray();
    } catch (Exception ex) {
      throw new RuntimeException(ex);
    }
  }

  private String writeKeysToString(HashMap<BigInteger, Address> addressHashMap) {
    StringBuilder keyString = new StringBuilder();
    for (Map.Entry<BigInteger, Address> entry : addressHashMap.entrySet()) {
      keyString.append(entry.getKey()).append(";");
    }
    return keyString.toString();
  }

  private void sendIdToParticipant() throws Exception {
    currentSessionInfo = myself.prepareMultipartyOperationObjects();

    startTimer(DealingSessionState.WaitForIDACK);
    informUser("Sending IDs to participants...", false);

    participantsAddresses.entrySet().stream().forEach(this::sendMsg);
    TimeUnit.MILLISECONDS.sleep(10);
    informUser("Send IDs to participants...", false);
  }

  private void sendNextSharesToParticipants() throws Exception {

    currentQueryShareList = myself.getNextMultipartyOperationObjects();
    Message msg = new Message();
    startTimer(DealingSessionState.WaitForShareACK);

    participantsAddresses.entrySet().stream().forEach(destination -> {
      msg.setObject(currentQueryShareList.getMultipartyOperationObject(destination.getKey()));
      msg.setDest(destination.getValue());
      try {
        informUser("Send share to a participant...", false);
        myChannel.sendMessage(
            msg,
            MessageType.MultipartyOperationTransfer.getMessageID(),
            currentSessionId);
        TimeUnit.MILLISECONDS.sleep(10);
      } catch (Exception e) {
        e.printStackTrace();
      }
    });
  }

  private void sendMsg(Map.Entry<BigInteger, Address> dest) {
    Message msg = new Message();
    msg.setObject(dest.getKey());
    msg.setDest(dest.getValue());
    try {
      myChannel.sendMessage(msg, MessageType.IDTransfer.getMessageID(), currentSessionId);
    } catch (Exception e) {
      e.printStackTrace();
    }
  }
}
