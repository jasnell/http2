package snell.http2.frames;

import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static snell.http2.utils.IoUtils.read32;
import static snell.http2.utils.IoUtils.write32;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class RstStreamFrame 
  extends Frame<RstStreamFrame> {
  
  static final byte TYPE = 0x3;
  
  public static RstStreamFrameBuilder make() {
    return new RstStreamFrameBuilder();
  }
  
  public static class RstStreamFrameBuilder
    extends FrameBuilder<RstStreamFrame,RstStreamFrameBuilder> {

    private int status;
    
    public RstStreamFrameBuilder() {
      super(TYPE);
    }
    
    protected void parseRest(
      InputStream in)
        throws IOException {
      checkNotNull(in);
      this.status = read32(in);
    }
    
    public RstStreamFrameBuilder status(Status status) {
      this.status = checkNotNull(status).ordinal();
      return this;
    }
    
    @Override
    public RstStreamFrame get() {
      this.length = 4;
      return new RstStreamFrame(this);
    }
    
  }
  
  private final int status;
  
  protected RstStreamFrame(
    RstStreamFrameBuilder builder) {
      super(builder);
      this.status = 
        max(0,builder.status);
  }

  public Status status() {
    try {
      return Status.values()[status];
    } catch (Throwable t) {
      return null;
    }
  }

  protected void writeRest(
    OutputStream out) 
      throws IOException {
    checkNotNull(out);
    write32(out,status);
  }

}
