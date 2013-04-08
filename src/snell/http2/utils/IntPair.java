package snell.http2.utils;

import com.google.common.base.Objects;

/**
 * A simple tuple structure. The Pair object itself is immutable. Each
 * of the objects contained may not be. As a defensive step, the hashCode()
 * is calculated when the Pair object is created based on the current state
 * of the two input objects. That way, if the paired objects do happen to
 * change state, at least the hash code of the Pair object remains stable.
 * The equals method, however, calls each paired objects equals() method, 
 * so the behavior of equals() may differ depending on whether or not the 
 * paired objects have changed. As a best practice, you should only ever 
 * use Immutable objects with Pair or ensure that the pair objects never
 * change state.
 */
public final class IntPair {

  public static IntPair of(int v1, int v2) {
    return new IntPair(v1,v2);
  }
  
  private final int v1;
  private final int v2;
  private transient int hash = -1;
  
  IntPair(int v1, int v2) {
    this.v1 = v1;
    this.v2 = v2;
    this.hash = hashCode();
  }
  
  public IntPair swap() {
    return IntPair.of(v2,v1);
  }
  
  public int one() {
    return v1;
  }
  
  public int two() {
    return v2;
  }
  
  public String toString() {
    return Objects.toStringHelper("Pair")
      .add("one", one())
      .add("two", two())
      .toString();
  }

  @Override
  public int hashCode() {
    if (hash == -1)
      hash = Objects.hashCode(v1,v2);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) 
      return false;
    IntPair other = (IntPair) obj;
    return 
      v1 == other.v1 &&
      v2 == other.v2; 
  }
}
