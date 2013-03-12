
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;

import snell.http2.frames.FrameReader;
import snell.http2.frames.SynStreamFrame;
import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.delta.DeltaHeaderSerializer;

public class Test {
  
  public static void main(String... args) throws Exception {
    


      Delta delta = new Delta(1);
      HeaderSerializer ser = 
          new DeltaHeaderSerializer(delta);
      DateTime dt = DateTime.now(DateTimeZone.UTC);
      
    ByteArrayOutputStream out = 
      new ByteArrayOutputStream();
    SynStreamFrame
      .create(
        ser, 
        true,
        1, 
        -1)
        .set(":method", "get")
        .set(":path", "/a")
        .set(":scheme", "https")
        .set("cookie", "a=b", "c=d")
        .set("foo", "bar", "baz")
        .set("baz", "bar")
        .set("date", dt)
        .writeTo(out);
    

      ByteArrayInputStream in = 
        new ByteArrayInputStream(
          out.toByteArray());
      FrameReader fr = 
        new FrameReader(ser);
      SynStreamFrame frame = 
        fr.nextFrame(in);

      for (String key : frame)
    	System.out.println(key + "\t" +frame.get(key));
  }

  
}
