package snell.http2.headers;

import static com.google.common.base.Preconditions.checkNotNull;

import java.io.IOException;
import java.io.InputStream;

import org.joda.time.DateTime;

import com.google.common.base.Splitter;
import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMultimap;

@SuppressWarnings("rawtypes")
public interface HeaderSet<X extends HeaderSet<X>> 
  extends Iterable<String> {

  @SuppressWarnings("unchecked")
  public static abstract class HeaderSetBuilder
    <H extends HeaderSet<H>, B extends HeaderSetBuilder<H,B>> 
    implements Supplier<H>, 
               HeaderSetter<B> {

    protected ImmutableMultimap.Builder<String,ValueSupplier> map = 
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
    
    protected static String tlc(String key) {
      checkNotNull(key);
      return key.toLowerCase();
    }
    
    @Override
    public B set(String key, String... val) {
      key = tlc(key);
      if (val != null) {
        if (key.equalsIgnoreCase("cookie")) {
          for (String v : val) {
            for (String crumb : splitter.split(v))
              map.put(key, new StringValueSupplier(crumb));
          }
        } else {
          map.put(key, new StringValueSupplier(val));
        }
      }
      return (B)this;
    }

    @Override
    public B set(String key, int val) {
      map.put(tlc(key), new NumberValueSupplier(val));
      return (B)this;
    }
    
    @Override
    public B set(String key, long val) {
      map.put(tlc(key), new NumberValueSupplier(val));
      return (B)this;
    }
    
    @Override
    public B set(String key, DateTime val) {
      map.put(tlc(key), new DateTimeValueSupplier(val));
      return (B)this;
    }

    @Override
    public B set(String key, ValueSupplier... val) {
      key = tlc(key);
      if (val != null) {
        for (ValueSupplier v : val)
          map.put(key,v);
      }
      return (B)this;
    }

  }
  
  boolean contains(String key, ValueSupplier val);
  
  Iterable<ValueSupplier> get(String key);
  
  int size();
  
}