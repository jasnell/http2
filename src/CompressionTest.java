import static com.google.common.base.Throwables.propagate;

import static snell.http2.utils.MiscUtilities.httpDateToJodaTime;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.joda.time.DateTime;

import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;

import snell.http2.headers.HeaderBlock;
import snell.http2.headers.HeaderBlock.HeaderBlockBuilder;
import snell.http2.headers.delta.Delta;

public final class CompressionTest {
  
  private static final Delta req_delta = 
    Delta.forRequest();
  private static final Delta res_delta = 
    Delta.forResponse();
  
  private static final ImmutableSet<String> NUMS = 
    ImmutableSet.of(
      "content-length", 
      "max-forwards",
      "age"
    );
  
  private static final ImmutableSet<String> DATES = 
    ImmutableSet.of(
      "date",
      "last-modified",
      "if-modified-since",
      "if-unmodified-since",
      "expires");
  
  private static final ImmutableSet<String> DATE_OR_NUM = 
    ImmutableSet.of(
      "retry-after");
  
  public static void main(String... args) {
    try {
      int c = 2;
      while(c > 0) {
        Delta delta = null;
        HeaderBlockBuilder blockBuilder = null;
        boolean is_req = true;
        BufferedReader line_reader = readFully();
        String line = null;
        while((line = line_reader.readLine()) != null) {
          if (delta == null) {
            String[] tokens = line.split("\\s",3);
            if (tokens[0].equalsIgnoreCase("HTTP")) {
              delta = res_delta;
              is_req = false;
            } else {
              delta = req_delta;
              is_req = true;
            }
            blockBuilder = 
              HeaderBlock.make(delta.ser());
            set_header_line(
              blockBuilder,
              tokens,
              is_req);
          }
          if (line.trim().length() == 0)
            break;
          int i = line.indexOf(':', 1);
          if (i > -1) {
            String key = line.substring(0,i);
            String val = line.substring(i+2).trim();
            if (NUMS.contains(key)) {
              set_num_val(blockBuilder,key,val);
            } else if (DATES.contains(key)) {
              set_date_val(blockBuilder,key,val);
            } else if (DATE_OR_NUM.contains(key)) {
              if (val.matches("\\d+")) {
                set_num_val(blockBuilder,key,val);
              } else {
                set_date_val(blockBuilder,key,val);
              }
            } else {
              blockBuilder.set(key, val);
            }
          }
        }
        if (blockBuilder != null) {
          blockBuilder
            .get()
            .writeTo(System.out);
          System.out.println("\n");
          c = 2;
        } else {
          c--;
        }
      }
    } catch (Throwable t) {
      throw propagate(t);
    }
  }
  
  private static void set_date_val(
    HeaderBlockBuilder builder,
    String key,
    String val) {
    try {
      DateTime dt = httpDateToJodaTime.apply(val.trim());
      builder.set(key,dt);
    } catch (Throwable t) {
      builder.set(key,val);
    }
  }
  
  private static void set_num_val(
    HeaderBlockBuilder builder, 
    String key, 
    String val) {
    try {
      long v = Long.parseLong(val);
      builder.set(key, v);
    } catch (Throwable t) {
      builder.set(key, val);
    }
  }
  
  private static void set_header_line(
    HeaderBlockBuilder builder, 
    String[] tokens, 
    boolean is_req) {
    if (is_req) {
      builder.set(":method", tokens[0].toLowerCase())
             .set(":path", tokens[1]);
    } else {
      String _status = tokens[1];
      int status = 200;
      try {
        status = Integer.parseInt(_status.trim());
      } catch (Throwable t) {}
      builder.set(":status", status);
    }
  }
  
  private static BufferedReader readFully() {
    try {
      InputStreamReader reader = 
        new InputStreamReader(
          System.in, "ISO-8859-1");
      return new BufferedReader(reader);
    } catch (Throwable t) {
      throw Throwables.propagate(t);
    }
    
  }
}
