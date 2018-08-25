package JGroupCommunication;

import org.jgroups.Header;
import org.jgroups.util.Streamable;

import java.io.DataInput;
import java.io.DataOutput;
import java.util.function.Supplier;

public class SSCSessionIDHeader extends Header implements Streamable {

    private Integer sessionID;

    public SSCSessionIDHeader()
    {

    }
    public SSCSessionIDHeader(Integer sessionID)
    {
        this.sessionID = sessionID;
    }

    public Integer getSessionID()
    {
        return sessionID;
    }

    @Override
    public short getMagicId() {
        return 1822;
    }

    @Override
    public Supplier<? extends Header> create() {
        return SSCSessionIDHeader::new;
    }

    @Override
    public int serializedSize() {
        return 4;
    }

    @Override
    public void writeTo(DataOutput dataOutput) throws Exception {

        dataOutput.writeInt(sessionID);
    }

    @Override
    public void readFrom(DataInput dataInput) throws Exception {
        sessionID = dataInput.readInt();
    }
}
