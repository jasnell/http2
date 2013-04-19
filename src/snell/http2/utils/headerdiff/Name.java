package snell.http2.utils.headerdiff;

public final class Name {

  private final NameTable nameTable;
  private final int idx;
  private transient int hash = 1;
  
  protected Name(
    NameTable table, 
    int idx) {
      this.nameTable = table;
      this.idx = idx;
  }
  
  public String value() {
    return nameTable.value(idx);
  }
  
  public String toString() {
    return value();
  }
  
  public int index() {
    return idx;
  }

  @Override
  public int hashCode() {
    if (hash == 1) {
      hash = 31 * hash + idx;
      hash = 31 * hash + ((nameTable == null) ? 0 : nameTable.hashCode());
    }
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj)
      return true;
    if (obj == null)
      return false;
    if (getClass() != obj.getClass())
      return false;
    Name other = (Name) obj;
    if (idx != other.idx)
      return false;
    if (nameTable != other.nameTable)
      return false;
    return true;
  }
  
}
