package snell.http2.headers;

import java.io.IOException;
import java.io.InputStream;

import org.joda.time.DateTime;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultimap;

public interface HeaderSet<X extends HeaderSet<X>> 
  extends Iterable<String> {

  @SuppressWarnings("unchecked")
  public static abstract class HeaderSetBuilder
    <H extends HeaderSet<H>, B extends HeaderSetBuilder<H,B>> 
    implements Supplier<H>, 
               HeaderSetter<B> {

    protected ImmutableMultimap.Builder<String,ValueProvider> map = 
      ImmutableMultimap.builder();
    protected HeaderSerializer ser;
    
    public B parse(
      InputStream in) 
        throws IOException {
      ser.deserialize(in, this);
      return (B)this;
    }
    
    public B serializer(HeaderSerializer ser) {
      this.ser = ser;
      return (B)this;
    }
    
    private static final Splitter splitter = 
      Splitter
        .on(';')
        .omitEmptyStrings()
        .trimResults();
    
    @Override
    public B set(String key, String... val) {
      if (val != null) {
        if (key.equalsIgnoreCase("cookie")) {
          for (String v : val) {
            for (String crumb : splitter.split(v))
              map.put(key, new StringValueProvider(crumb));
          }
        } else {
          map.put(key, new StringValueProvider(val));
        }
      }
      return (B)this;
    }

    @Override
    public B set(String key, int val) {
      map.put(key, new NumberValueProvider(val));
      return (B)this;
    }
    
    @Override
    public B set(String key, long val) {
      map.put(key, new NumberValueProvider(val));
      return (B)this;
    }
    
    @Override
    public B set(String key, DateTime val) {
      map.put(key, new DateTimeValueProvider(val));
      return (B)this;
    }

    @Override
    public B set(String key, ValueProvider... val) {
      if (val != null) {
        for (ValueProvider v : val)
          map.put(key,v);
      }
      return (B)this;
    }

  }
  
  boolean contains(String key, ValueProvider val);
  
  Iterable<ValueProvider> get(String key);
  
  int size();
  
}