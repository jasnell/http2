package snell.http2.frames;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static snell.http2.utils.IoUtils.read32;
import static snell.http2.utils.IoUtils.write32;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public final class WindowUpdateFrame
  extends Frame<WindowUpdateFrame> {
  
  private static final byte FLAG_END_FLOW_CONTROL = 0x2;
  static final byte TYPE = 0x9;
  
  public static WindowUpdateFrameBuilder make() {
    return new WindowUpdateFrameBuilder();
  }
  
  public static final class WindowUpdateFrameBuilder 
    extends FrameBuilder<WindowUpdateFrame,WindowUpdateFrameBuilder> {

    private int val = 0;
    
    protected WindowUpdateFrameBuilder() {
      super(TYPE);
    }
    
    @Override
    protected void parseRest(
      InputStream in) 
        throws IOException {
      checkNotNull(in);
      this.val = read32(in);
    }

    public WindowUpdateFrameBuilder endFlowControl() {
      return this.flag(FLAG_END_FLOW_CONTROL, true);
    }
    
    public WindowUpdateFrameBuilder endFlowControl(boolean on) {
      return this.flag(FLAG_END_FLOW_CONTROL, on);
    }

    public WindowUpdateFrameBuilder value(int v) {
      checkArgument(inRange(v,0,0x7FFFFFFF));
      this.val = v;
      return this;
    }
    
    @Override
    public WindowUpdateFrame get() {
      this.length = 4;
      return new WindowUpdateFrame(this);
    }
    
  }
  
  private final int val;
  
  protected WindowUpdateFrame(
    WindowUpdateFrameBuilder builder) {
      super(builder);
      this.val = builder.val;
  }
  
  public boolean endFlowControl() {
    return flag(FLAG_END_FLOW_CONTROL);
  }
  
  @Override
  protected void writeRest(
    OutputStream out) 
      throws IOException {
    write32(out,val);
  }    
}
