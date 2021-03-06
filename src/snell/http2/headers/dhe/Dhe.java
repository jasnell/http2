package snell.http2.headers.dhe;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static snell.http2.headers.dhe.Header.TYPE_CLONE;
import static snell.http2.headers.dhe.Header.TYPE_INDEX;
import static snell.http2.headers.dhe.Header.TYPE_LITERAL;
import static snell.http2.headers.dhe.Header.TYPE_RANGE;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.Huffman;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.dhe.Header.BuilderContext;
import snell.http2.headers.dhe.Header.Clone;
import snell.http2.headers.dhe.Header.CloneInstance;
import snell.http2.headers.dhe.Header.Index;
import snell.http2.headers.dhe.Header.IndexInstance;
import snell.http2.headers.dhe.Header.Literal;
import snell.http2.headers.dhe.Header.LiteralInstance;
import snell.http2.headers.dhe.Header.Range;
import snell.http2.headers.dhe.Header.RangeInstance;

import com.google.common.collect.ImmutableSet;
import com.google.common.io.ByteStreams;

public final class Dhe 
  implements HeaderSerializer {
  
  public static enum Mode {
    REQUEST(Huffman.REQUEST_TABLE), 
    RESPONSE(Huffman.RESPONSE_TABLE);
    
    private final Huffman huffman;
    
    Mode(Huffman huffman) {
      this.huffman = huffman;
    }
    
    public Huffman huffman() {
      return huffman;
    }
  }
  
  public static Dhe forRequest() {
    return new Dhe(Mode.REQUEST);
  }
  
  public static Dhe forResponse() {
    return new Dhe(Mode.RESPONSE);
  }

  private final Mode mode;
  private final Storage storage = 
    new Storage();
  
  public Dhe(Mode mode) {
    this.mode = mode;
  }
  
  public Storage storage() {
    return storage;
  }
  
  public Mode mode() {
    return mode;
  }
  
  @Override
  public Huffman huffman() {
    return mode.huffman();
  }

  private static final ImmutableSet<String> 
    ALWAYS_EPHEMERAL = 
      ImmutableSet.of(
        "authorization",       // never store credentials in memory!
        "proxy-authorization"
      );
  
  @Override
  public void serialize(
    OutputStream buffer, 
    HeaderSet<?> map)
      throws IOException {
    ByteArrayOutputStream rest_buf = 
      new ByteArrayOutputStream();
    Storage storage = storage();
    BuilderContext ctx = 
      new BuilderContext(storage);
    int c = 0;
    for (String name : map) {
      name = name.toLowerCase();
      for (ValueSupplier<?> val : map.get(name)) {
        boolean ephemeral = 
          ALWAYS_EPHEMERAL.contains(name);
        try {
          byte idx = storage.indexOf(name, val);
          if (ctx.index(idx, rest_buf)) 
            c++;
        } catch (RuntimeException r) {
          try {
            byte idx = storage.indexOfName(name);
            if (ctx.cloned(idx, val, ephemeral, rest_buf)) 
              c++;
          } catch (RuntimeException r2) {
            if (ctx.literal(name, val, ephemeral, rest_buf))
              c++;
          }
        }
      }
    }  
    c += ctx.writeRemaining(rest_buf);
    buffer.write((byte)(c-1));
    ByteStreams.copy(
      new ByteArrayInputStream(
        rest_buf.toByteArray()), 
        buffer);
  }
    
  @Override
  public void deserialize(
    InputStream in, 
    HeaderSetter<?> set)
      throws IOException {
    Storage storage = storage();
    byte[] c = new byte[1];
    int r = in.read(c);
    checkState(r >= 0);
    while(c[0] >= 0) {
      Header<?> header = 
        Header.parse(
          in,
          huffman(),
          storage());
      switch(header.code()) {
      case TYPE_INDEX:
        Index index = header.cast();
        for (IndexInstance ii : index) {
          byte idx = ii.index();
          set.set(
            checkNotNull(storage.nameOf(idx)), 
            storage.valueOf(idx));
        }
        break;
      case TYPE_RANGE:
        Range range = header.cast();
        for (RangeInstance ri : range) {
          byte start = ri.start();
          byte end = ri.end();
          for (byte idx = start; idx <= end; idx++)
            set.set(
              checkNotNull(storage.nameOf(idx)),
              storage.valueOf(idx));
        }
        break;
      case TYPE_CLONE:
        Clone clone = header.cast();
        for (CloneInstance ci : clone) {
          byte idx = ci.index();
          ValueSupplier<?> value = ci.value();
          String name = storage.nameOf(idx);
          if (name != null) {
            set.set(
              name, 
              value);
            if (!clone.ephemeral())
              storage.push(name, value);
          }
        }
        break;
      case TYPE_LITERAL:
        Literal literal = header.cast();
        for (LiteralInstance li : literal) {
          String name = li.name();
          ValueSupplier<?> val = li.value();
          set.set(name,val);
          if (!literal.ephemeral())
            storage.push(name,val);
        }
        break;
      default:
        throw new IllegalStateException();
      }
      c[0]--;
    }
  }

}
