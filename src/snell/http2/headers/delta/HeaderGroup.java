package snell.http2.headers.delta;

import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.base.Objects;

import snell.http2.headers.HeaderSetter;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.delta.Storage.PopListener;
import snell.http2.utils.IntPair;
import snell.http2.utils.Pair;

/**
 * Header Group represents a currently active set of Headers
 */
@SuppressWarnings("rawtypes")
public final class HeaderGroup 
  implements PopListener {

  private Set<Integer> group = 
    new LinkedHashSet<Integer>();
  private final Storage storage;
  private final byte id;
  
  public HeaderGroup(byte id) {
    this(Storage.create(),id);
  }
  
  public HeaderGroup(
    Storage storage, 
    byte id) {
    this.storage = storage;
    this.id = id;
  }
  
  public byte id() {
    return id;
  }
  
  public Storage storage() {
    return storage;
  }
  
  public boolean hasKV(String key, ValueSupplier val) {
    IntPair pair = storage.locate(key, val);
    int index = pair.two();
    return index > -1 && group.contains(index);
  }
  
  public boolean empty() {
    return group.isEmpty();
  }
  
  public boolean hasEntry(int index) {
    return group.contains(index);
  }
  

  public void removeEntry(int index) {
    group.remove(index);
  }
  
  public Iterable<Integer> getIndices() {
    return group;
  }
  
  public void toggle(int index) {
    if (group.contains(index))
      group.remove(index);
    else 
      group.add(index);
  }
  
  public String toString() {
    return Objects.toStringHelper("HeaderGroup")
      .add("Idxs", group)
      .add("Store", storage)
      .toString();
  }
  
  public void set(HeaderSetter<?> set) {
    for (int idx : getIndices()) {
      Pair<String, ValueSupplier> entry = 
        storage.lookup(idx);
      set.set(entry.one(), entry.two());
    }
  }
  
  public void set(
    HeaderSetter<?> set, 
    Set<Pair<String,ValueSupplier>> except) {
    for (int idx : getIndices()) {
      Pair<String, ValueSupplier> entry = 
        storage.lookup(idx);
      if (!except.contains(entry))
        set.set(entry.one(), entry.two());
    }
  }

  @Override
  public void popped(int idx) {
    this.group.remove(idx);
  }
}
