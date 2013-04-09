package snell.http2.headers;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.headers.delta.Huffman;

public interface HeaderSerializer {

  Huffman huffman();
  
  void serialize(
    OutputStream buffer, 
    HeaderSet<?> map) 
      throws IOException;
  
  void deserialize(
    InputStream in, 
    HeaderSetter<?> set) 
      throws IOException;
  
}
