package JGroupCommunication;

import java.util.HashMap;

public enum MessageType {
    NewSession(0),
    IDTransfer(1),
    SessionObjectTransfer(2),
    MultipartyOperationTransfer(3),
    CommitmentObjectsTransfer(4),
    CombinerAddressTransfer(5),
    IntermediateAddressTransfer(6),
    IntermediateCalculationFlag(7),
    OthersShare(8),
    IntermediateOthersShare(9),
    ReconstructionFlag(10),
    Acknowledge(11),
    CancelSession(12),
    SessionFinish(13);


    private final int _ID;

    MessageType(int ID)
    {
        _ID = ID;
        getMappings().put(ID, this);
    }

    public int getMessageID() { return _ID; }

    private static HashMap<Integer, MessageType> mappings;
    private static HashMap<Integer, MessageType> getMappings()
    {
        if (mappings == null)
        {
            synchronized (MessageType.class)
            {
                if (mappings == null)
                {
                    mappings = new HashMap<>();
                }
            }
        }
        return mappings;
    }

    public static MessageType forValue(int value)
    {
        return getMappings().get(value);
    }

}
