package snell.http2.utils.delta;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.Huffman;

public final class DeltaHeaderSerializer
  implements HeaderSerializer {

  public final Delta delta;
  
  public DeltaHeaderSerializer(Delta delta) {
    this.delta = delta;
  }
  
  @Override
  public void serialize(
    OutputStream buffer, 
    HeaderSet<?> map)
      throws IOException {
    delta.encodeTo(buffer, map);
  }

  @Override
  public void deserialize(
    InputStream in, 
    HeaderSetter<?> set) 
      throws IOException {
    delta.decodeFrom(in, set, delta.huffman());
  }

  @Override
  public Huffman huffman() {
    return delta.huffman();
  }
}
