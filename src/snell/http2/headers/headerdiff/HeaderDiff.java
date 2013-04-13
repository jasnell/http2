package snell.http2.headers.headerdiff;

public final class HeaderDiff {

  public static enum Mode {
    REQUEST, 
    RESPONSE;
  }
  
  private final HeaderTable table = 
    new HeaderTable();
  private final NameTable names = 
    new NameTable();
  private final Mode mode;
  private final HeaderDiffHeaderSerializer ser;
  
  public HeaderDiff(Mode mode) {
    ser = new HeaderDiffHeaderSerializer(this);
    this.mode = mode;
  }
  
  public Mode mode() {
    return mode;
  }
  
  public HeaderTable headerTable() {
    return table;
  }
  
  public NameTable nameTable() {
    return names;
  }
  
  public HeaderDiffHeaderSerializer ser() {
    return ser;
  }
}
