package snell.http2.headers.dhe;

import java.io.IOException;
import java.io.OutputStream;

import snell.http2.headers.NumberValueSupplier;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.ValueSupplier;


class StaticStorage 
  extends Storage {
  
  StaticStorage() {
    super(null);
    loadDefaults();
  }
  
  @Override
  public byte push(String name, ValueSupplier<?> value) {
    throw new UnsupportedOperationException();
  }

  private void loadDefaults() {
    super.push("date", NULLVP);
    super.push(":scheme", StringValueSupplier.create("https"));
    super.push(":scheme", StringValueSupplier.create("http"));
    super.push(":scheme", StringValueSupplier.create("ftp"));
    super.push(":method", StringValueSupplier.create("get"));
    super.push(":method", StringValueSupplier.create("post"));
    super.push(":method", StringValueSupplier.create("put"));
    super.push(":method", StringValueSupplier.create("delete"));
    super.push(":method", StringValueSupplier.create("options"));
    super.push(":method", StringValueSupplier.create("patch"));
    super.push(":method", StringValueSupplier.create("connect"));
    super.push(":path", StringValueSupplier.create("/"));
    super.push(":host", NULLVP);
    super.push("cookie", NULLVP);
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
      super.push(":status", NumberValueSupplier.create(c));
    super.push(":status-text", StringValueSupplier.create("OK"));
    super.push(":version", StringValueSupplier.create("1.1"));    
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
        "content-disposition",
        "content-language",
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
        "link",
        "prefer",
        "preference-applied",
        "accept-patch"
        
    };
    for (String n : nulls)
      super.push(n, NULLVP);
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
