package snell.http2.frames;

import java.io.IOException;
import java.io.InputStream;

import snell.http2.utils.IoUtils;
import snell.http2.headers.HeaderSerializer;

public final class FrameReader {

  private final HeaderSerializer ser;
  
  public FrameReader(HeaderSerializer ser) {
    this.ser = ser;
  }
  
  @SuppressWarnings("unchecked")
  public <F extends Frame<F>>F nextFrame(InputStream in) throws IOException {
    byte[] header = new byte[4];
    int r = in.read(header);
    if (r < 4)
      throw new IllegalStateException(); // obviously this doesn't work for real
    
    boolean fin = (header[0] & 0x80) == 0x80;
    boolean con = (header[0] & 0x40) == 0x40;
    byte type = (byte)(header[0] & ~0xC0);
    int size = (header[1] << 16) | (header[2] << 8) | header[3];
    
    int opaque_id = IoUtils.uvarint2int(in);
    
    F frame = null;
    
    switch(type) {
    case DataFrame.TYPE:
      frame = (F) DataFrame.parse(fin, con, opaque_id, size, in);
      break;
    case GoAwayFrame.TYPE:
      frame = (F) GoAwayFrame.parse(fin, con, opaque_id, size, in);
      break;
    case HeadersFrame.TYPE:
      frame = (F) HeadersFrame.parse(ser, fin, con, opaque_id, size, in);
      break;
    case PingFrame.TYPE:
      frame = (F) PingFrame.parse(fin, con, opaque_id, size, in);
      break;
    case RstStreamFrame.TYPE:
      frame = (F)RstStreamFrame.parse(fin, con, opaque_id, size, in);
      break;
    case SettingsFrame.TYPE:
      SettingsFrame sf = SettingsFrame.create(fin);
      sf.con(con);
      frame = (F) sf;
      break;
    case SynReplyFrame.TYPE:
      frame = (F)SynReplyFrame.parse(ser, fin, con, opaque_id, size, in);
      break;
    case SynStreamFrame.TYPE:
      frame = (F)SynStreamFrame.parse(ser, fin, con, opaque_id, size, in);
      break;
    }
    
    return frame;
    
  }
  
}
