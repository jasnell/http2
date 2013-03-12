package snell.http2.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.google.common.base.Objects;
import com.google.common.collect.Sets;

/**
 * Essentially a priority queue... Orders items 
 * by the frequency of use, with items that have
 * been used the most at the tail, items that have 
 * been used the least at the head. If the maximum
 * number of items it reached, items at the head 
 * are dropped off.
 */
public abstract class MLU<X>{

  private int c = 0;
  private int offset = 0;
  private final int min_offset;
  
  // Maintains a ordered mapping of external ID 
  // values to internal storage locations the 
  // external IDs can change with every call to 
  // reindex(), but the internal storage locations 
  // remain constant until items are removed
  private IntMap index = new IntMap(256);
  private IntMap refcnt = new IntMap(256);
  private final List<X> store = 
      new ArrayList<X>(256);  
  private final Set<ReindexListener> listeners = 
    Sets.newLinkedHashSetWithExpectedSize(1);
  
  // Keep track of items we should not reindex,
  // We only reindex items that haven't been added 
  // or touched since the last call to reindex. 
  private final Set<Integer> no_reindex = 
    Sets.newHashSetWithExpectedSize(30);
  // Temporarily track which items are to be removed from the internal
  // Store. We only actually remove the items when reindex is called
  // in case there are old pointers still hanging around. 
  private final Set<Integer> to_remove = 
    Sets.newHashSetWithExpectedSize(30);
  
  
  protected MLU(int min_offset) {
    this.min_offset = min_offset;
    this.offset = min_offset;
  }
  
  public String toString() {
    return Objects.toStringHelper(
      this.getClass())
      .add("Store", store)
      .toString();
  }
  
  public int[] getIndex() {
    return index.keys();
  }
  
  /**
   * O(n) lookup of the current index for a given
   * value. Iterates over the index in reverse so that
   * the "most likely" values are matched first. Returns -1
   * if the item is not found
   */
  public int indexOf(X s) {
    int[] keys = index.keys();
    for (int idx = keys.length - 1; idx >= 0; idx--) {
      if (Objects.equal(get(idx), s))
        return idx;
    }
    return -1;
  }
  
  /**
   * Adds a new item, attempting to reserve space
   * first. If the item is successfully added, the
   * current index position of the item will be 
   * returned, otherwise -1 will be returned.
   */
  public int add(X s) {
    if (reserve(s)) {
      store.add(s);
      int pos = store.size() - 1;
      int idx = offset++;
      index.append(idx, pos);
      refcnt.append(idx,0);
      no_reindex.add(idx);
      c++;
      return idx;
    } else return -1;
  }
  
  protected void added(X s) {}
  
  /**
   * Reserves space for the given value, returns
   * true if the space was appropriate reserved, 
   * false if it wasn't. Subclasses should override
   * this to provide specific eviction policies...
   */
  protected boolean reserve(X s) {
    return true;
  }
  
  /**
   * Touch the item at the given index, this 
   * will increment the items internal counter
   * and will increase the likelihood that the 
   * item will be reindexed with a higher probability
   * on the next reindex call
   */
  public boolean touch(int i) {
    if (!index.contains(i))
      return false;
    if (no_reindex.contains(i))
      return true; // item has already been touched
    refcnt.inc(i);
    no_reindex.add(i);
    c++;
    return true;
  }
  
  /**
   * Gets the item at the given index position
   */
  public X get(int i) {
    if (!index.contains(i))
      return null;
    int pos = index.get(i);
    return store.get(pos);
  }

  /**
   * Removes an item from the MLU storage
   */
  public boolean pop() {
    return remove(0);
  }
  
  /**
   * Removes the item at the given index position
   * Note: the item is not immediately removed from the internal
   * store until the next time reindex is called.
   */
  public boolean remove(int i) {
    if (!index.contains(i))
      return false;
    int pos = index.get(i);
    if (pos == -1) 
      return false;
    X x = store.get(pos);
    to_remove.add(pos);
    index.delete(i);
    refcnt.delete(i);
    c--;
    removing(x);
    return true;
  }
  
  protected void removing(X item) {}
  
  /**
   * Returns the current MLU storage size
   */
  public int size() {
    return index.size();
  }
  
  /**
   * Reindexes the stored values, sorting items
   * by first calculating each items weight
   * relative to the other items in the store,
   * the performing a merge-sort on the key 
   * index. The stored items are never actually
   * moved around, just a new index order is 
   * assigned. An IntMap containing a listing
   * of the changed index positions in returned. 
   * Only indices that were actually changed are
   * included in the IntMap.
   */
  public IntMap reindex() {
    // Remove items from the internal storage... we wait to 
    // do this to ensure that all references are used and 
    // done prior to removing...
    store.removeAll(to_remove);
    to_remove.clear();
    // Check to see if there's anything to do...
    if (index.size() <= 1) {
      // Nope, clean up and return
      no_reindex.clear();
      return new IntMap(); // empty change set
    }
    
    // Determine the new sort order for the index.
    // This implementation uses a quicksort, calculating
    // the sort keys by calculating each items refcnt
    // by the internal op counter. There is an obvious
    // upper limit on this enforced. There can only be
    // a maximum of Integer.MAX_VALUE operations on this
    // particular implementation of MLU. In the future,
    // I will add an automatic adjustment to that so that
    // when refcnt's reach a specific threshold, we can 
    // reset the operation counter to a lower more reasonable
    // value to prevent possible overflow
    int[] new_key_order = 
      sort(index.keys());
    
    // clear the no_reindex set now that we've resorted everything
    no_reindex.clear();
    
    // prepare the new index storage...
    IntMap new_index = 
      new IntMap(
        new_key_order.length);
    IntMap new_refcnt = 
      new IntMap(
        new_key_order.length);
    IntMap revised_mapping = 
      new IntMap(
        new_key_order.length);
    
    // Loop through copying the old index state into the new index
    // Keep track of which items were changed so that we can notify
    // the HeaderGroup...
    for (int old_idx : new_key_order) {
      int new_idx = 
        new_index.appendWithOffset(
          min_offset,
          index.get(old_idx));
      new_refcnt.appendWithOffset(
        min_offset, 
        refcnt.get(old_idx));
      if (old_idx != new_idx)
        revised_mapping.put(
          old_idx, 
          new_idx);
    }
    this.index = new_index;
    this.refcnt = new_refcnt;
    this.offset = 
      min_offset + 
      this.index.size();
    
    // This will tell any subscribed HeaderGroups which indices
    // to update in their internal storage...
    this.notifyReindexListeners(
      revised_mapping);
    return revised_mapping;
  }
  
  private double p(int idx) {
    return no_reindex.contains(idx) ?
      1.0 : 
      refcnt.get(idx) / c;
  }
  
  private int partition(
    int[] keys, 
    int l, 
    int r) {
      int t;
      double pivot = 
        p(keys[(l + r)>>>1]); 
      while (l <= r) {
        while (p(keys[l]) < pivot)
          l++;
        while (p(keys[r]) > pivot)
          r--;
        if (l <= r) {
          t = keys[l];
          keys[l] = keys[r];
          keys[r] = t;
          l++;
          r--;
        }
      }; 
      return l;
  }
   
  private int[] sort(int[] keys) {
    sort(keys,0,keys.length-1);
    return keys;
  }
  
  private void sort(
    int[] keys, 
    int l, 
    int r) {
      int i = partition(keys, l, r);
      if (l<i-1)
        sort(keys, l, i - 1);
      if (i<r)
        sort(keys, i, r);
  }
  
  private void notifyReindexListeners(IntMap changes) {
    for (ReindexListener listener : listeners) {
      try {
        listener.reindexed(changes);
      } catch (Throwable t) {
        // TODO: Log this.. for now, ignore
      }
    }
  }
  
  public void addReindexListener(ReindexListener listener) {
    this.listeners.add(listener);
  }
  
  public static interface ReindexListener {
    void reindexed(IntMap changes);
  }
  
  public int updatesRemaining() {
    return Integer.MAX_VALUE - c;
  }
  
  public int offsetsRemaining() {
    return Integer.MAX_VALUE - offset;
  }
}
