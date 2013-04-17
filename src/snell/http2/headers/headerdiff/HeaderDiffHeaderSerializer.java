package snell.http2.headers.headerdiff;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static snell.http2.headers.headerdiff.Utils.stringFromValueSupplier;
import static snell.http2.headers.headerdiff.Utils.utf8length;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import snell.http2.headers.HeaderSerializer;
import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.Huffman;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.headerdiff.Header.DeltaHeaderBuilder;
import snell.http2.headers.headerdiff.Header.HeaderBuilder;
import snell.http2.headers.headerdiff.Header.IndexingMode;
import snell.http2.headers.headerdiff.Header.LiteralHeaderBuilder;
import snell.http2.headers.headerdiff.HeaderDiff.Mode;
import snell.http2.utils.IntTriple;

public class HeaderDiffHeaderSerializer 
  implements HeaderSerializer {

  private final HeaderDiff hd;
  
  protected HeaderDiffHeaderSerializer(HeaderDiff hd) {
    this.hd = hd;
  }
  
  @Override
  public Huffman huffman() {
    return null;
  }

  @Override
  public void serialize(
    OutputStream buffer, 
    HeaderSet<?> map)
      throws IOException {
    
    checkNotNull(map);
    int size = map.size();
    checkArgument(size <= 0xFF);
    
    buffer.write(size);
    
    HeaderTable headers = 
      hd.headerTable();
    NameTable names = 
      hd.nameTable();
    Mode mode = 
      hd.mode();
    
    for(String key : map) {
      Name name =  names.nameFor(key);
      for (ValueSupplier<?> value : map.get(key)) {
        HeaderBuilder<?,?> builder = null;
        String val = 
          stringFromValueSupplier(value);
        IntTriple triple = 
          headers.locate(name, val);
        if (triple.two() > -1)
          builder = 
            Header
              .indexed()
              .name(name)
              .index(triple.two());
        else {
          boolean length_ok = 
            headers.canFitWithinExisting(val);
          boolean require_novelty = 
            mode == Mode.REQUEST && 
            headers.size() < 10000;
          int common_prefix_length = 0;
          int added_length = 0;
          int least_recently_used = 
            headers.leastRecentlyUsedIndex();
          if (triple.one() > -1) {
            added_length = 
              val.length() - 
              headers.getItemLength(triple.one());
            common_prefix_length = triple.three();
          }
          boolean delta_sub_length_ok = 
            headers.currentByteSize() + added_length < headers.maxByteSize();
          if (common_prefix_length > 0) {
            boolean is_novel = 
              require_novelty || 
              added_length > 15;
            DeltaHeaderBuilder 
              dbuilder = 
                (DeltaHeaderBuilder) 
                  (builder = 
                    Header
                      .delta(name)
                      .value(val)
                      .commonPrefixLength(common_prefix_length)
                      .referenceIndex(triple.one()));
              if (is_novel && length_ok) 
                dbuilder.mode(IndexingMode.INCREMENTAL);
              else if (delta_sub_length_ok)
                dbuilder.mode(IndexingMode.SUBSTITUTION);    
          } else {
            LiteralHeaderBuilder lbuilder = 
              (LiteralHeaderBuilder) 
                (builder = 
                   Header
                     .literal(name)
                     .value(val));
            if (mode == Mode.REQUEST && 
                headers.maxSize() < 10000 &&
                least_recently_used > -1) {
              int newDataLength = 
                utf8length(val) - headers.leastRecentlyUsedSize();
              if (headers.remainingBytes() > newDataLength) {
                lbuilder.mode(IndexingMode.SUBSTITUTION);
                lbuilder.substitution(least_recently_used);
              }
            } else if (length_ok) {
              lbuilder.mode(IndexingMode.INCREMENTAL);
            }
          }
        }
        if (builder != null) {
          builder
            .get()
            .update(headers)
            .writeTo(buffer);
        }
      }
    }
    
  }

  @Override
  public void deserialize(
    InputStream in, 
    HeaderSetter<?> set)
      throws IOException {
  }

}
