
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;


import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.delta.DeltaHeaderSerializer;
import snell.http2.headers.delta.Huffman;
import snell.http2.headers.delta.Storage;
import snell.http2.utils.CountingReference;
import snell.http2.utils.ReferenceCounter;

public class Test {

  public static void main(String... args) throws Exception {
    byte group_id = 0x0;
    Delta delta = new Delta(
      group_id, 
      Huffman.REQUEST_TABLE);
    HeaderSerializer ser = 
      new DeltaHeaderSerializer(delta);
      
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();

    HeadersFrame frame = 
      HeadersFrame.make(ser)
        .useUtf8Headers()
        .streamId(6)
        .set(":method", "get")
        .set(":path", "/")
        .set(":host", "example.org")
        .set("user-agent", "M😄zilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
        .get();

    frame.writeTo(out);
    Frame.parse(new ByteArrayInputStream(out.toByteArray()), ser);
    
    System.out.println(Arrays.toString(out.toByteArray()));
    System.out.println(out.toByteArray().length);
    
    out = new ByteArrayOutputStream();
    
    frame = 
        HeadersFrame.make(ser)
          .useUtf8Headers()
          .streamId(6)
          .set(":method", "get")
          .set(":path", "/foo")
          .set(":host", "example.org")
          .set("user-agent", "M😄zilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
          .get();

      frame.writeTo(out);
  
    System.out.println(Arrays.toString(out.toByteArray()));

    ByteArrayInputStream in = 
        new ByteArrayInputStream(
          out.toByteArray());

    frame = Frame.parse(in, ser);
    
    for (String s : frame) {
      System.out.println(s + "\t" + frame.get(s));
    }

  }

  
}
