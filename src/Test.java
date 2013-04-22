

public class Test {

  public static void main(String... args) throws Exception {
   
    for (int n = 1; n < 100; n++) {
      
        n |= 1;
        n++;
        
        System.out.println(n);
    }
    
  }
  

}
