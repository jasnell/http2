package snell.http2.headers.delta;

import com.google.common.base.Objects;

import snell.http2.headers.ValueProvider;
import snell.http2.utils.Pair;
import snell.http2.utils.MLU.ReindexListener;

public final class Storage {
  
  private final StringValueMLU static_mlu = 
    new DefaultStaticMLU();
  private final StringValueMLU main_mlu =
    new StringValueMLU(
      16 * 1024, 
      1024, 
      static_mlu.size());

  public String toString() {
    return Objects.toStringHelper("Storage")
      .add("MLU", main_mlu)
      .toString();
  }
  
  protected void addReindexListener(
    ReindexListener listener) {
      main_mlu.addReindexListener(listener);
  }

  public int lookupIndex(String key, ValueProvider val) {
    Pair<Integer,Integer> pair = findEntryIdx(key,val);
    return pair.two() != null ? pair.two() : -1;
  }
  
  public Pair<String, ValueProvider> lookupfromIndex(int seq_num) {
    return seq_num < static_mlu.size() ?
        static_mlu.get(seq_num) :
        main_mlu.get(seq_num);
  }
  
  public String lookupKeyFromIndex(int seq_num) {
    Pair<String, ValueProvider> entry = 
      lookupfromIndex(seq_num);
    return entry != null ? entry.one() : null;
  }
  
  public int insertVal(String key, ValueProvider val) {
    return main_mlu.add(key, val);
  }
  
  public boolean touchIdx(int idx) {
    return idx < static_mlu.size() ?
      false : main_mlu.touch(idx);
  }
  
  public void reindex() {
    main_mlu.reindex();
  }
  
  public Pair<Integer,Integer> findEntryIdx(String key, ValueProvider val) {
    Pair<Integer,Integer> pair = 
      main_mlu.findEntryIndices(key, val);
    int i1 = -1, i2 = -1;
    if (pair.one() != -1) {
      i1 = pair.one();
      if (pair.two() != -1)
        i2 = pair.two();
      else {
        pair = this.static_mlu.findEntryIndices(key, val);
        if (pair.two() != -1)
          i2 = pair.two();
      }
    } else {
      pair = this.static_mlu.findEntryIndices(key, val);
      if (pair.one() != -1)
        i1 = pair.one();
      if (pair.two() != -1)
        i2 = pair.two();
    }
    return Pair.<Integer,Integer>of(i1,i2);
  }
  
}
