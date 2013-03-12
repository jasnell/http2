package snell.http2.frames;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.TreeSet;

import snell.http2.utils.IoUtils;

public class SettingsFrame 
  extends Frame<SettingsFrame> {
  
  public static final int MAX_KEY_SIZE = 0xFFFFFF;
  
  public static enum FrameFlags {
    CLEAR((byte)0x80);
    private final byte b;
    FrameFlags(byte b) {
      this.b = b;
    }
    byte val() {
      return b;
    }
  }
  
  public static enum SettingFlags {
    PERSIST_VALUE((byte)0x80),
    PERSISTED((byte)0x40);
    private final byte b;
    SettingFlags(byte b) {
      this.b = b;
    }
    byte val() {
      return b;
    }
  }
  
  public static enum Settings {
    UPLOAD_BANDWIDTH,
    DOWNLOAD_BANDWIDTH,
    ROUND_TRIP_TIME,
    MAX_CONCURRENT_STREAMS,
    CURRENT_CWND,
    DOWNLOAD_RETRANS_RATE,
    INITIAL_WINDOW_SIZE,
    CLIENT_CERTIFICATE_VECTOR_SIZE
    ;
  }
  
  static final byte TYPE = 0x4;
  private final byte flags;
  private TreeSet<Long> set = new TreeSet<Long>(new SettingsComparator());
  
  protected SettingsFrame(
    boolean fin, 
    int max_size,
    FrameFlags... flags) {
      super(fin, TYPE, 0, max_size);
      this.flags = initFlags(flags);
  }
  
  private byte initFlags(FrameFlags... flags) {
    byte f = 0;
    for (FrameFlags flag : flags)
      f |= flag.val();
    return f;
  }
  
  public SettingsFrame set(Settings key, long val, SettingFlags... flags) {
    if (key == null)
      throw new IllegalArgumentException();
    // TODO: Need to do proper checking and enforce proper order
    byte f = 0;
    for (SettingFlags flag : flags)
      f |= flag.val();
    int k = (f << 24) | (key.ordinal() + 1);
    int v = (int)val;
    if (!set.add(((long)k << 32) | v))
      throw new IllegalStateException();
    return this;
  }
  
  @Override
  public void writeTo(OutputStream out) throws IOException {
    put(flags);
    putUvarint(set.size());
    //putInt(set.size());
    for (Long l : set)
      IoUtils.write64(out, l);
    super.writeTo(out);
  }

  public static SettingsFrame create(boolean fin, int max_size, FrameFlags... flags) {
    return new SettingsFrame(fin, max_size, flags);
  }
  
  public static SettingsFrame create(boolean fin, FrameFlags... flags) {
    return create(fin, Frame.DEFAULT_MAX_SIZE, flags);
  }
  
  public static SettingsFrame create(FrameFlags... flags) {
    return create(false, flags);
  }

  static class SettingsComparator implements Comparator<Long> {

    @Override
    public int compare(Long l1, Long l2) {
      int m1 = (int)(l1 >> 32) & ~0xFF000000;
      int m2 = (int)(l2 >> 32) & ~0xFF000000;
      if (m1 < m2) return -1;
      if (m1 > m2) return 1;
      return 0;
    }
    
  }
}
