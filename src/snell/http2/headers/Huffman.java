package snell.http2.headers;


import static snell.http2.utils.IntPair.of;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Comparator;
import java.util.Map;

import snell.http2.utils.BitBucket;
import snell.http2.utils.IntPair;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.primitives.Ints;


public final class Huffman {

  private static boolean within(byte c, int l, int h) {
    return c >= (byte)l && c <= (byte)h;
  }
  
  private static void storeContinuation(byte c, BitBucket bucket) {
    c = (byte)(c << 2); // shift the bit over two
    bucket.storeBits(c, 6); // and write out six bits...
  }
  
  public static void encode(
    String data, 
    Huffman codeTable, 
    OutputStream out) 
      throws IOException {
    if (data == null)
      return;
    
    byte[] bytes = data.getBytes("UTF-8");
    
    BitBucket bucket = 
      new BitBucket();
    
    for (int n = 0; n < bytes.length; n++) {
      byte current = bytes[n];
      if (current == 127)
        throw new IllegalArgumentException();
      if (within(current,0,127)) {
        bucket.storeBit32(codeTable.get(current));
      } else {
        bucket.storeBit32(codeTable.get(current));
        if (within(current,0xC2,0xDF)) {
          storeContinuation(bytes[n+1],bucket);
          n++;
        } else if (within(current,0xE0,0xEF)) {
          storeContinuation(bytes[n+1],bucket);
          storeContinuation(bytes[n+2],bucket);
          n += 2;
        } else if (within(current,0xF0,0xF4)) {
          storeContinuation(bytes[n+1],bucket);
          storeContinuation(bytes[n+2],bucket);
          storeContinuation(bytes[n+3],bucket);
          n += 3;
        } else {
          throw new IllegalArgumentException();
        }
      }
    }    
    bucket.storeBit32(codeTable.getEof());
    bucket.writeTo(out);
  }
      
  public static void decode(
    byte[] bytes, 
    Huffman codeTable,
    OutputStream out) 
      throws IOException {
      BitBucket buf = 
        new BitBucket(bytes);
      codeTable.decodeFrom(buf, out);
  }
  
  private static HuffmanTableBuilder make() {
    return new HuffmanTableBuilder();
  }
  
  private static class HuffmanTableBuilder
    implements Supplier<Huffman> {

    private final ImmutableMap.Builder<Integer,IntPair> table =
      ImmutableMap.builder();
    private final ImmutableSortedMap.Builder<IntPair,Integer> decode = 
      ImmutableSortedMap.orderedBy(sorter);
    
    private HuffmanTableBuilder add(int c, IntPair pair) {
      int o = pair.one();
      int n = pair.two();
      pair = of(o << (32 - n), n);
      table.put(c,pair);
      decode.put(pair,c);
      return this;
    }
    
    @Override
    public Huffman get() {
      return new Huffman(this);
    }
   
  }
  
  private final ImmutableMap<Integer,IntPair> table;
  private final Node root = new Node();
  
  protected Huffman(HuffmanTableBuilder builder) {
    this.table = builder.table.build();
    buildDecodeTable(builder);
  }
  
  private void buildDecodeTable(HuffmanTableBuilder builder) {
    ImmutableSortedMap<IntPair,Integer> decode = 
      builder.decode.build();
    for (Map.Entry<IntPair,Integer> entry : decode.entrySet()) {
      Node node = root;
      BitBucket b = new BitBucket(entry.getKey().one());
      int l = entry.getKey().two();
      while(l > 0) {
        boolean d = b.getBit();
        Node n = node.get(d);
        if (n == null)
          node = node.set(d, new Node());
        else node = n;
        l--;
      }
      node.set(entry.getValue());
    }
  }
  
  private int decodeFrom(
    BitBucket bit, 
    OutputStream out) 
      throws IOException {
    int c = 0;
    Node node = root;
    while(true) {
      boolean b = bit.getBit();
      node = node.get(b);
      c++;
      if (node.sym() != null) {
        byte sym = node.sym().byteValue();
        if (sym == 127)
          break;
        out.write(sym);
        if (!within(sym,0,127)) {
          if (within(sym,0xC2,0xDF))
            out.write(readContinuation(bit,1));
          else if (within(sym,0xE0,0xEF))
            out.write(readContinuation(bit,2));
          else if (within(sym,0xF0,0xF4))
            out.write(readContinuation(bit,3));
          else
            throw new IllegalArgumentException();
        }
        node = root;
      }
    }
    return c;
  }
  
  private static byte[] readContinuation(BitBucket bucket, int c) {
    byte[] ret = new byte[c];
    while (c > 0) {
      byte[] b = bucket.getBits(6);
      b[0] = (byte)((b[0]&~0xFF00) >>> 2);
      b[0] |= 0x80;
      ret[ret.length-c] = b[0]; 
      c--;
    }
    return ret;
  }
  
  public void encode(
    String data, 
    OutputStream out) 
      throws IOException {
    encode(data,this,out);
  }
  
  public void decode(
    byte[] data, 
    OutputStream out) 
      throws IOException {
    decode(data,this,out);
  }
  
  private static final Comparator<IntPair> sorter = 
    new Comparator<IntPair>() {
      @Override
      public int compare(
        IntPair a,
        IntPair b) {
          return Ints.compare(a.one(), b.one());
      }
  };
  
  public IntPair get(int c) {
    return table.get(c);
  }
  
  public IntPair getEof() {
    return get((char)127);
  }
  
  private static final class Node {
    private Node l,r;
    private Integer sym;
    Node() {}
    Node left(Node node) {
      l = node;
      return l;
    }
    Node right(Node node) {
      r = node;
      return r;
    }
    Node set(boolean bit, Node node) {
      return bit ? right(node) : left(node);
    }
    void set(Integer sym) {
      this.sym = sym;
    }
    Node get(boolean bit) {
      return bit?r:l;
    }
    Integer sym() {
      return sym;
    }
  }
  
  public static final Huffman REQUEST_TABLE = 
    make()
.add(0, of(0x01fffffe,25))
.add(1, of(0x01ffffff,25))
.add(2, of(0x00ffffe0,24))
.add(3, of(0x00ffffe1,24))
.add(4, of(0x00ffffe2,24))
.add(5, of(0x00ffffe3,24))
.add(6, of(0x00ffffe4,24))
.add(7, of(0x00ffffe5,24))
.add(8, of(0x00ffffe6,24))
.add(9, of(0x00ffffe7,24))
.add(10, of(0x00ffffe8,24))
.add(11, of(0x00ffffe9,24))
.add(12, of(0x00ffffea,24))
.add(13, of(0x00ffffeb,24))
.add(14, of(0x00ffffec,24))
.add(15, of(0x00ffffed,24))
.add(16, of(0x00ffffee,24))
.add(17, of(0x00ffffef,24))
.add(18, of(0x00fffff0,24))
.add(19, of(0x00fffff1,24))
.add(20, of(0x00fffff2,24))
.add(21, of(0x00fffff3,24))
.add(22, of(0x00fffff4,24))
.add(23, of(0x00fffff5,24))
.add(24, of(0x00fffff6,24))
.add(25, of(0x00fffff7,24))
.add(26, of(0x00fffff8,24))
.add(27, of(0x00fffff9,24))
.add(28, of(0x00fffffa,24))
.add(29, of(0x00fffffb,24))
.add(30, of(0x00fffffc,24))
.add(31, of(0x00fffffd,24))
.add(32, of(0x00000ff6,12))
.add(33, of(0x00000ff7,12))
.add(34, of(0x00003ffa,14))
.add(35, of(0x00007ffc,15))
.add(36, of(0x00007ffd,15))
.add(37, of(0x00000018,6))
.add(38, of(0x00000054,7))
.add(39, of(0x00007ffe,15))
.add(40, of(0x00000ff8,12))
.add(41, of(0x00000ff9,12))
.add(42, of(0x00000ffa,12))
.add(43, of(0x00000ffb,12))
.add(44, of(0x000003ee,10))
.add(45, of(0x00000019,6))
.add(46, of(0x00000002,5))
.add(47, of(0x00000003,5))
.add(48, of(0x0000001a,6))
.add(49, of(0x0000001b,6))
.add(50, of(0x0000001c,6))
.add(51, of(0x0000001d,6))
.add(52, of(0x00000055,7))
.add(53, of(0x00000056,7))
.add(54, of(0x00000057,7))
.add(55, of(0x00000058,7))
.add(56, of(0x00000059,7))
.add(57, of(0x0000005a,7))
.add(58, of(0x0000001e,6))
.add(59, of(0x000003ef,10))
.add(60, of(0x0003fffe,18))
.add(61, of(0x0000001f,6))
.add(62, of(0x0001fffc,17))
.add(63, of(0x000001ec,9))
.add(64, of(0x00001ffc,13))
.add(65, of(0x000000ba,8))
.add(66, of(0x000001ed,9))
.add(67, of(0x000000bb,8))
.add(68, of(0x000000bc,8))
.add(69, of(0x000001ee,9))
.add(70, of(0x000000bd,8))
.add(71, of(0x000003f0,10))
.add(72, of(0x000003f1,10))
.add(73, of(0x000001ef,9))
.add(74, of(0x000003f2,10))
.add(75, of(0x000007fa,11))
.add(76, of(0x000003f3,10))
.add(77, of(0x000001f0,9))
.add(78, of(0x000003f4,10))
.add(79, of(0x000003f5,10))
.add(80, of(0x000001f1,9))
.add(81, of(0x000003f6,10))
.add(82, of(0x000001f2,9))
.add(83, of(0x000001f3,9))
.add(84, of(0x000001f4,9))
.add(85, of(0x000003f7,10))
.add(86, of(0x000003f8,10))
.add(87, of(0x000003f9,10))
.add(88, of(0x000003fa,10))
.add(89, of(0x000003fb,10))
.add(90, of(0x000003fc,10))
.add(91, of(0x00003ffb,14))
.add(92, of(0x00fffffe,24))
.add(93, of(0x00003ffc,14))
.add(94, of(0x00003ffd,14))
.add(95, of(0x0000005b,7))
.add(96, of(0x0007fffe,19))
.add(97, of(0x00000004,5))
.add(98, of(0x0000005c,7))
.add(99, of(0x00000005,5))
.add(100, of(0x00000020,6))
.add(101, of(0x00000000,4))
.add(102, of(0x00000021,6))
.add(103, of(0x00000022,6))
.add(104, of(0x00000023,6))
.add(105, of(0x00000006,5))
.add(106, of(0x000000be,8))
.add(107, of(0x000000bf,8))
.add(108, of(0x00000024,6))
.add(109, of(0x00000025,6))
.add(110, of(0x00000026,6))
.add(111, of(0x00000007,5))
.add(112, of(0x00000008,5))
.add(113, of(0x000001f5,9))
.add(114, of(0x00000009,5))
.add(115, of(0x0000000a,5))
.add(116, of(0x0000000b,5))
.add(117, of(0x00000027,6))
.add(118, of(0x000000c0,8))
.add(119, of(0x00000028,6))
.add(120, of(0x000000c1,8))
.add(121, of(0x000000c2,8))
.add(122, of(0x000001f6,9))
.add(123, of(0x0001fffd,17))
.add(124, of(0x00000ffc,12))
.add(125, of(0x0001fffe,17))
.add(126, of(0x00000ffd,12))
.add(127, of(0x00000029,6))
.add((byte)0xC2, of(0x000000c3,8))
.add((byte)0xC3, of(0x000000c4,8))
.add((byte)0xC4, of(0x000000c5,8))
.add((byte)0xC5, of(0x000000c6,8))
.add((byte)0xC6, of(0x000000c7,8))
.add((byte)0xC7, of(0x000000c8,8))
.add((byte)0xC8, of(0x000000c9,8))
.add((byte)0xC9, of(0x000000ca,8))
.add((byte)0xCA, of(0x000000cb,8))
.add((byte)0xCB, of(0x000000cc,8))
.add((byte)0xCC, of(0x000000cd,8))
.add((byte)0xCD, of(0x000000ce,8))
.add((byte)0xCE, of(0x000000cf,8))
.add((byte)0xCF, of(0x000000d0,8))
.add((byte)0xD0, of(0x000000d1,8))
.add((byte)0xD1, of(0x000000d2,8))
.add((byte)0xD2, of(0x000000d3,8))
.add((byte)0xD3, of(0x000000d4,8))
.add((byte)0xD4, of(0x000000d5,8))
.add((byte)0xD5, of(0x000000d6,8))
.add((byte)0xD6, of(0x000000d7,8))
.add((byte)0xD7, of(0x000000d8,8))
.add((byte)0xD8, of(0x000000d9,8))
.add((byte)0xD9, of(0x000000da,8))
.add((byte)0xDA, of(0x000000db,8))
.add((byte)0xDB, of(0x000000dc,8))
.add((byte)0xDC, of(0x000000dd,8))
.add((byte)0xDD, of(0x000000de,8))
.add((byte)0xDE, of(0x000000df,8))
.add((byte)0xDF, of(0x000000e0,8))
.add((byte)0xE0, of(0x000000e1,8))
.add((byte)0xE1, of(0x000000e2,8))
.add((byte)0xE2, of(0x000000e3,8))
.add((byte)0xE3, of(0x000000e4,8))
.add((byte)0xE4, of(0x000000e5,8))
.add((byte)0xE5, of(0x000000e6,8))
.add((byte)0xE6, of(0x000000e7,8))
.add((byte)0xE7, of(0x000000e8,8))
.add((byte)0xE8, of(0x000000e9,8))
.add((byte)0xE9, of(0x000000ea,8))
.add((byte)0xEA, of(0x000000eb,8))
.add((byte)0xEB, of(0x000000ec,8))
.add((byte)0xEC, of(0x000000ed,8))
.add((byte)0xED, of(0x000000ee,8))
.add((byte)0xEE, of(0x000000ef,8))
.add((byte)0xEF, of(0x000000f0,8))
.add((byte)0xF0, of(0x000000f1,8))
.add((byte)0xF1, of(0x000000f2,8))
.add((byte)0xF2, of(0x000000f3,8))
.add((byte)0xF3, of(0x000000f4,8))
.add((byte)0xF4, of(0x000000f5,8))
      .get();

  public static final Huffman RESPONSE_TABLE = 
    make()
.add(0,  of(0x007fffe0,23))
.add(1,  of(0x007fffe1,23))
.add(2,  of(0x007fffe2,23))
.add(3,  of(0x007fffe3,23))
.add(4,  of(0x007fffe4,23))
.add(5,  of(0x007fffe5,23))
.add(6,  of(0x007fffe6,23))
.add(7,  of(0x007fffe7,23))
.add(8,  of(0x007fffe8,23))
.add(9,  of(0x007fffe9,23))
.add(10, of(0x007fffea,23))
.add(11, of(0x007fffeb,23))
.add(12, of(0x007fffec,23))
.add(13, of(0x007fffed,23))
.add(14, of(0x007fffee,23))
.add(15, of(0x007fffef,23))
.add(16, of(0x007ffff0,23))
.add(17, of(0x007ffff1,23))
.add(18, of(0x007ffff2,23))
.add(19, of(0x007ffff3,23))
.add(20, of(0x007ffff4,23))
.add(21, of(0x007ffff5,23))
.add(22, of(0x007ffff6,23))
.add(23, of(0x007ffff7,23))
.add(24, of(0x007ffff8,23))
.add(25, of(0x007ffff9,23))
.add(26, of(0x007ffffa,23))
.add(27, of(0x007ffffb,23))
.add(28, of(0x007ffffc,23))
.add(29, of(0x007ffffd,23))
.add(30, of(0x007ffffe,23))
.add(31, of(0x007fffff,23))
.add(32, of(0x00000000,4))
.add(33, of(0x00000ffa,12))
.add(34, of(0x000000b6,8))
.add(35, of(0x00003ffa,14))
.add(36, of(0x00007ffc,15))
.add(37, of(0x000003ee,10))
.add(38, of(0x000003ef,10))
.add(39, of(0x00001ffa,13))
.add(40, of(0x000003f0,10))
.add(41, of(0x000003f1,10))
.add(42, of(0x00001ffb,13))
.add(43, of(0x000007fc,11))
.add(44, of(0x0000001a,6))
.add(45, of(0x0000004c,7))
.add(46, of(0x0000004d,7))
.add(47, of(0x000000b7,8))
.add(48, of(0x00000001,4))
.add(49, of(0x00000002,4))
.add(50, of(0x00000003,4))
.add(51, of(0x00000008,5))
.add(52, of(0x00000009,5))
.add(53, of(0x0000001b,6))
.add(54, of(0x0000001c,6))
.add(55, of(0x0000001d,6))
.add(56, of(0x0000001e,6))
.add(57, of(0x0000001f,6))
.add(58, of(0x0000000a,5))
.add(59, of(0x000001ea,9))
.add(60, of(0x0000fffc,16))
.add(61, of(0x000000b8,8))
.add(62, of(0x00003ffb,14))
.add(63, of(0x00001ffc,13))
.add(64, of(0x0001fffc,17))
.add(65, of(0x000000b9,8))
.add(66, of(0x000001eb,9))
.add(67, of(0x000001ec,9))
.add(68, of(0x000001ed,9))
.add(69, of(0x000001ee,9))
.add(70, of(0x000000ba,8))
.add(71, of(0x00000020,6))
.add(72, of(0x000003f2,10))
.add(73, of(0x000001ef,9))
.add(74, of(0x000000bb,8))
.add(75, of(0x000003f3,10))
.add(76, of(0x000003f4,10))
.add(77, of(0x00000021,6))
.add(78, of(0x000000bc,8))
.add(79, of(0x000000bd,8))
.add(80, of(0x000001f0,9))
.add(81, of(0x000003f5,10))
.add(82, of(0x000003f6,10))
.add(83, of(0x0000004e,7))
.add(84, of(0x00000022,6))
.add(85, of(0x000003f7,10))
.add(86, of(0x000003f8,10))
.add(87, of(0x000001f1,9))
.add(88, of(0x000003f9,10))
.add(89, of(0x000003fa,10))
.add(90, of(0x000003fb,10))
.add(91, of(0x00000ffb,12))
.add(92, of(0x00003ffc,14))
.add(93, of(0x00000ffc,12))
.add(94, of(0x00007ffd,15))
.add(95, of(0x000001f2,9))
.add(96, of(0x0003fffe,18))
.add(97, of(0x00000023,6))
.add(98, of(0x0000004f,7))
.add(99, of(0x00000024,6))
.add(100, of(0x00000050,7))
.add(101, of(0x0000000b,5))
.add(102, of(0x00000051,7))
.add(103, of(0x000000be,8))
.add(104, of(0x000000bf,8))
.add(105, of(0x00000052,7))
.add(106, of(0x000001f3,9))
.add(107, of(0x000001f4,9))
.add(108, of(0x00000053,7))
.add(109, of(0x00000054,7))
.add(110, of(0x00000055,7))
.add(111, of(0x00000025,6))
.add(112, of(0x00000056,7))
.add(113, of(0x000003fc,10))
.add(114, of(0x00000057,7))
.add(115, of(0x00000058,7))
.add(116, of(0x00000059,7))
.add(117, of(0x0000005a,7))
.add(118, of(0x000000c0,8))
.add(119, of(0x000001f5,9))
.add(120, of(0x000000c1,8))
.add(121, of(0x000001f6,9))
.add(122, of(0x000003fd,10))
.add(123, of(0x0001fffd,17))
.add(124, of(0x00003ffd,14))
.add(125, of(0x0001fffe,17))
.add(126, of(0x0000fffd,16))
.add(127, of(0x0000000c,5))
.add((byte)0xC2, of(0x000000c2,8))
.add((byte)0xC3, of(0x000000c3,8))
.add((byte)0xC4, of(0x000000c4,8))
.add((byte)0xC5, of(0x000000c5,8))
.add((byte)0xC6, of(0x000000c6,8))
.add((byte)0xC7, of(0x000000c7,8))
.add((byte)0xC8, of(0x000000c8,8))
.add((byte)0xC9, of(0x000000c9,8))
.add((byte)0xCA, of(0x000000ca,8))
.add((byte)0xCB, of(0x000000cb,8))
.add((byte)0xCC, of(0x000000cc,8))
.add((byte)0xCD, of(0x000000cd,8))
.add((byte)0xCE, of(0x000000ce,8))
.add((byte)0xCF, of(0x000000cf,8))
.add((byte)0xD0, of(0x000000d0,8))
.add((byte)0xD1, of(0x000000d1,8))
.add((byte)0xD2, of(0x000000d2,8))
.add((byte)0xD3, of(0x000000d3,8))
.add((byte)0xD4, of(0x000000d4,8))
.add((byte)0xD5, of(0x000000d5,8))
.add((byte)0xD6, of(0x000000d6,8))
.add((byte)0xD7, of(0x000000d7,8))
.add((byte)0xD8, of(0x000000d8,8))
.add((byte)0xD9, of(0x000000d9,8))
.add((byte)0xDA, of(0x000000da,8))
.add((byte)0xDB, of(0x000000db,8))
.add((byte)0xDC, of(0x000000dc,8))
.add((byte)0xDD, of(0x000000dd,8))
.add((byte)0xDE, of(0x000000de,8))
.add((byte)0xDF, of(0x000000df,8))
.add((byte)0xE0, of(0x000000e0,8))
.add((byte)0xE1, of(0x000000e1,8))
.add((byte)0xE2, of(0x000000e2,8))
.add((byte)0xE3, of(0x000000e3,8))
.add((byte)0xE4, of(0x000000e4,8))
.add((byte)0xE5, of(0x000000e5,8))
.add((byte)0xE6, of(0x000000e6,8))
.add((byte)0xE7, of(0x000000e7,8))
.add((byte)0xE8, of(0x000000e8,8))
.add((byte)0xE9, of(0x000000e9,8))
.add((byte)0xEA, of(0x000000ea,8))
.add((byte)0xEB, of(0x000000eb,8))
.add((byte)0xEC, of(0x000000ec,8))
.add((byte)0xED, of(0x000000ed,8))
.add((byte)0xEE, of(0x000000ee,8))
.add((byte)0xEF, of(0x000000ef,8))
.add((byte)0xF0, of(0x000000f0,8))
.add((byte)0xF1, of(0x000000f1,8))
.add((byte)0xF2, of(0x000000f2,8))
.add((byte)0xF3, of(0x000000f3,8))
.add((byte)0xF4, of(0x000000f4,8))
      .get();
}
