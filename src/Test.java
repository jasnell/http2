
import java.io.ByteArrayOutputStream;
import java.util.Arrays;

public class Test {
  
  public static void main(String... args) throws Exception {
  
    Huffman huffman = Huffman.REQUEST_TABLE;
    
    String s = "ǩḵƕģhello😪😩😫😂😌 ǩḵƕģworldǩḵƕģ";
    
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    
    huffman.encode(s, out);
    
    byte[] data = out.toByteArray();
        
    out = new ByteArrayOutputStream();
    
    huffman.decode(data, out);
    
    System.out.println("E " + Arrays.toString(data));
    
    System.out.println("A " + Arrays.toString(s.getBytes("UTF-8")));
    System.out.println("D " + Arrays.toString(out.toByteArray()));
 
    System.out.println(new String(out.toByteArray(),"UTF-8"));
  }

  
}
