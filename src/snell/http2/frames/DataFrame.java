package snell.http2.frames;

import static java.lang.Math.min;

import java.io.IOException;
import java.io.InputStream;
import java.nio.channels.ReadableByteChannel;
import java.util.Iterator;

import static com.google.common.base.Throwables.propagate;

public final class DataFrame 
  extends Frame<DataFrame> {

  static final byte TYPE = 0x0;
  
  public static Iterable<DataFrame> produce(
    final InputStream in,
    final int stream_id) {
      return produce(
        in,
        DEFAULT_MAX_SIZE,
        stream_id);
  }
  
  private static DataFrame getNext(
    InputStream in, 
    int c, 
    int stream_id) {
    try {
      DataFrameBuilder builder = 
        make().streamId(stream_id);
      builder.fillFrom(in, c) ;
      DataFrame f = builder.get();
      return f.length > 0 ? f : null;
    } catch (Throwable t) {
      throw propagate(t);
    }
  }
  
  public static Iterable<DataFrame> produce(
    final InputStream in,
    int size,
    final int stream_id) {
      final int s = 
        min(size, DEFAULT_MAX_SIZE);
      return new Iterable<DataFrame>() {
        public Iterator<DataFrame> iterator() {
          return new Iterator<DataFrame>() {
            private DataFrame next = getNext(in,s,stream_id);
            public boolean hasNext() {
              return next != null;
            }
            public DataFrame next() {
              if (hasNext()) {
                DataFrame ret = next;
                next = getNext(in,s,stream_id);
                return ret;
              } else return null;
            }
            public void remove() {
              throw new UnsupportedOperationException();
            }
          };
        }        
      };
  }
  
  public static DataFrameBuilder make() {
    return new DataFrameBuilder();
  }
  
  public static final class DataFrameBuilder 
    extends FrameBuilder<DataFrame,DataFrameBuilder> {
    
    protected DataFrameBuilder() {
      super(TYPE);
    }
    
    @Override
    protected void parseRest(
      InputStream in) 
        throws IOException {
      fillFrom(in,this.length);
    }

    public DataFrame get() {
      return new DataFrame(this);
    }
    
    public DataFrameBuilder fill(
      InputStream in) 
        throws IOException {
        fillFrom(in);
        return this;
    }
    
    public DataFrameBuilder fill(
      InputStream in,
      int c) 
        throws IOException {
        fillFrom(in,c);
        return this;
    }
    
    public DataFrameBuilder fill(
      ReadableByteChannel in) 
        throws IOException {
        fillFrom(in);
        return this;
    }
    
    public DataFrameBuilder fill(
      ReadableByteChannel in,
      int n) 
        throws IOException {
        fillFrom(in,n);
        return this;
    }

    @Override
    public boolean fillFrom(
      InputStream in) 
        throws IOException {
      return super.fillFrom(in);
    }

    public boolean fillFrom(
      InputStream in, 
      int c) 
        throws IOException {
      return super.fillFrom(in,c);
    }
    
    @Override
    public boolean fillFrom(
      ReadableByteChannel c) 
        throws IOException {
      return super.fillFrom(c);
    }
    
    public boolean fillFrom(
      ReadableByteChannel c, 
      int n) 
        throws IOException {
      return super.fillFrom(c,n);
    }
  }
  
  protected DataFrame(
    DataFrameBuilder builder) {
    super(builder);
  }

  @Override
  public int size() {
    return super.size();
  }

}
