package snell.http2.headers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public interface HeaderSerializer {

  void serialize(
    OutputStream buffer, 
    HeaderSet<?> map) 
      throws IOException;
  
  void deserialize(
    InputStream in, 
    HeaderSet<?> set) 
      throws IOException;
  
}
