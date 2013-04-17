package snell.http2.headers;
import static snell.http2.headers.StringValueSupplier.create;
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
    protected final HeaderSerializer ser;
    protected Huffman huffman;
    
    protected HeaderSetBuilder(HeaderSerializer ser) {
      checkNotNull(ser);
      this.ser = ser;
      this.huffman = ser.huffman();
    }
    
    public B parse(
      InputStream in) 
        throws IOException {
      ser.deserialize(in, this);
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
        
    private StringValueSupplier c(String v) {
      return create(huffman,v);
    }
    
    private StringValueSupplier c(String... v) {
      return create(huffman,v);
    }
    
    @Override
    public B set(String key, String... val) {
      key = tlc(key);
      if (val != null) {
        if (key.equalsIgnoreCase("cookie")) {
          for (String v : val)
            for (String crumb : splitter.split(v))
              map.put(key, c(crumb));
        } else
          map.put(key, c(val));
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

    @Override
    public B set(String key, byte[] val) {
      return set(key, BinaryValueSupplier.create(val));
    }
    
    public B set(String key, InputStream in, int c) throws IOException {
      return set(key, BinaryValueSupplier.create(in,c));
    }
    
  }
  
  boolean contains(String key, ValueSupplier val);
  
  Iterable<ValueSupplier> get(String key);
  
  int size();
  
}