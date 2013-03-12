package snell.http2.headers;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Iterator;

import org.joda.time.DateTime;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;

/**
 * Storage for headers... pretty simple
 */
public final class HeaderBlock 
  implements HeaderSet<HeaderBlock> {

  private final HeaderSerializer ser;
  
  public HeaderBlock(HeaderSerializer ser) {
    this.ser = ser;
  }
  
  private Multimap<String,ValueProvider> map = 
    LinkedHashMultimap.create();
  
  public void writeTo(OutputStream buf) throws IOException {
    ser.serialize(buf, this);
  }
 
  public int size() {
    return map.size();
  }
    
  private static final Splitter splitter = 
    Splitter
      .on(';')
      .omitEmptyStrings()
      .trimResults();
  
  public HeaderBlock set(String key, String... val) {
    if (key.equalsIgnoreCase("cookie")) {
      for (String v : val) {
        for (String crumb : splitter.split(v))
          map.put(key, new StringValueProvider(crumb));
      }
    } else {
      map.put(key, new StringValueProvider(val));
    }
    return this;
  }
  
  @Override
  public HeaderBlock set(String key, int val) {
    map.replaceValues(key, ImmutableList.of(new NumberValueProvider(val)));
    return this;
  }
  
  @Override
  public HeaderBlock set(String key, long val) {
    map.replaceValues(key, ImmutableList.of(new NumberValueProvider(val)));
    return this;
  }
  
  @Override
  public HeaderBlock set(String key, DateTime val) {
    map.replaceValues(key, ImmutableList.of(new DateTimeValueProvider(val)));
    return this;
  }

  @Override
  public HeaderBlock set(String key, ValueProvider... val) {
    if (val == null) return this;
    for (ValueProvider v : val) {
      if (v instanceof StringValueProvider)
        map.put(key,v);
      else 
        map.replaceValues(key, ImmutableList.of(v));
    }
    return this;
  }

  @Override
  public Iterable<ValueProvider> get(String key) {
    return map.get(key);
  }

  @Override
  public Iterator<String> iterator() {
    return map.keySet().iterator();
  }

  @Override
  public boolean contains(String key, ValueProvider val) {
    if (!map.containsKey(key)) return false;
    return map.get(key).contains(val);
  }
}
