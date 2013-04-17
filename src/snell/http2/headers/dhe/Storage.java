package snell.http2.headers.dhe;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Objects;
import com.google.common.base.Strings;

import snell.http2.headers.ValueSupplier;
import snell.http2.utils.IntMap;
import snell.http2.utils.IntTriple;

public class Storage {

  private static final int RACK_SIZE = 128;
  
  private final Storage static_store;  
  private final Item[] rack = 
    new Item[RACK_SIZE];
  private final IntMap idx = 
    new IntMap(128);
  private final IntMap nidx =
    new IntMap(128);
  private int head = 0, tail = 0, count = 0;
  private int length = 0;
  private final int maxlength;
  
  private transient int stats_store_count = 0;
  private transient int stats_pop_count = 0;
  
  public Storage() {
    this(Integer.MAX_VALUE);
  }
  
  protected Storage(Storage static_store) {
    this(Integer.MAX_VALUE, static_store);
  }
  
  public Storage(int maxlength) {
    this(maxlength, new StaticStorage());
  }
  
  protected Storage(
    int maxlength, 
    Storage static_storage) {
      checkArgument(maxlength >= 0);
      this.maxlength = maxlength;
      this.static_store = static_storage;
  }
  
  public byte push(
    String name, 
    ValueSupplier<?> value) {
      stats_store_count++;
      checkNotNull(name);
      checkNotNull(value);
      Item item = 
        new Item(name, value);
      int ihc = item.hashCode();
      if (idx.contains(ihc)) {
        return (byte)idx.get(ihc);
      } else {
        reserve(item);
        int seq = head++;
        rack[seq] = item;
        idx.put(item.hashCode(), seq);
        nidx.put(item.nameHashCode(), seq);
        count++;
        length += item.length();
        if (head == RACK_SIZE)
          head = 0;
        return (byte)seq;
      }
  }
  
  public byte indexOfName(String name) {
    checkNotNull(name);
    name = name.toLowerCase();
    byte nix = (byte)nidx.get(name.hashCode(),-1);
    if (nix != -1)
      return nix;
    nix = static_store != null ?
      static_store.indexOfName(name) : -1;
    if (nix == -1)
      throw new RuntimeException();
    return (byte)(nix | 0x80);
  }
  
  public byte indexOf(
    String name, 
    ValueSupplier<?> value) {
      Item item = new Item(name,value);
      byte nix = (byte)idx.get(item.hashCode(), -1);
      if (nix != -1)
        return nix;
      nix = 
        static_store != null ? 
          static_store.indexOf(name, value) : 
            -1;
      if (nix == -1)
        throw new RuntimeException();
      return (byte)(nix | 0x80);
  }
  
  private void reserve(Item item) {
    if (size() + 1 > RACK_SIZE)
      pop();
    while(length + item.length() > maxlength)
      pop();
  }
  
  private void pop() {
    stats_pop_count++;
    Item item = rack[tail];
    if (item != null) {
      idx.delete(item.hashCode());
      length -= item.length();
    }
    rack[tail++] = null;
    count--;
    if (tail >= RACK_SIZE)
      tail = 0;
  }
  
  public int size() {
    return count;
  }
  
  public int byteSize() {
    return length;
  }
  
  public String nameOf(byte idx) {
    if (idx < 0) {
      idx = (byte)(idx & ~0x80);
      return static_store != null ?
        static_store.nameOf(idx) : 
        null;
    } else {
      checkArgument(idx <= RACK_SIZE);
      Item item = rack[idx];
      return item != null ? item.name() : null;
    }
  }
  
  public <V>ValueSupplier<V> valueOf(byte idx) {
    if (idx < 0) {
      idx = (byte)(idx & ~0x80);
      return static_store != null ?
        static_store.<V>valueOf(idx) :
        null;
    } else {
      checkArgument(idx <= RACK_SIZE);
      Item item = rack[idx];
      return item != null ? item.<V>value() : null;
    }
  }
  
  final static class Item {
    private final String name;
    private final ValueSupplier<?> value;
    private transient final int len;
    private transient int hash = 1;
    private transient int nameHash = 1;
    private transient int valHash = 1;
    
    Item(
      String name,
      ValueSupplier<?> value) {
      checkNotNull(name);
      checkNotNull(value);
      this.name = name.toLowerCase();
      this.value = value;
      this.len = value != null ? value.length() : 0;
      hashCode();
    }
    
    int length() {
      return len;
    }
    
    String name() {
      return name;
    }
    
    @SuppressWarnings("unchecked")
    <V>ValueSupplier<V> value() {
      return (ValueSupplier<V>) value;
    }

    int nameHashCode() {
      return nameHash;
    }
    
    @Override
    public int hashCode() {
      if (hash == 1) {
        nameHash = name.hashCode();
        valHash = value.hashCode();
        hash = 31 * hash + ((name == null) ? 0 : nameHash);
        hash = 31 * hash + ((value == null) ? 0 : valHash);
      }
      return hash;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) return true;
      if (obj == null) return false;
      if (getClass() != obj.getClass())
        return false;
      Item other = (Item) obj;
      return Objects.equal(name, other.name) &&
             Objects.equal(value, other.value);
    }
    
    public String toString() {
      return Objects
        .toStringHelper(Item.class)
        .add("Name", name)
        .add("Value", value)
        .toString();
    }
  }
 
  public void printStats(String prefix) {
    System.err.println(
      String.format(
        "%s - Count: %d, Bytes: %d, Puts: %d, Pops: %d", 
        prefix,
        count, 
        length, 
        stats_store_count, 
        stats_pop_count));
  }
  
  public String toString() {
    return java.util.Arrays.toString(rack);
  }
  
  public static final char[] PFX_LIMITS = 
    new char[] {'/', '&', '?', '=', ',', ';', ' '};
  
  public String expand(
    byte idx, 
    int position, 
    int length, 
    String suffix) {
    try {
      Item item = rack[idx];
      if (item != null) {
        ValueSupplier<?> val = item.value();
        if (val instanceof CommonPrefixStringValueSupplier) {
          CommonPrefixStringValueSupplier svs = val.cast();
          String s = svs.get(position);
          s = s.substring(0,length);
          s += suffix;
          return s;
        }
        return null;
      } else return null;
    } catch (Throwable t) {
      return null;
    }
  }
  
  private static int cp(String a1, String a2) {
    String pfx = Strings.commonPrefix(a1, a2);
    int n = pfx.length()-1;
    while(n >= 0) {
      char c = pfx.charAt(n);
      for (char b : PFX_LIMITS)
        if (b == c)
          return n;
      n--;
    }
    return n;
  }
  
  // Common Prefix Test Code
  public IntTriple findLongestCommonPrefix(String name, String val) {
    checkNotNull(name);
    checkNotNull(val);
    name = name.toLowerCase();
    int idx = -1;
    int len = -1;
    int lst = -1;
    int c = this.size();
    int i = head;
    while(c > 0) {
      Item item = rack[i];
      if (item != null && 
          name.equals(item.name())) {
        ValueSupplier<?> vs = item.value();
        if (vs instanceof CommonPrefixStringValueSupplier) {
          CommonPrefixStringValueSupplier svs = vs.cast();
          int n = 0;
          for (String s : svs.get()) {
            int cp = cp(s,val);
            if (cp > 0 && cp > len) {
              idx = i;
              len = cp;
              lst = n;
            }
            n++;
          }
        }
      }
      i--;
      c--;
      if (i < 0) i = 127;
    }
    return IntTriple.of(idx,len,lst);
  }
  
}
