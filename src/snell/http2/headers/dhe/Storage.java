package snell.http2.headers.dhe;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.base.Objects;

import snell.http2.headers.ValueSupplier;
import snell.http2.utils.IntMap;

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
  
  private int size() {
    return count;
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
  
}
