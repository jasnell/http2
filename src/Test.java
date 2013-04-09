
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;


import snell.http2.frames.Frame;
import snell.http2.frames.HeadersFrame;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.delta.DeltaHeaderSerializer;
import snell.http2.headers.delta.Huffman;
import snell.http2.utils.RangedIntegerSupplier;

public class Test {

  public static void main(String... args) throws Exception {
    final RangedIntegerSupplier stream_ids = 
      RangedIntegerSupplier
        .createAllEvenIntegers();
    
    DateTime now = DateTime.now(DateTimeZone.UTC);
    
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
        .fin()
        .useUtf8Headers()
        .streamId(stream_ids.next())
        .set(":method", "get")
        .set(":path", "/")
        .set(":host", "example.org")
        .set("foo", 123)
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
          .fin()
          .streamId(stream_ids.next())
          .set(":method", "get")
          .set(":path", "/foo")
          .set(":host", "example.org")
          .set("foo", 123)
          .set("bar", now)
          .set("baz", new byte[] {1,2,3,4})
          .set("user-agent", "M😄zilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
          .get();

      frame.writeTo(out);
  
    System.out.println(Arrays.toString(out.toByteArray()));

    ByteArrayInputStream in = 
        new ByteArrayInputStream(
          out.toByteArray());

    frame = Frame.parse(in, ser);
    
    System.out.println(frame.fin());
    
    for (String s : frame) {
      System.out.println(s + "\t" + frame.get(s));
    }

  }

  
}
