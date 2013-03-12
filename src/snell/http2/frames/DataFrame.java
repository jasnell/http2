package snell.http2.frames;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;

public class DataFrame extends Frame<DataFrame> {

  static final byte TYPE = 0x0;
    
  protected DataFrame(boolean fin, int opaque_id, int max_size) {
    super(fin, (byte)0x0, opaque_id, max_size);
  }

  protected DataFrame(boolean fin, int opaque_id) {
    super(fin, (byte)0x0, opaque_id);
  }

  public static DataFrame create(int id) {
    return new DataFrame(false, id);
  }
  
  public static DataFrame create(boolean fin, int id) {
    return new DataFrame(fin, id);
  }
  
  public static DataFrame create(boolean fin, int id, int max_size) {
    return new DataFrame(fin, id, max_size);
  }
  
  @Override
  public boolean fillFrom(InputStream in) throws IOException {
    return super.fillFrom(in);
  }

  public boolean fillFrom(InputStream in, int c) throws IOException {
    return super.fillFrom(in,c);
  }
  
  @Override
  public boolean fillFrom(ReadableByteChannel c) throws IOException {
    return super.fillFrom(c);
  }
  
  public boolean fillFrom(ReadableByteChannel c, int n) throws IOException {
    return super.fillFrom(c,n);
  }

  @Override
  public int size() {
    return super.size();
  }

  static DataFrame parse(
    boolean fin, 
    boolean con, 
    int opaque_id, 
    int size, 
    InputStream rest) 
      throws IOException {
    DataFrame frame = 
      DataFrame.create(fin, opaque_id);
    frame.con(con);
    frame.fillFrom(rest);
    return frame;
  }
}
