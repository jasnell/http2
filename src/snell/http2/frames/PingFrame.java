package snell.http2.frames;

public final class PingFrame 
  extends Frame<PingFrame> {
  
  static final byte TYPE = 0x6;
  
  public static PingFrameBuilder make() {
    return new PingFrameBuilder();
  }
  
  public static class PingFrameBuilder 
    extends FrameBuilder<PingFrame,PingFrameBuilder> {
    protected PingFrameBuilder() {
      super(TYPE);
    }
    @Override
    public PingFrame get() {
      return new PingFrame(this);
    }
  }
  
  protected PingFrame(
    PingFrameBuilder builder) {
      super(builder);
  }

}
