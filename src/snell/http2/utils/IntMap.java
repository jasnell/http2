package snell.http2.utils;

import java.util.Arrays;

public final class IntMap {

  private int[] keys, vals;
  private int size;
  private final int growth_factor;
  
  public IntMap() {
    this(20);
  }
  
  public IntMap(int initial_capacity) {
    reset(initial_capacity);
    growth_factor = Math.max(1, (int)(initial_capacity*0.25));
  }
  
  public void reset(int initial_capacity) {
    int ic = Math.max(1,initial_capacity);
    this.keys = new int[ic];
    this.vals = new int[ic];
    size = 0;
  }
  
  private void remove(int idx) {
    System.arraycopy(keys, idx + 1, keys, idx, size - (idx + 1));
    System.arraycopy(vals, idx + 1, vals, idx, size - (idx + 1));
    size--;
  }
  
  public void put(int key, int val) {
    int i = bs(keys, 0, size, key);
    if (i >= 0)
      vals[i] = val;
    else {
      i = ~i;
      if (size >= keys.length) growBy(1);
      if (size - i != 0) {
        System.arraycopy(keys, i, keys, i + 1, size - i);
        System.arraycopy(vals, i, vals, i + 1, size - i);
      }
      keys[i] = key;
      vals[i] = val;
      size++;
    }
  }
  
  public int size() {
    return size;
  }
  
  public int get(int key, int def) {
    int i = bs(keys, 0, size, key);
    return i < 0 ? def : vals[i];

  }
  
  public int get(int key) {
    return get(key,0);
  }
  
  public void inc(int key) {
    put(key,get(key,0) + 1);
  }
  
  public void dec(int key) {
    dec(key,0);  
  }
  
  public void dec(int key, int min) {
    put(key,Math.max(0,get(key,0)-1));
  }
  
  public boolean contains(int key) {
    return bs(keys,0,size,key) >= 0;
  }
  
  public boolean delete(int key) {
    int i = bs(keys, 0, size, key);
    if (i >= 0) {
      remove(i);
      return true;
    } else return false;
  }
  
  private void growBy(int c) {
    int n = size + c;
    int[] nkeys = new int[n];
    int[] nvals = new int[n];
    System.arraycopy(keys, 0, nkeys, 0, keys.length);
    System.arraycopy(vals, 0, nvals, 0, vals.length);
    keys = nkeys;
    vals = nvals;
  }
  
  public void append(int key, int value) {
    if (size != 0 && key <= keys[size - 1]) {
      put(key, value);
      return;
    }
    int pos = size;
    if (pos >= keys.length)
      growBy(growth_factor); //growBy(1);
    keys[pos] = key;
    vals[pos] = value;
    size = pos + 1;
  }
  
  public int append(int value) {
    int idx = size();
    append(idx,value);
    return idx;
  }
  
  public int appendWithOffset(int offset, int value) {
    int idx = offset + size();
    append(idx,value);
    return idx;
  }
  
  private static int bs(
    int[] a, 
    int s, 
    int l, 
    int k) {
      int h = s + l, 
          b = s - 1, 
          g;
      while (h - b > 1) {
        g = (h + b) / 2;
        if (a[g] < k) b = g;
        else h = g;
      }
      if (h == s + l) 
        return ~(s + l);
      else if (a[h] == k)
        return h;
      else
        return ~h;
  }
  
  public int[] notMatching(int val) {
    int[] res = new int[size];
    int pos = 0;
    for (int n = 0; n < size; n++) {
      if (vals[n] != val)
        res[pos++] = keys[n];
    }
    int[] nres = new int[pos];
    System.arraycopy(res, 0, nres, 0, pos);
    return nres;
  }
  
  public boolean isEmpty() {
    return size == 0;
  }
  
  public int[] keys() {
    return Arrays.copyOf(keys, size());
  }
  
  public String toString() {
    StringBuilder buf = new StringBuilder();
    buf.append('{');
    for (int n = 0; n < size; n++) {
      if (n > 0) buf.append(", ");
      buf.append(keys[n]);
      buf.append('=');
      buf.append(vals[n]);
    }
    buf.append('}');
    return buf.toString();
  }
  
}
