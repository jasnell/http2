import static org.joda.time.DateTime.now;
import static org.joda.time.DateTimeZone.UTC;
import static snell.http2.utils.RangedIntegerSupplier.forAllEvenIntegers;
import static snell.http2.frames.Frame.parse;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;

import snell.http2.frames.HeadersFrame;
import snell.http2.headers.Huffman;
import snell.http2.headers.StringValueSupplier;
import snell.http2.headers.delta.Delta;
import snell.http2.headers.dhe.CommonPrefixStringValueSupplier;
import snell.http2.headers.dhe.CommonPrefixStringValueSupplier.CommonPrefixStringValueParser;
import snell.http2.headers.dhe.Storage;
import snell.http2.utils.BitBucket;
import snell.http2.utils.RangedIntegerSupplier;

public class Test {
  
  public static void main(String... args) throws Exception {
  
    Huffman huffman = Huffman.REQUEST_TABLE;
    
    Storage storage = new Storage();
    storage.push("test", StringValueSupplier.create("hello;there"));
    
    StringValueSupplier s = 
      StringValueSupplier.create(true,"hello;","hello;howdyhi!");
    
    CommonPrefixStringValueSupplier c = 
      new CommonPrefixStringValueSupplier(
        huffman,true,"test",s,storage);
    
    ByteArrayOutputStream b = new ByteArrayOutputStream();
    
    c.writeTo(b);
    
    System.out.println(Arrays.toString(b.toByteArray()));
    
    ByteArrayInputStream in = new ByteArrayInputStream(b.toByteArray());
    
    CommonPrefixStringValueParser parser = 
      new CommonPrefixStringValueParser()
        .useHuffman(huffman)
        .usingStorage(storage)
        .forName("test");

    c = parser.parse(in, (byte)0x21);
    
    System.out.println(c);
    
//    final RangedIntegerSupplier stream_ids = 
//      forAllEvenIntegers();
//
//    final Delta delta = 
//      Delta.forRequest();
//      
//    ByteArrayOutputStream out = 
//      new ByteArrayOutputStream();
//
//    HeadersFrame.make(delta.ser())
//      .streamId(stream_ids)
//      .set(":method", "get")
//      .set(":path", "/")
//      .set(":host", "example.org")
//      .set("foo", 123)
//      .set("user-agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
//      .fin()
//      .get()
//      .writeTo(out);
//    
//    parse(
//      new ByteArrayInputStream(
//        out.toByteArray()), 
//        delta.ser());
//
//    out = new ByteArrayOutputStream();
//    
//    HeadersFrame.make(delta.ser())
//      .useUtf8Headers()                   // Enables extended characters in header strings, but disables huffman coding for this frame only
//      .streamId(stream_ids)
//      .set(":method", "get")
//      .set(":method", "put")              // Strings are multi-valued
//      .set(":path", "/foo")
//      .set(":host", "example.org")
//      .set("foo", 123)                    // Number values
//      .set("bar", now(UTC))               // DateTime values
//      .set("baz", new byte[] {1,2,3,4})   // Raw Binary values
//      .set("user-agent", "M😄zilla/5.0 (Macintosh; Intel Mac OS X 10.8; rv:16.0) Gecko/20100101 Firefox/16.0")
//      .fin()
//      .get()
//      .writeTo(out);
//
//    ByteArrayInputStream in = 
//      new ByteArrayInputStream(
//        out.toByteArray());
//
//    HeadersFrame frame = 
//      parse(
//        in, 
//        delta.ser());
//    
//    System.out.println(frame.fin());
//    
//    for (String s : frame) {
//      System.out.println(s + "\t" + frame.get(s));
//    }

  }

  
}
