package snell.http2.headers.delta2;

/**
 * Basic Heap Structure Really... provides storage of static
 * and dynamic heap items... 
 */
public class Storage<V> {

  private final Storage<V> static_storage;
  private Node<V> head = null; // head is where new items are added
  private Node<V> tail = null; // tail is where items fall off
  private final int offset;
  private int count = 0;
  
  protected Storage() {
    this.static_storage = null;
    this.offset = 0;
  }
  
  public Storage(Storage<V> static_storage) {
    this.static_storage = static_storage;
    this.offset = this.static_storage.size();
  }
  
  public int size() {
    return offset+count;
  }
  
  public void push(V val) {
    Node<V> n = new Node<V>(val);
    if (head == null) {
      n.next = n.next = null;
      head = n;
      tail = head;
    } else {
      n.next = head;
      head.prev = n;
      head = n;
    }
    count++;
  }
  
  public boolean pop() {
    if (tail != null) {
      Node<V> n = tail;
      tail = n.prev;
      if (tail != null)
        tail.next = null;
      else head = null;
      count--;
      return true;
    } else return false;
  }
  
  public int indexOf(V val) {
    Node<V> n = head;
    int i = size()-1;
    while(n != null) {
      if (n.val.equals(val))
        return i;
      n = n.next;
      i--;
    }
    return static_storage != null ?
      static_storage.indexOf(val) : -1;
  }
  
  private static class Node<V> {
    private final V val;
    private Node<V> next;
    private Node<V> prev;
    private Node(V val) {
      this.val = val;
    }
  }
  
  public static void main(String...args) throws Exception {
    Storage<String> s = new Storage<String>();
    s.push("a");
    s.push("b");
    s.push("c");
    
    Storage<String> s2 = new Storage<String>(s);
    s2.push("d");
    s2.push("e");
    s2.pop();
    
    System.out.println(s2.indexOf("a"));
  }
}
