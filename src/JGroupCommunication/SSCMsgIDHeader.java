package JGroupCommunication;

import org.jgroups.Header;
import org.jgroups.util.Streamable;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.function.Supplier;

public class SSCMsgIDHeader extends Header implements Streamable {

    private Integer _msgID;

    public SSCMsgIDHeader()
    {

    }
    public SSCMsgIDHeader(Integer msgID)
    {
        _msgID = msgID;
    }

    public Integer getMsgID()
    {
        return _msgID;
    }

    @Override
    public short getMagicId() {
        return 1821;
    }

    @Override
    public Supplier<? extends Header> create() {
        return SSCMsgIDHeader::new;
    }

    @Override
    public int serializedSize() {
        return 4;
    }

    @Override
    public void writeTo(DataOutput dataOutput) throws Exception {

        dataOutput.writeInt(_msgID);
    }

    @Override
    public void readFrom(DataInput dataInput) throws Exception {
        _msgID = dataInput.readInt();
    }
}
