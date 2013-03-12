package snell.http2.headers.delta;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.io.ByteStreams.nullOutputStream;

import com.google.common.base.Objects;
import com.google.common.base.Throwables;
import com.google.common.io.CountingOutputStream;

import snell.http2.headers.ValueProvider;
import snell.http2.utils.MLU;
import snell.http2.utils.Pair;

public class StringValueMLU 
  extends MLU<Pair<String,ValueProvider>> {

  private final int max_items;
  private final long max_size;
  private long cur_size = 0;
  
  public StringValueMLU(
    int max_items, 
    long max_size,
    int offset) {
      super(offset);
      this.max_items = max_items;
      this.max_size = max_size;
  }
  
  protected boolean reserve(
    Pair<String, ValueProvider> s) {
    boolean reindex = false;
    long size = size(s);
    checkArgument(
      this.max_items == 0 || this.max_items >= 1, 
      "max_items too small");
    checkArgument(
      this.max_size == 0 || this.max_size >= size, 
      "max_bytes too small");
    if (this.max_items > 0) {
      int c = size() - this.max_items;
      while(c-- > 0)
        if (!pop())
          return false;
        else reindex = true;
    }
    if (this.max_size > 0) {
      while (this.max_size < cur_size + size)
        if (!pop())
          return false;
        else reindex = true;
    }
    if (reindex) reindex();
    return true;
  }
  
  public Pair<Integer,Integer> findEntryIndices(
    String key, 
    ValueProvider val) {
    int a = -1, b = -1;
    int[] index = this.getIndex();
    for (int n = index.length - 1; n >= 0; n--) {
      Pair<String,ValueProvider> pair =
        this.get(index[n]);
      if (pair != null && pair.one().equals(key)) {
        a = index[n];
        for (int j = n; j >= 0; j--) {
          pair = this.get(index[j]);
          if (Objects.equal(val,pair.two()) &&
              Objects.equal(key,pair.one())) {
            b = index[j];
            break;
          }
        }
      }
    }
    return Pair.of(a, b);
    }

  public int add(String key, ValueProvider val) {
    return add(Pair.of(key, val));
  }
  
  public int indexOf(String key, ValueProvider val) {
    return indexOf(Pair.of(key, val));
  }
 
  @Override
  protected void added(Pair<String, ValueProvider> item) {
    cur_size += size(item);
  }

  @Override
  protected void removing(Pair<String, ValueProvider> item) {
    cur_size -= size(item);
  }

  private static long size(Pair<String,ValueProvider> s) {
    try {
      CountingOutputStream cout = 
        new CountingOutputStream(
          nullOutputStream());
      s.two().writeTo(cout);
      return cout.getCount();
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
  }
}
