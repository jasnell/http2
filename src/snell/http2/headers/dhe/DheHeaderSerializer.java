package snell.http2.headers.dhe;


import static com.google.common.base.Preconditions.*;
import static snell.http2.headers.dhe.Header.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Map;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.io.ByteStreams;

import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.delta.Huffman;
import snell.http2.headers.dhe.Dhe.Mode;

public final class DheHeaderSerializer 
  implements HeaderSerializer {
  
  private final Dhe dhe;
  private final Huffman huffman;
  
  public DheHeaderSerializer(Dhe dhe) {
    this.dhe = dhe;
    this.huffman = 
      dhe.mode() == Mode.REQUEST ?
        Huffman.REQUEST_TABLE :
        Huffman.RESPONSE_TABLE;
  }
  
  public Storage storage() {
    return dhe.storage();
  }
  
  public Mode mode() {
    return dhe.mode();
  }
  
  @Override
  public Huffman huffman() {
    return huffman;
  }

  private static final ImmutableSet<String> 
    ALWAYS_EPHEMERAL = 
      ImmutableSet.of(
        //"referer", 
        //":path", 
        //"authorization", 
        //"www-authenticate",
        //"proxy-authenticate",
        //"date",
        //"last-modified",
        //"content-length",
        //"age"
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
      new BuilderContext();
    Multimap<String,ValueSupplier<?>> store =
      LinkedHashMultimap.create();
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
          if (!ephemeral)
            store.put(name, val);
        }
      }
    }
    for (Map.Entry<String, ValueSupplier<?>> entry : store.entries())
      storage.push(entry.getKey(),entry.getValue());    
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
    if (r < 0)
      throw new IllegalStateException();
    Multimap<String,ValueSupplier<?>> store = 
      LinkedHashMultimap.create();
    while(c[0] >= 0) {
      Header<?> header = 
        Header.parse(in,huffman);
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
              store.put(name, value);
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
            store.put(name, val);
        }
        break;
      default:
        throw new IllegalStateException();
      }
      c[0]--;
    }
    for (Map.Entry<String, ValueSupplier<?>> entry : store.entries())
      storage.push(entry.getKey(),entry.getValue());
  }

}
