package snell.http2.frames;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PingFrame 
  extends Frame<PingFrame> {
  
  static final byte TYPE = 0x6;
  
  protected PingFrame(
    int opaque_id) {
      super(true, TYPE, opaque_id);
  }

  @Override
  public void writeTo(OutputStream out) throws IOException {
    super.writeTo(out);
  }

  static PingFrame parse(
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    PingFrame frame = 
      PingFrame.create(opaque_id);
    frame.con(con);
    frame.fin(fin);
    return frame;
  }
  
  public static PingFrame create(int id) {
    return new PingFrame(id);
  }
  
}
