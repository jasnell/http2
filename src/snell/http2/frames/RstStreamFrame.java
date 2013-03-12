package snell.http2.frames;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.utils.IoUtils;

public class RstStreamFrame 
  extends Frame<RstStreamFrame> {
  
  public static enum Status {
    PROTOCOL_ERROR,
    INVALID_STREAM,
    REFUSED_STREAM,
    UNUSED,
    CANCEL,
    INTERNAL_ERROR,
    FLOW_CONTROL_ERROR,
    STREAM_IN_USE,
    STREAM_ALREADY_CLOSED,
    INVALID_CREDENTIALS,
    FRAME_TOO_LARGE;
    
    public RstStreamFrame create(int id) {
      return RstStreamFrame.create(id, this);
    }
    
    public static Status get(int o) {
      return Status.values()[o];
    }
  }
  
  static final byte TYPE = 0x3;
  
  private final int status;
  
  protected RstStreamFrame(
    int opaque_id,
    int status) {
      super(true, TYPE, opaque_id, 4);
      this.status = status;
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    putUvarint(status);
    super.writeTo(out);
  }
  
  static RstStreamFrame parse(
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    int status = IoUtils.uvarint2int(rest);
    RstStreamFrame frame = 
      RstStreamFrame.create(opaque_id, Status.get(status));
    frame.con(con);
    frame.fin(fin);
    return frame;
  }

  public static RstStreamFrame create(int id, Status status) {
    if (status == null || status == Status.UNUSED)
      throw new IllegalArgumentException();
    return new RstStreamFrame(id, status.ordinal()+1);
  }
  
}
