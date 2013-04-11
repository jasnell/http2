package snell.http2.utils;

import java.io.IOException;
import java.io.Writer;
import java.io.OutputStream;

import static org.apache.commons.codec.binary.Hex.encodeHex;

public class HexWriter 
  extends OutputStream {

  private final Writer writer;
  
  public HexWriter(
    Writer writer) {
      this.writer = writer;
  }
  
  @Override
  public void write(int b) throws IOException {
    writer.write(encodeHex(b(b)));
  }
  
  @Override
  public void write(byte[] b, int off, int len) throws IOException {
    byte[] n = new byte[len];
    System.arraycopy(b, 0, n, 0, len);
    writer.write(encodeHex(n));
  }

  @Override
  public void write(byte[] b) throws IOException {
    writer.write(encodeHex(b));
  }

  private static byte[] b(int b) {
    return new byte[] {(byte)b};
  }

  @Override
  public void close() throws IOException {
    writer.close();
    super.close();
  }

  @Override
  public void flush() throws IOException {
    writer.flush();
    super.flush();
  }
  
  
}
