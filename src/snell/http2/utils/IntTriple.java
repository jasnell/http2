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
public final class IntTriple {

  public static IntTriple of(int v1, int v2, int v3) {
    return new IntTriple(v1,v2,v3);
  }
  
  private final int v1;
  private final int v2;
  private final int v3;
  private transient int hash = -1;
  
  IntTriple(int v1, int v2, int v3) {
    this.v1 = v1;
    this.v2 = v2;
    this.v3 = v3;
    this.hash = hashCode();
  }
  
  public int one() {
    return v1;
  }
  
  public int two() {
    return v2;
  }
  
  public int three() {
    return v3;
  }
  
  public String toString() {
    return Objects.toStringHelper("Pair")
      .add("one", one())
      .add("two", two())
      .add("three", three())
      .toString();
  }

  @Override
  public int hashCode() {
    if (hash == -1)
      hash = Objects.hashCode(v1,v2,v3);
    return hash;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) return true;
    if (obj == null) return false;
    if (getClass() != obj.getClass()) 
      return false;
    IntTriple other = (IntTriple) obj;
    return 
      v1 == other.v1 &&
      v2 == other.v2 &&
      v3 == other.v3; 
  }
}
