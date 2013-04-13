package snell.http2.headers.headerdiff;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Lists.newArrayListWithExpectedSize;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static snell.http2.headers.headerdiff.Utils.commonPrefixLength;
import static snell.http2.headers.headerdiff.Utils.utf8length;

import java.util.List;
import java.util.Set;

import snell.http2.utils.CountingReference;
import snell.http2.utils.IntTriple;
import snell.http2.utils.Pair;
import snell.http2.utils.ReferenceCounter;

public final class HeaderTable {

  public static interface Size {
    int size();
  }
  
  public static interface PopListener {
    void popped(int idx);
  }
  
  private static final class Item {
    final Name name;
    final CountingReference<String> val;
    final int seq;
    private transient int vsize = -1;
    private transient int hash = 1;
    Item(
      Name name, 
      CountingReference<String> val, 
      int seq) {
      this.name = name;
      this.val = val;
      this.seq = seq;
    }
    Name name() {
      return name;
    }
    String value() {
      return val != null ? val.get() : null;
    }
    Pair<Name,String> asPair() {
      return Pair.of(name(),value());
    }
    int seq() {
      return seq;
    }
    private int valLen() {
      if (vsize == -1)
        vsize = utf8length(val.get());
      return vsize;
    }
    public int size() {
      int size = 0;
      if (val != null && val.count() == 1)
        size += valLen();
      return size;
    }
    @Override
    public int hashCode() {
      if (hash == 1) {
        hash = 31 * hash + ((name == null) ? 0 : name.hashCode());
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
      if (name == null) {
        if (other.name != null)
          return false;
      } else if (!name.equals(other.name))
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
  
  private final ReferenceCounter<String> valCounter = 
    new ReferenceCounter<String>();
  private final List<Item> store;
  private final int max, maxsize;
  private int first, current, current_size;
  private final Set<PopListener> listeners = 
    newHashSetWithExpectedSize(10); // what size is reasonable?

  public HeaderTable() {
    this(DEFAULT_MAX, DEFAULT_MAX_SIZE);
  }
  
  public HeaderTable(int max, int maxsize) {
    this.max = max;
    this.maxsize = maxsize;
    this.first = 0;
    this.current = 0;
    store = newArrayListWithExpectedSize(max);
  }
  
  public int maxSize() {
    return max;
  }
  
  public int maxByteSize() {
    return maxsize;
  }
  
  public int currentByteSize() {
    return current_size;
  }
  
  public int remainingBytes() {
    return maxsize - current_size;
  }
  
  public int remainingItems() {
    return max - size();
  }
  
  public boolean canFitWithinExisting(String val) {
    return current_size + utf8length(val) <= maxsize;
  }
  
  public void clear() {
    store.clear();
    first = 0;
    current = 0;
  }
  
  public int size() {
    return store.size();
  }
  
  private CountingReference<String> valRef(String val) {
    return valCounter.acquire(val);
  }
  
  private Item itemFor(Name name, String val, int c) {
    return new Item(name,valRef(val),c);
  }
  
  public void replace(int idx, Name name, String val) {
    Item item = itemFor(name,val,idx);
    store.set(idx, item);
  }
  
  public int store(Name name, String val) {
    if (max == 0 || maxsize == 0) return -1;
    Item item = itemFor(name,val,current);
    checkState(reserve(item));
    store.add(item);
    current_size += item.size();
    current++;
    if (current > DEFAULT_MAX)
      current = 0;
    return item.seq();
  }
  
  private Item lookupItem(int idx) {
    return getItem(idx-first);
  }
  
  public Name lookupName(int idx) {
    Pair<Name,String> pair = lookup(idx);
    return pair != null ? pair.one() : null;
  }
  
  public Pair<Name,String> lookup(int idx) {
    Item item = lookupItem(idx);
    return item != null ? item.asPair() : null;
  }
  
  public int getItemLength(int idx) {
    Item item = getItem(idx);
    return item != null ? item.size() : 0;
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
    if (first >= DEFAULT_MAX-1)
      first = 0;
    return release(
      item);
  }
  
  public int leastRecentlyUsedIndex() {
    return store.isEmpty() ? -1 : store.get(0).seq();
  }
  
  public int leastRecentlyUsedSize() {
    String val = 
      store.isEmpty() ? 
        null : 
        store.get(0).value();
    return val != null ?
      val.length() : 0;
  }
  
  private void notifyListeners(int seq) {
    for (PopListener p : listeners)
      p.popped(seq);
  }
  
  public void addListener(PopListener listener) {
    listeners.add(listener);
  }
  
  protected boolean release(Item item) {
    if (item != null) {
      current_size -= item.size();
      valCounter.release(item.value());
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
  
  public IntTriple locate(Name name, String val) {
    int a = -1, b = -1;
    int cl = 0;
    for (int c = current - 1; c >= 0; c--) {
      Item item = lookupItem(c);
      checkNotNull(item);
      if (item.name().equals(name)) {
        int cp = commonPrefixLength(item.value(), val);
        if (cp >= cl) {
          cl = cp;
          a = item.seq();
        }
        if (item.value().equals(val)) {
          b = item.seq();
          break;
        }
      }
    }
    return IntTriple.of(a,b,cl);
  }
}
