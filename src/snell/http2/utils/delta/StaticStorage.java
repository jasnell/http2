package snell.http2.utils.delta;

import java.io.IOException;
import java.io.OutputStream;

import snell.http2.headers.NumberValueSupplier;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.ValueSupplier;

@SuppressWarnings("rawtypes")
public final class StaticStorage 
  extends Storage {

  private volatile static Storage instance = null;
  
  public static Storage getInstance() {
    if (instance == null) {
      synchronized(StaticStorage.class) {
        instance = new StaticStorage();
      }
    }
    return instance;
  }
  
  private StaticStorage() {
    loadDefaults();
  }
  
  @Override
  public void addListener(PopListener listener) {
    throw new UnsupportedOperationException();
  }
  
  @Override
  public void clear() {
    throw new UnsupportedOperationException();
  }

  @Override
  public int store(String key, ValueSupplier val) {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean pop() {
    throw new UnsupportedOperationException();
  }

  private void loadDefaults() {
    super.store("date", NULLVP);
    super.store(":scheme", StringValueSupplier.create("https"));
    super.store(":scheme", StringValueSupplier.create("http"));
    super.store(":scheme", StringValueSupplier.create("ftp"));
    super.store(":method", StringValueSupplier.create("get"));
    super.store(":method", StringValueSupplier.create("post"));
    super.store(":method", StringValueSupplier.create("put"));
    super.store(":method", StringValueSupplier.create("delete"));
    super.store(":method", StringValueSupplier.create("options"));
    super.store(":method", StringValueSupplier.create("patch"));
    super.store(":method", StringValueSupplier.create("connect"));
    super.store(":path", StringValueSupplier.create("/"));
    super.store(":host", NULLVP);
    super.store("cookie", NULLVP);
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
      super.store(":status", NumberValueSupplier.create(c));
    super.store(":status-text", StringValueSupplier.create("OK"));
    super.store(":version", StringValueSupplier.create("1.1"));    
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
      super.store(n, NULLVP);
  }

  /**
   * A Null ValueSupplier that serves as a standin for headers that
   * have no default value defined. This would be guaranteed not to
   * match any provided value...
   */
  private static ValueSupplier<Void> NULLVP = 
    new ValueSupplier<Void>((byte)0x0) {
    public void writeTo(
      OutputStream buffer) 
        throws IOException {}
    public byte flags() {
      return 0x0;
    }
    @Override
    public Void get() {
      return null;
    }
    @Override
    public int length() {
      return 0;
    }    
  };
}
