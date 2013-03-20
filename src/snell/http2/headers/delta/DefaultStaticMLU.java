package snell.http2.headers.delta;

import java.io.IOException;
import java.io.OutputStream;

import snell.http2.headers.NumberValueProvider;
import snell.http2.headers.StringValueProvider;
import snell.http2.headers.ValueProvider;
import snell.http2.utils.IntMap;
import snell.http2.utils.Pair;

public class DefaultStaticMLU 
  extends StringValueMLU {

  private final boolean locked;
  
  public DefaultStaticMLU() {
    super(0, 0, 0);    
    loadDefaults();
    this.locked = true;
  }

  private void checkLocked() {
    if (locked)
      throw new UnsupportedOperationException();
  }
  
  @Override
  protected boolean reserve(Pair<String, ValueProvider> s) {
    checkLocked();
    return super.reserve(s);
  }

  @Override
  public int add(String key, ValueProvider val) {
    checkLocked();
    return super.add(key, val);
  }

  @Override
  public int add(Pair<String, ValueProvider> s) {
    checkLocked();
    return super.add(s);
  }

  @Override
  public boolean touch(int i) {
    checkLocked();
    return super.touch(i);
  }

  @Override
  public boolean pop() {
    checkLocked();
    return super.pop();
  }

  @Override
  public boolean remove(int i) {
    checkLocked();
    return super.remove(i);
  }

  @Override
  public IntMap reindex() {
    checkLocked();
    return super.reindex();
  }

  private void loadDefaults() {
    add("date", NULLVP);
    add(":scheme", StringValueProvider.create("https"));
    add(":scheme", StringValueProvider.create("http"));
    add(":scheme", StringValueProvider.create("ftp"));
    add(":method", StringValueProvider.create("get"));
    add(":method", StringValueProvider.create("post"));
    add(":method", StringValueProvider.create("put"));
    add(":method", StringValueProvider.create("delete"));
    add(":method", StringValueProvider.create("options"));
    add(":method", StringValueProvider.create("patch"));
    add(":method", StringValueProvider.create("connect"));
    add(":path", StringValueProvider.create("/"));
    add(":host", NULLVP);
    add("cookie", NULLVP);
    int[] codes = 
      new int[] {
        100, 101, 102, 200, 201, 202, 203,
        204, 205, 206, 207, 208,
        300, 301, 302, 303, 304, 305,
        307, 308, 400, 401, 402, 403, 404,
        405, 406, 407, 408, 409, 410, 411,
        412, 413, 414, 415, 416, 417, 500,
        501, 502, 503, 504, 505
      };
    for (int c : codes)
      add(":status", NumberValueProvider.create(c));
    add(":status-text", StringValueProvider.create("OK"));
    add(":version", StringValueProvider.create("1.1"));    
    String[] nulls = new String[] {
        "accept",
        "accept-charset",
        "accept-encoding",
        "accept-language",
        "accept-ranges",
        "allow",
        "authorization",
        "cache-control",
        "content-base",
        "content-encoding",
        "content-length",
        "content-location",
        "content-md5",
        "content-range",
        "content-type",
        "etag",
        "expect",
        "expires",
        "from",
        "if-match",
        "if-modified-since",
        "if-none-match",
        "if-range",
        "if-unmodified-since",
        "last-modified",
        "location",
        "max-forwards",
        "origin",
        "pragma",
        "proxy-authenticate",
        "proxy-authorization",
        "range",
        "referer",
        "retry-after",
        "server",
        "set-cookie",
        "status",
        "te",
        "trailer",
        "transfer-encoding",
        "upgrade",
        "user-agent",
        "user-agent",
        "vary",
        "via",
        "warning",
        "www-authenticate",
        "access-control-allow-origin",
        "content-disposition",
        "get-dictionary",
        "p3p",
        "x-content-type-options",
        "x-frame-options",
        "x-powered-by",
        "x-xss-protection"
    };
    for (String n : nulls)
      add(n, NULLVP);
  }

  /**
   * A Null ValueProvider that serves as a standin for headers that
   * have no default value defined. This would be guaranteed not to
   * match any provided value...
   */
  private static ValueProvider NULLVP = new ValueProvider() {
    public void writeTo(
      OutputStream buffer) 
        throws IOException {}
    public int flags() {
      return 0;
    }    
  };
}
