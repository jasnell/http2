package snell.http2.headers.delta;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;

import java.util.List;
import java.util.Set;

import com.google.common.base.Throwables;

import snell.http2.headers.ValueSupplier;
import snell.http2.utils.CountingReference;
import snell.http2.utils.IntPair;
import snell.http2.utils.Pair;
import snell.http2.utils.ReferenceCounter;


@SuppressWarnings("rawtypes")
public class Storage {
  
  public static Storage create() {
    return new Storage(StaticStorage.getInstance());
  }
  
  public static Storage create(int max, int max_size) {
    return new Storage(StaticStorage.getInstance(),max,max_size);
  }
  
  public static interface Size {
    int size();
  }
  
  public static interface PopListener {
    void popped(int idx);
  }
  
  private static final class Item {
    final CountingReference<String> key;
    final CountingReference<ValueSupplier> val;
    final int seq;
    private transient int ksize = -1, vsize = -1;
    private transient int hash = 1;
    Item(
      CountingReference<String> key, 
      CountingReference<ValueSupplier> val, int seq) {
      this.key = key;
      this.val = val;
      this.seq = seq;
    }
    String key() {
      return key != null ? key.get() : null;
    }
    ValueSupplier value() {
      return val != null ? val.get() : null;
    }
    Pair<String,ValueSupplier> asPair() {
      return Pair.of(key(),value());
    }
    int seq() {
      return seq;
    }
    private int keyLen() {
      if (ksize == -1) {
        try {
        ksize = key().getBytes("ISO-8859-1").length;
        } catch (Throwable t) {
          throw Throwables.propagate(t);
        }
      }
      return ksize;
    }
    private int valLen() {
      if (vsize == -1)
        vsize = value().length();
      return vsize;
    }
    public int size() {
      int size = 0;
      if (key != null && key.count() == 1) {
        size += keyLen();
      }
      if (val != null && val.count() == 1)
        size += valLen();
      return size;
    }
    @Override
    public int hashCode() {
      if (hash == 1) {
        hash = 31 * hash + ((key == null) ? 0 : key.hashCode());
        hash = 31 * hash + ((val == null) ? 0 : val.hashCode());
        hash = 31 * hash + seq;
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
      Item other = (Item) obj;
      if (key == null) {
        if (other.key != null)
          return false;
      } else if (!key.equals(other.key))
        return false;
      if (seq != other.seq)
        return false;
      if (val == null) {
        if (other.val != null)
          return false;
      } else if (!val.equals(other.val))
        return false;
      return true;
    }


  }
  
  private final static int DEFAULT_MAX = 0xFFFF;
  private final static int DEFAULT_MAX_SIZE = Integer.MAX_VALUE;
  
  private final Storage static_storage;
  private final ReferenceCounter<String> keyCounter = 
    new ReferenceCounter<String>();
  private final ReferenceCounter<ValueSupplier> valCounter = 
    new ReferenceCounter<ValueSupplier>();
  private final List<Item> store;
  private final int offset, max, maxsize;
  private int first, current, current_size;
  private final Set<PopListener> listeners = 
    newHashSetWithExpectedSize(10); // what size is reasonable?
  
  public Storage() {
    this(null, DEFAULT_MAX, DEFAULT_MAX_SIZE);
  }
  
  public Storage(Storage static_storage) {
    this(static_storage, DEFAULT_MAX, DEFAULT_MAX_SIZE);
  }
  
  public Storage(Storage static_storage, int max) {
    this(static_storage,max,DEFAULT_MAX_SIZE);
  }
  
  public Storage(Storage static_storage, int max, int maxsize) {
    this.static_storage = static_storage;
    this.offset = 
      static_storage != null ?
      static_storage.size() : 0;
    this.max = max;
    this.first = 0;
    this.current = offset;
    this.maxsize = maxsize;
    store = newArrayListWithExpectedSize(max);
  }
  
  public int currentByteSize() {
    return current_size;
  }
  
  public void addListener(PopListener listener) {
    listeners.add(listener);
  }
  
  public void clear() {
    store.clear();
    first = -1;
    current = -1;
  }
  
  public int size() {
    return store.size();
  }
  
  private CountingReference<String> keyRef(String key) {
    return keyCounter.acquire(key);
  }
  
  private CountingReference<ValueSupplier> valRef(ValueSupplier val) {
    return valCounter.acquire(val);
  }
  
  private Item itemFor(String key, ValueSupplier val, int c) {
    return new Item(keyRef(key),valRef(val),c);
  }
  
  public int store(String key, ValueSupplier val) {
    if (max == 0 || maxsize == 0) return -1;
    Item item = itemFor(key,val,current);
    checkState(reserve(item));
    store.add(item);
    current_size += item.size();
    current++;
    if (current > DEFAULT_MAX)
      current = offset;
    return item.seq();
  }

  private Item lookupItem(int idx) {
    if (idx < offset)
      return static_storage.lookupItem(idx);
    else if ((offset+first) > idx) {
      idx = (offset+max) - (offset+first) + idx - offset;//;
      return getItem(idx);
    } else {
      idx -= offset;// + first;
      return getItem(idx);
    }
  }
  
  public String lookupKey(int idx) {
    Pair<String,ValueSupplier> pair = lookup(idx);
    return pair != null ? pair.one() : null;
  }
  
  public Pair<String,ValueSupplier> lookup(int idx) {
    Item item = lookupItem(idx);
    return item != null ? item.asPair() : null;
  }
  
  private Item getItem(int idx) {
    return idx >= 0 && idx < size() ? store.get(idx) : null;
  }
  
  public boolean pop() {
    if (store.isEmpty())
      return true;
    Item item = store.remove(0);
    notifyListeners(item.seq());
    first++;
    if ((offset+first) >= DEFAULT_MAX-1)
      first = 0;
    return release(
      item);
  }
  
  private void notifyListeners(int seq) {
    for (PopListener p : listeners)
      p.popped(seq);
  }
  
  protected boolean release(Item item) {
    if (item != null) {
      keyCounter.release(item.key());
      valCounter.release(item.value());
      current_size -= item.size();
      return true;
    } else return false;
  }
  
  protected boolean reserve(Item item) {
    if (size() == max)
      pop();
    int s = item.size();
    checkState(s <= maxsize);
    while (current_size+s > maxsize)
      pop();
    return true;
  }
  
  protected IntPair locate(String key, ValueSupplier val) {
    int a = -1, b = -1;
    for (int c = current - 1; c >= 0; c--) {
      Item item = lookupItem(c);
      checkNotNull(item);
      if (item.key().equals(key)) {
        if (a == -1)
          a = item.seq();
        if (item.value().equals(val)) {
          b = item.seq();
          break;
        }
      }
    }
    return IntPair.of(a,b);
  }

}
