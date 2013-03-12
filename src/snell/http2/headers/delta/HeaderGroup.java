package snell.http2.headers.delta;

import java.util.LinkedHashSet;
import java.util.Set;

import com.google.common.base.Objects;

import snell.http2.headers.HeaderSet;
import snell.http2.headers.ValueProvider;
import snell.http2.utils.IntMap;
import snell.http2.utils.Pair;
import snell.http2.utils.MLU.ReindexListener;

/**
 * Header Group represents a currently active set of Headers
 */
public final class HeaderGroup 
  implements ReindexListener {

  private Set<Integer> group = 
    new LinkedHashSet<Integer>();
  private final Storage storage;
  
  public HeaderGroup(Storage storage) {
    this.storage = storage;
    storage.addReindexListener(this);
  }
  
  public Storage storage() {
    return storage;
  }
  
  public boolean hasKV(String key, ValueProvider val) {
    int index = storage.lookupIndex(key, val);
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
  
  public void set(HeaderSet<?> set) {
    for (int idx : getIndices()) {
      Pair<String, ValueProvider> entry = 
        storage.lookupfromIndex(idx);
      set.set(entry.one(), entry.two());
    }
  }

  @Override
  /**
   * Called when the underlying MLU storage cache
   * has been reindexed.. updates the internal HeaderGroup
   * ID's to match the current state of the MLU index
   */
  public void reindexed(IntMap changes) {
    Set<Integer> new_dict = 
      new LinkedHashSet<Integer>();
    for (int key : group)
      new_dict.add(
        changes.contains(key)?
          changes.get(key):
          key);
    this.group = new_dict;
  }
}
