package snell.http2.headers;

import org.joda.time.DateTime;

public interface HeaderSet<X extends HeaderSet<X>> 
  extends Iterable<String> {

  X set(String key, String... val);

  X set(String key, int val);
  
  X set(String key, long val);

  X set(String key, DateTime val);

  X set(String key, ValueProvider... val);

  boolean contains(String key, ValueProvider val);
  
  Iterable<ValueProvider> get(String key);
  
  int size();
  
}