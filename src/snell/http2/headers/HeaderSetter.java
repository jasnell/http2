package snell.http2.headers;

import org.joda.time.DateTime;

@SuppressWarnings("rawtypes")
public interface HeaderSetter<B extends HeaderSetter<B>> {

  public abstract B set(String key, String... val);

  public abstract B set(String key, int val);

  public abstract B set(String key, long val);

  public abstract B set(String key, DateTime val);

  public abstract B set(String key, ValueSupplier... val);

}