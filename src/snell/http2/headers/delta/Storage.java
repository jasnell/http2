package snell.http2.headers.delta;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;

import java.util.List;
import java.util.Set;

import snell.http2.headers.ValueSupplier;
import snell.http2.utils.IntPair;
import snell.http2.utils.Pair;


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
    final Pair<String,ValueSupplier> item;
    final int seq;
    private transient int size = 0;
    Item(
      String key, 
      ValueSupplier val, int seq) {
      this.item = Pair.of(key, val);
      this.seq = seq;
    }
    String key() {
      return item.one();
    }
    ValueSupplier value() {
      return item.two();
    }
    Pair<String,ValueSupplier> item() {
      return item;
    }
    int seq() {
      return seq;
    }
    public int size() {
      if (size == 0) {
        if (item.one() != null) {
          try {
            size += item.one().getBytes("ISO-8859-1").length;
          } catch (Throwable t) {}
        }
        if (item.two() != null) {
          size += item.two().length();
        }
      }
      return size;
    }
    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((item == null) ? 0 : item.hashCode());
      result = prime * result + seq;
      return result;
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
      if (item == null) {
        if (other.item != null)
          return false;
      } else if (!item.equals(other.item))
        return false;
      if (seq != other.seq)
        return false;
      return true;
    }

  }
  
  private final static int DEFAULT_MAX = 0xFFFF;
  private final static int DEFAULT_MAX_SIZE = Integer.MAX_VALUE;
  
  private final Storage static_storage;
  
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
  
  public int store(String key, ValueSupplier val) {
    Item item = new Item(key,val,current);
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
    return item != null ? item.item() : null;
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
    current_size -= item.size();
    return true;
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
    for (int c = current - 2; c >= 0; c--) {
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
