package snell.http2.frames;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static java.lang.Math.max;
import static snell.http2.utils.IoUtils.read32;
import static snell.http2.utils.IoUtils.write32;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class GoAwayFrame 
  extends Frame<GoAwayFrame> {
  
  static final byte TYPE = 0x7;
  
  public static GoAwayFrameBuilder make() {
    return new GoAwayFrameBuilder();
  }
  
  public static class GoAwayFrameBuilder 
    extends FrameBuilder<GoAwayFrame,GoAwayFrameBuilder> {

    private int status;
    private int lastStream;
    
    public GoAwayFrameBuilder() {
      super(TYPE);
    }
    
    protected void parseRest(
      InputStream in)
        throws IOException {
      checkNotNull(in);
      this.lastStream = read32(in);
      this.status = read32(in);
    }
    
    public GoAwayFrameBuilder status(Status status) {
      this.status = checkNotNull(status).ordinal();
      return this;
    }
    
    public GoAwayFrameBuilder lastStream(int id) {
      checkArgument(id >= 0 && id <= Integer.MAX_VALUE);
      this.lastStream = id;
      return this;
    }
    
    @Override
    public GoAwayFrame get() {
      this.length = 8;
      return new GoAwayFrame(this);
    }
    
  }
  
  private final int status;
  private final int lastStream;
  
  protected GoAwayFrame(
    GoAwayFrameBuilder builder) {
      super(builder);
      this.status = 
        max(0,builder.status);
      this.lastStream = 
        max(0,builder.lastStream);
  }

  public Status status() {
    try {
      return Status.values()[status];
    } catch (Throwable t) {
      return null;
    }
  }
  
  public int lastStream() {
    return lastStream;
  }
  
  protected void writeRest(
    OutputStream out) 
      throws IOException {
    checkNotNull(out);
    write32(out,lastStream);
    write32(out,status);
  }

}
