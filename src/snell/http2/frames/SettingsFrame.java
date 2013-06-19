package snell.http2.frames;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static snell.http2.utils.IoUtils.read64;
import static snell.http2.utils.IoUtils.write32;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Iterator;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.primitives.Ints;

public final class SettingsFrame 
  extends Frame<SettingsFrame>
  implements Iterable<SettingsFrame.Entry> {
  
  public static final byte TYPE = 0x4;
  private static final byte FLAG_CLEAR_PERSISTED = 0x2;
  
  public static final int MAX_KEY_SIZE = 0xFFFFFF;
  
  public static enum SettingFlags {
    PERSIST_VALUE((byte)0x1),
    PERSISTED((byte)0x2);
    private final byte b;
    SettingFlags(byte b) {
      this.b = b;
    }
    byte val() {
      return b;
    }
    boolean set(byte flags) {
      return (flags & b) == b;
    }
    static Iterable<SettingFlags> select(byte flags) {
      ImmutableSet.Builder<SettingFlags> set =
        ImmutableSet.builder();
      for (SettingFlags f : values()) {
        if (f.set(flags))
          set.add(f);
      }
      return set.build();
    }
  }
  
  public static enum Settings {
    UPLOAD_BANDWIDTH(1),
    DOWNLOAD_BANDWIDTH(2),
    ROUND_TRIP_TIME(3),
    MAX_CONCURRENT_STREAMS(4),
    CURRENT_CWND(5),
    DOWNLOAD_RETRANS_RATE(6),
    INITIAL_WINDOW_SIZE(7),
    FLOW_CONTROL_OPTIONS(10)
    ;
    private final int v;
    Settings(int v) {
      checkArgument(v <= MAX_KEY_SIZE);
      this.v = v;
    }
    public int id() {
      return v;
    }
    static Settings select(int id) {
      for (Settings s : values()) 
        if (s.id() == id)
          return s;
      return null;
    }
  }
  
  public static SettingsFrameBuilder make() {
    return new SettingsFrameBuilder();
  }
  
  public static SettingsFrameBuilder make(boolean includeCsh) {
    return make().includeClientSessionHeader(includeCsh);
  }
  
  public static final class SettingsFrameBuilder
    extends FrameBuilder<SettingsFrame,SettingsFrameBuilder> {
    
    private boolean includeCsh = false;
    private ImmutableSortedSet.Builder<Entry> settings = 
      ImmutableSortedSet.naturalOrder();
    
    protected SettingsFrameBuilder() {
      super(TYPE);
      super.streamId(0);
    }
    
    @Override
    protected void parseRest(
      InputStream in) 
        throws IOException {
      int l = this.length;
      while(l > 0) {
        settings.add(Entry.parse(in));
        l -= 8;
      }
    }

    @Override
    public SettingsFrameBuilder streamId(int id) {
      throw new UnsupportedOperationException();
    }

    public SettingsFrameBuilder clearPersisted(boolean on) {
      return this.flag(FLAG_CLEAR_PERSISTED, on);
    }
    
    public SettingsFrameBuilder clearPersisted() {
      return clearPersisted(true);
    }
    
    public SettingsFrameBuilder includeClientSessionHeader(boolean on) {
      this.includeCsh = on;
      return this;
    }
    
    public SettingsFrameBuilder includeClientSessionHeader() {
      return includeClientSessionHeader(true);
    }
    
    public SettingsFrameBuilder persist(Settings key, int val) {
      return set(key,val,SettingFlags.PERSIST_VALUE);
    }
    
    public SettingsFrameBuilder persisted(Settings key, int val) {
      return set(key,val,SettingFlags.PERSISTED);
    }
    
    public SettingsFrameBuilder set(
      Settings key,
      int val) {
        return set(key,val,null);
    }
    
    public SettingsFrameBuilder set(
      Settings key, 
      int val, 
      SettingFlags flag) {
      checkNotNull(key);
      settings.add(
        new Entry(
          key,
          val,
          flag != null ? ImmutableSet.of(flag) : ImmutableSet.<SettingFlags>of()) );
      return this;
    }
    
    public SettingsFrame get() {
      ImmutableSortedSet<Entry> entries = 
        this.settings.build();
      this.length = entries.size() * 8;
      return new SettingsFrame(this,entries);
    }    
  }
  
  private final boolean includeCsh;
  private final ImmutableSet<Entry> set;
  
  protected SettingsFrame(
    SettingsFrameBuilder builder,
    ImmutableSet<Entry> entries) {
      super(builder);
      this.includeCsh = 
        builder.includeCsh;
      this.set = entries;
  }
  
  public boolean clearPersisted() {
    return this.flag(FLAG_CLEAR_PERSISTED);
  }
  
  public Entry get(Settings setting) {
    for (Entry entry : set) {
      if (entry.setting() == setting)
        return entry;
    }
    return null;
  }
  
  public int getValue(Settings setting) {
    Entry entry = get(setting);
    return entry != null ? entry.value() : -1;
  }
  
  @Override
  public void writeTo(OutputStream out) throws IOException {
    putCsh(out);
    super.writeTo(out);
  }
  
  protected void writeRest(OutputStream out) throws IOException {
    for (Entry l : set)
      l.writeTo(out);
  }
  
  private static final byte[] CSH = 
    {0x46,0x4f,0x4f,0x20,
     0x2a,0x20,0x48,0x54,
     0x54,0x50,0x2f,0x32,
     0x2e,0x30,0x0d,0x0a,
     0x0d,0x0a,0x42,0x41,
     0x52,0x0d,0x0a,0x0d,
     0x0a};
  
  public static boolean consumeCsh(
    InputStream in) 
      throws IOException {
    byte[] buf = new byte[CSH.length];
    int r = in.read(buf);
    if (r < buf.length)
      throw new IOException();
    return Arrays.equals(buf, CSH);
  }
  
  private void putCsh(OutputStream out) throws IOException {
    if (includeCsh)
      out.write(CSH);
  }

  public static final class Entry
    implements Comparable<Entry> {
    
    public static Entry parse(
      InputStream in) 
        throws IOException {
      long entry = read64(in);
      int key = (int)(entry >>> 32);
      int val = (int)entry;
      byte flag = (byte)(entry >>> 24);
      key = key & ~0xFF000000;
      return new Entry(
        Settings.select(key),
        val,
        SettingFlags.select(flag));
    }
    
    private Settings setting;
    private int value;
    private Iterable<SettingFlags> flags;
    private transient int hash = 1;
    protected Entry(
      Settings setting, 
      int value, 
      Iterable<SettingFlags> flags) {
      this.setting = setting;
      this.value = value;
      this.flags = flags;
    }
    public Settings setting() {
      return setting;
    }
    public int value() {
      return value;
    }
    public Iterable<SettingFlags> flags() {
      return flags;
    }
    protected void writeTo(
      OutputStream out) 
        throws IOException {
      byte flag = 0;
      for (SettingFlags f : flags)
        flag |= f.val();
      write32(out,(flag << 24)|setting.id());
      write32(out,value);
    }
    @Override
    public int hashCode() {
      if (hash == 1) {
        final int prime = 31;
        hash = prime * hash + ((flags == null) ? 0 : flags.hashCode());
        hash = prime * hash + ((setting == null) ? 0 : setting.hashCode());
        hash = prime * hash + value;
      }
      return hash;
    }
    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      Entry other = (Entry) obj;
      if (flags == null) {
        if (other.flags != null)
          return false;
      } else if (!flags.equals(other.flags))
        return false;
      if (setting != other.setting)
        return false;
      if (value != other.value)
        return false;
      return true;
    }
    @Override
    public int compareTo(Entry other) {
      checkNotNull(other);
      checkNotNull(setting);
      return Ints.compare(
        setting.id(), 
        other.setting.id());
    }
    
  }

  @Override
  public Iterator<Entry> iterator() {
    return set.iterator();
  }
}
