package snell.http2.frames;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.utils.IoUtils;

public class GoAwayFrame 
  extends Frame<GoAwayFrame> {
  
  public static enum Status {
    OK, 
    PROTOCOL_ERROR,
    INTERNAL_ERROR;
 
    public GoAwayFrame create(int id) {
      return GoAwayFrame.create(id, this);
    }
    
    public GoAwayFrame create() {
      return GoAwayFrame.create(this);
    }
    
    public static Status get(int o) {
      return values()[o];
    }
  }
  
  static final byte TYPE = 0x7;
  
  private final int status;
  
  protected GoAwayFrame(
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
  
  static GoAwayFrame parse(
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    int status = IoUtils.uvarint2int(rest);
    GoAwayFrame frame = 
      GoAwayFrame.create(opaque_id, Status.get(status));
    frame.fin(fin);
    frame.con(con);
    return frame;
  }

  public static GoAwayFrame create(int id, Status status) {
    if (status == null)
      throw new IllegalArgumentException();
    return new GoAwayFrame(id, status.ordinal());
  }
  
  public static GoAwayFrame create(Status status) {
    return create(0,status);
  }

}
