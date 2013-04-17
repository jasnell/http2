package snell.http2.headers;

import static snell.http2.utils.IntPair.of;

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.Comparator;
import java.util.Map;

import snell.http2.utils.BitBucket;
import snell.http2.utils.CodepointIterator;
import snell.http2.utils.IntPair;

import com.google.common.base.Supplier;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.primitives.Ints;


public final class Huffman {

  public static void encode(
    String data, 
    Huffman codeTable, 
    OutputStream out) 
      throws IOException {
    if (data == null)
      return;
    BitBucket bucket = 
      new BitBucket();
    CodepointIterator ci = CodepointIterator.getInstance(data);
    while(ci.hasNext()) {
      int cp = ci.next();
      if (cp <= 256)
        bucket.storeBit32(codeTable.get(cp));
      else
        storeUtf8Codepoint(cp,codeTable,bucket);
    }
    bucket.storeBit32(codeTable.getEof());
    bucket.writeTo(out);
  }
  
  private static final void storeUtf8Codepoint(
    int cp, 
    Huffman codeTable, 
    BitBucket bucket)
      throws IOException { 
    int leading = 0;
    int pad_bits = 0;
    if (cp >= 0x80 && cp <= 0x07FF) {
      leading = 257;
      pad_bits = 11;
    } else if (cp >= 0x0800 && cp <= 0xFFFF) {
      leading = 258;
      pad_bits = 16;
    } else if (cp >= 0x10000 && cp <= 0x1FFFFF) {
      leading = 259;
      pad_bits = 21;
    } else if (cp >= 0x200000 && cp <= 0x3FFFFFF) {
      leading = 260;
      pad_bits = 26;
    } else if (cp >= 0x4000000 && cp <= 0x7FFFFFFF) {
      leading = 261;
      pad_bits = 31;
    }
    cp <<= 32 - pad_bits;
    bucket.storeBit32(codeTable.get(leading));
    bucket.storeBit32(cp,pad_bits);
  }
  
  
  private static void decodeUtf8Codepoint(
    int sym, 
    BitBucket bucket, 
    Writer w) 
      throws IOException {
    int bit_len = 0;
    switch (sym) {
    case 257:
      bit_len = 11;
      break;
    case 258:
      bit_len = 16;
      break;
    case 259:
      bit_len = 21;
      break;
    case 260:
      bit_len = 26;
      break;
    case 261:
      bit_len = 31;
      break;
    }
    byte[] bytes = 
      bucket.getBits(bit_len);
    int cp = 0;
    for (int n = 0; n < bytes.length; n++)
      cp |= bytes[n] << ((3-n)*8);
    cp >>>= (32 - bit_len);
    w.write(Character.toChars(cp)); // TODO: handle unsupported codepoints..?
  }
  
  public static void decode(
    byte[] bytes, 
    Huffman codeTable,
    OutputStream out) 
      throws IOException {
      OutputStreamWriter sw = 
        new OutputStreamWriter(
          out, "UTF-8");
      BitBucket buf = 
        new BitBucket(bytes);
      codeTable.decodeFrom(buf, sw);
      sw.flush();
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
    Writer w) 
      throws IOException {
    int c = 0;
    Node node = root;
    while(true) {
      boolean b = bit.getBit();
      node = node.get(b);
      c++;
      if (node.sym() != null) {
        if (node.sym() > 256) {
          decodeUtf8Codepoint(
            node.sym(), 
            bit, 
            w);
        } else if (node.sym() == 256) 
          break;
        else {
          w.write(node.sym());
        }
        node = root;
      }
    }
    return c;
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
    return get((char)256);
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
      .add(0,  of(0x07ffffbc,27))
      .add(1,  of(0x07ffffbd,27))
      .add(2,  of(0x07ffffbe,27))
      .add(3,  of(0x07ffffbf,27))
      .add(4,  of(0x07ffffc0,27))
      .add(5,  of(0x07ffffc1,27))
      .add(6,  of(0x07ffffc2,27))
      .add(7,  of(0x07ffffc3,27))
      .add(8,  of(0x07ffffc4,27))
      .add(9,  of(0x07ffffc5,27))
      .add(10, of(0x07ffffc6,27))
      .add(11, of(0x07ffffc7,27))
      .add(12, of(0x07ffffc8,27))
      .add(13, of(0x07ffffc9,27))
      .add(14, of(0x07ffffca,27))
      .add(15, of(0x07ffffcb,27))
      .add(16, of(0x07ffffcc,27))
      .add(17, of(0x07ffffcd,27))
      .add(18, of(0x07ffffce,27))
      .add(19, of(0x07ffffcf,27))
      .add(20, of(0x07ffffd0,27))
      .add(21, of(0x07ffffd1,27))
      .add(22, of(0x07ffffd2,27))
      .add(23, of(0x07ffffd3,27))
      .add(24, of(0x07ffffd4,27))
      .add(25, of(0x07ffffd5,27))
      .add(26, of(0x07ffffd6,27))
      .add(27, of(0x07ffffd7,27))
      .add(28, of(0x07ffffd8,27))
      .add(29, of(0x07ffffd9,27))
      .add(30, of(0x07ffffda,27))
      .add(31, of(0x07ffffdb,27))
      .add(32, of(0x00000ff6,12))
      .add(33, of(0x00000ff7,12))
      .add(34, of(0x00003ffa,14))
      .add(35, of(0x00007ffc,15))
      .add(36, of(0x00007ffd,15))
      .add(37, of(0x00000024,6))
      .add(38, of(0x0000006e,7))
      .add(39, of(0x00007ffe,15))
      .add(40, of(0x00000ff8,12))
      .add(41, of(0x00000ff9,12))
      .add(42, of(0x00000ffa,12))
      .add(43, of(0x00000ffb,12))
      .add(44, of(0x000003f2,10))
      .add(45, of(0x00000025,6))
      .add(46, of(0x00000004,5))
      .add(47, of(0x00000000,4))
      .add(48, of(0x00000026,6))
      .add(49, of(0x00000027,6))
      .add(50, of(0x00000005,5))
      .add(51, of(0x00000028,6))
      .add(52, of(0x0000006f,7))
      .add(53, of(0x00000070,7))
      .add(54, of(0x00000071,7))
      .add(55, of(0x00000072,7))
      .add(56, of(0x00000073,7))
      .add(57, of(0x00000074,7))
      .add(58, of(0x00000029,6))
      .add(59, of(0x000001ea,9))
      .add(60, of(0x0003fffe,18))
      .add(61, of(0x0000002a,6))
      .add(62, of(0x0001fffc,17))
      .add(63, of(0x000001eb,9))
      .add(64, of(0x00001ffc,13))
      .add(65, of(0x000000ec,8))
      .add(66, of(0x000001ec,9))
      .add(67, of(0x000000ed,8))
      .add(68, of(0x000000ee,8))
      .add(69, of(0x000001ed,9))
      .add(70, of(0x000000ef,8))
      .add(71, of(0x000003f3,10))
      .add(72, of(0x000003f4,10))
      .add(73, of(0x000001ee,9))
      .add(74, of(0x000003f5,10))
      .add(75, of(0x000007fa,11))
      .add(76, of(0x000001ef,9))
      .add(77, of(0x000001f0,9))
      .add(78, of(0x000003f6,10))
      .add(79, of(0x000003f7,10))
      .add(80, of(0x000001f1,9))
      .add(81, of(0x000003f8,10))
      .add(82, of(0x000001f2,9))
      .add(83, of(0x000001f3,9))
      .add(84, of(0x000001f4,9))
      .add(85, of(0x000003f9,10))
      .add(86, of(0x000003fa,10))
      .add(87, of(0x000001f5,9))
      .add(88, of(0x000001f6,9))
      .add(89, of(0x000003fb,10))
      .add(90, of(0x000003fc,10))
      .add(91, of(0x00003ffb,14))
      .add(92, of(0x07ffffdc,27))
      .add(93, of(0x00003ffc,14))
      .add(94, of(0x00003ffd,14))
      .add(95, of(0x0000002b,6))
      .add(96, of(0x0007fffe,19))
      .add(97, of(0x00000006,5))
      .add(98, of(0x00000075,7))
      .add(99, of(0x00000007,5))
      .add(100, of(0x0000002c,6))
      .add(101, of(0x00000001,4))
      .add(102, of(0x0000002d,6))
      .add(103, of(0x0000002e,6))
      .add(104, of(0x0000002f,6))
      .add(105, of(0x00000008,5))
      .add(106, of(0x000000f0,8))
      .add(107, of(0x000000f1,8))
      .add(108, of(0x00000030,6))
      .add(109, of(0x00000031,6))
      .add(110, of(0x00000009,5))
      .add(111, of(0x0000000a,5))
      .add(112, of(0x0000000b,5))
      .add(113, of(0x000001f7,9))
      .add(114, of(0x0000000c,5))
      .add(115, of(0x0000000d,5))
      .add(116, of(0x0000000e,5))
      .add(117, of(0x00000032,6))
      .add(118, of(0x000000f2,8))
      .add(119, of(0x00000033,6))
      .add(120, of(0x000000f3,8))
      .add(121, of(0x000000f4,8))
      .add(122, of(0x000001f8,9))
      .add(123, of(0x0001fffd,17))
      .add(124, of(0x00000ffc,12))
      .add(125, of(0x0001fffe,17))
      .add(126, of(0x00000ffd,12))
      .add(127, of(0x07ffffdd,27))
      .add(128, of(0x07ffffde,27))
      .add(129, of(0x07ffffdf,27))
      .add(130, of(0x07ffffe0,27))
      .add(131, of(0x07ffffe1,27))
      .add(132, of(0x07ffffe2,27))
      .add(133, of(0x07ffffe3,27))
      .add(134, of(0x07ffffe4,27))
      .add(135, of(0x07ffffe5,27))
      .add(136, of(0x07ffffe6,27))
      .add(137, of(0x07ffffe7,27))
      .add(138, of(0x07ffffe8,27))
      .add(139, of(0x07ffffe9,27))
      .add(140, of(0x07ffffea,27))
      .add(141, of(0x07ffffeb,27))
      .add(142, of(0x07ffffec,27))
      .add(143, of(0x07ffffed,27))
      .add(144, of(0x07ffffee,27))
      .add(145, of(0x07ffffef,27))
      .add(146, of(0x07fffff0,27))
      .add(147, of(0x07fffff1,27))
      .add(148, of(0x07fffff2,27))
      .add(149, of(0x07fffff3,27))
      .add(150, of(0x07fffff4,27))
      .add(151, of(0x07fffff5,27))
      .add(152, of(0x07fffff6,27))
      .add(153, of(0x07fffff7,27))
      .add(154, of(0x07fffff8,27))
      .add(155, of(0x07fffff9,27))
      .add(156, of(0x07fffffa,27))
      .add(157, of(0x07fffffb,27))
      .add(158, of(0x07fffffc,27))
      .add(159, of(0x07fffffd,27))
      .add(160, of(0x07fffffe,27))
      .add(161, of(0x07ffffff,27))
      .add(162, of(0x03ffff80,26))
      .add(163, of(0x03ffff81,26))
      .add(164, of(0x03ffff82,26))
      .add(165, of(0x03ffff83,26))
      .add(166, of(0x03ffff84,26))
      .add(167, of(0x03ffff85,26))
      .add(168, of(0x03ffff86,26))
      .add(169, of(0x03ffff87,26))
      .add(170, of(0x03ffff88,26))
      .add(171, of(0x03ffff89,26))
      .add(172, of(0x03ffff8a,26))
      .add(173, of(0x03ffff8b,26))
      .add(174, of(0x03ffff8c,26))
      .add(175, of(0x03ffff8d,26))
      .add(176, of(0x03ffff8e,26))
      .add(177, of(0x03ffff8f,26))
      .add(178, of(0x03ffff90,26))
      .add(179, of(0x03ffff91,26))
      .add(180, of(0x03ffff92,26))
      .add(181, of(0x03ffff93,26))
      .add(182, of(0x03ffff94,26))
      .add(183, of(0x03ffff95,26))
      .add(184, of(0x03ffff96,26))
      .add(185, of(0x03ffff97,26))
      .add(186, of(0x03ffff98,26))
      .add(187, of(0x03ffff99,26))
      .add(188, of(0x03ffff9a,26))
      .add(189, of(0x03ffff9b,26))
      .add(190, of(0x03ffff9c,26))
      .add(191, of(0x03ffff9d,26))
      .add(192, of(0x03ffff9e,26))
      .add(193, of(0x03ffff9f,26))
      .add(194, of(0x03ffffa0,26))
      .add(195, of(0x03ffffa1,26))
      .add(196, of(0x03ffffa2,26))
      .add(197, of(0x03ffffa3,26))
      .add(198, of(0x03ffffa4,26))
      .add(199, of(0x03ffffa5,26))
      .add(200, of(0x03ffffa6,26))
      .add(201, of(0x03ffffa7,26))
      .add(202, of(0x03ffffa8,26))
      .add(203, of(0x03ffffa9,26))
      .add(204, of(0x03ffffaa,26))
      .add(205, of(0x03ffffab,26))
      .add(206, of(0x03ffffac,26))
      .add(207, of(0x03ffffad,26))
      .add(208, of(0x03ffffae,26))
      .add(209, of(0x03ffffaf,26))
      .add(210, of(0x03ffffb0,26))
      .add(211, of(0x03ffffb1,26))
      .add(212, of(0x03ffffb2,26))
      .add(213, of(0x03ffffb3,26))
      .add(214, of(0x03ffffb4,26))
      .add(215, of(0x03ffffb5,26))
      .add(216, of(0x03ffffb6,26))
      .add(217, of(0x03ffffb7,26))
      .add(218, of(0x03ffffb8,26))
      .add(219, of(0x03ffffb9,26))
      .add(220, of(0x03ffffba,26))
      .add(221, of(0x03ffffbb,26))
      .add(222, of(0x03ffffbc,26))
      .add(223, of(0x03ffffbd,26))
      .add(224, of(0x03ffffbe,26))
      .add(225, of(0x03ffffbf,26))
      .add(226, of(0x03ffffc0,26))
      .add(227, of(0x03ffffc1,26))
      .add(228, of(0x03ffffc2,26))
      .add(229, of(0x03ffffc3,26))
      .add(230, of(0x03ffffc4,26))
      .add(231, of(0x03ffffc5,26))
      .add(232, of(0x03ffffc6,26))
      .add(233, of(0x03ffffc7,26))
      .add(234, of(0x03ffffc8,26))
      .add(235, of(0x03ffffc9,26))
      .add(236, of(0x03ffffca,26))
      .add(237, of(0x03ffffcb,26))
      .add(238, of(0x03ffffcc,26))
      .add(239, of(0x03ffffcd,26))
      .add(240, of(0x03ffffce,26))
      .add(241, of(0x03ffffcf,26))
      .add(242, of(0x03ffffd0,26))
      .add(243, of(0x03ffffd1,26))
      .add(244, of(0x03ffffd2,26))
      .add(245, of(0x03ffffd3,26))
      .add(246, of(0x03ffffd4,26))
      .add(247, of(0x03ffffd5,26))
      .add(248, of(0x03ffffd6,26))
      .add(249, of(0x03ffffd7,26))
      .add(250, of(0x03ffffd8,26))
      .add(251, of(0x03ffffd9,26))
      .add(252, of(0x03ffffda,26))
      .add(253, of(0x03ffffdb,26))
      .add(254, of(0x03ffffdc,26))
      .add(255, of(0x03ffffdd,26))
      .add(256, of(0x00000034,6))
      .add(257, of(0x00000035,6))
      .add(258, of(0x00000036,6))
      .add(259, of(0x0000000f,5))
      .add(260, of(0x00000010,5))
      .add(261, of(0x00000011,5))
      .get();

  public static final Huffman RESPONSE_TABLE = 
    make()
      .add(0, of(0x03ffffbe,26))
      .add(1, of(0x03ffffbf,26))
      .add(2, of(0x03ffffc0,26))
      .add(3, of(0x03ffffc1,26))
      .add(4, of(0x03ffffc2,26))
      .add(5, of(0x03ffffc3,26))
      .add(6, of(0x03ffffc4,26))
      .add(7, of(0x03ffffc5,26))
      .add(8, of(0x03ffffc6,26))
      .add(9, of(0x03ffffc7,26))
      .add(10, of(0x03ffffc8,26))
      .add(11, of(0x03ffffc9,26))
      .add(12, of(0x03ffffca,26))
      .add(13, of(0x03ffffcb,26))
      .add(14, of(0x03ffffcc,26))
      .add(15, of(0x03ffffcd,26))
      .add(16, of(0x03ffffce,26))
      .add(17, of(0x03ffffcf,26))
      .add(18, of(0x03ffffd0,26))
      .add(19, of(0x03ffffd1,26))
      .add(20, of(0x03ffffd2,26))
      .add(21, of(0x03ffffd3,26))
      .add(22, of(0x03ffffd4,26))
      .add(23, of(0x03ffffd5,26))
      .add(24, of(0x03ffffd6,26))
      .add(25, of(0x03ffffd7,26))
      .add(26, of(0x03ffffd8,26))
      .add(27, of(0x03ffffd9,26))
      .add(28, of(0x03ffffda,26))
      .add(29, of(0x03ffffdb,26))
      .add(30, of(0x03ffffdc,26))
      .add(31, of(0x03ffffdd,26))
      .add(32, of(0x00000000,4))
      .add(33, of(0x00000ffa,12))
      .add(34, of(0x00000064,7))
      .add(35, of(0x00003ffa,14))
      .add(36, of(0x00007ffc,15))
      .add(37, of(0x000003f0,10))
      .add(38, of(0x000003f1,10))
      .add(39, of(0x00001ffa,13))
      .add(40, of(0x000003f2,10))
      .add(41, of(0x000003f3,10))
      .add(42, of(0x00001ffb,13))
      .add(43, of(0x000007fc,11))
      .add(44, of(0x00000026,6))
      .add(45, of(0x00000065,7))
      .add(46, of(0x00000066,7))
      .add(47, of(0x000000ec,8))
      .add(48, of(0x00000001,4))
      .add(49, of(0x00000002,4))
      .add(50, of(0x00000003,4))
      .add(51, of(0x00000008,5))
      .add(52, of(0x00000009,5))
      .add(53, of(0x0000000a,5))
      .add(54, of(0x00000027,6))
      .add(55, of(0x00000028,6))
      .add(56, of(0x00000029,6))
      .add(57, of(0x0000002a,6))
      .add(58, of(0x0000000b,5))
      .add(59, of(0x000001ea,9))
      .add(60, of(0x0000fffc,16))
      .add(61, of(0x00000067,7))
      .add(62, of(0x00003ffb,14))
      .add(63, of(0x00001ffc,13))
      .add(64, of(0x0001fffc,17))
      .add(65, of(0x000000ed,8))
      .add(66, of(0x000001eb,9))
      .add(67, of(0x000001ec,9))
      .add(68, of(0x000001ed,9))
      .add(69, of(0x000001ee,9))
      .add(70, of(0x000000ee,8))
      .add(71, of(0x0000002b,6))
      .add(72, of(0x000003f4,10))
      .add(73, of(0x000001ef,9))
      .add(74, of(0x000000ef,8))
      .add(75, of(0x000003f5,10))
      .add(76, of(0x000003f6,10))
      .add(77, of(0x0000002c,6))
      .add(78, of(0x000000f0,8))
      .add(79, of(0x000000f1,8))
      .add(80, of(0x000001f0,9))
      .add(81, of(0x000003f7,10))
      .add(82, of(0x000003f8,10))
      .add(83, of(0x00000068,7))
      .add(84, of(0x0000002d,6))
      .add(85, of(0x000001f1,9))
      .add(86, of(0x000003f9,10))
      .add(87, of(0x000000f2,8))
      .add(88, of(0x000003fa,10))
      .add(89, of(0x000003fb,10))
      .add(90, of(0x000003fc,10))
      .add(91, of(0x00000ffb,12))
      .add(92, of(0x00003ffc,14))
      .add(93, of(0x00000ffc,12))
      .add(94, of(0x00007ffd,15))
      .add(95, of(0x000001f2,9))
      .add(96, of(0x0003fffe,18))
      .add(97, of(0x0000002e,6))
      .add(98, of(0x00000069,7))
      .add(99, of(0x0000002f,6))
      .add(100, of(0x0000006a,7))
      .add(101, of(0x0000000c,5))
      .add(102, of(0x0000006b,7))
      .add(103, of(0x0000006c,7))
      .add(104, of(0x0000006d,7))
      .add(105, of(0x0000006e,7))
      .add(106, of(0x000001f3,9))
      .add(107, of(0x000001f4,9))
      .add(108, of(0x0000006f,7))
      .add(109, of(0x00000070,7))
      .add(110, of(0x00000071,7))
      .add(111, of(0x00000030,6))
      .add(112, of(0x00000072,7))
      .add(113, of(0x000001f5,9))
      .add(114, of(0x00000073,7))
      .add(115, of(0x00000074,7))
      .add(116, of(0x00000031,6))
      .add(117, of(0x00000075,7))
      .add(118, of(0x000000f3,8))
      .add(119, of(0x000001f6,9))
      .add(120, of(0x000000f4,8))
      .add(121, of(0x000001f7,9))
      .add(122, of(0x000003fd,10))
      .add(123, of(0x0001fffd,17))
      .add(124, of(0x00003ffd,14))
      .add(125, of(0x0001fffe,17))
      .add(126, of(0x0000fffd,16))
      .add(127, of(0x03ffffde,26))
      .add(128, of(0x03ffffdf,26))
      .add(129, of(0x03ffffe0,26))
      .add(130, of(0x03ffffe1,26))
      .add(131, of(0x03ffffe2,26))
      .add(132, of(0x03ffffe3,26))
      .add(133, of(0x03ffffe4,26))
      .add(134, of(0x03ffffe5,26))
      .add(135, of(0x03ffffe6,26))
      .add(136, of(0x03ffffe7,26))
      .add(137, of(0x03ffffe8,26))
      .add(138, of(0x03ffffe9,26))
      .add(139, of(0x03ffffea,26))
      .add(140, of(0x03ffffeb,26))
      .add(141, of(0x03ffffec,26))
      .add(142, of(0x03ffffed,26))
      .add(143, of(0x03ffffee,26))
      .add(144, of(0x03ffffef,26))
      .add(145, of(0x03fffff0,26))
      .add(146, of(0x03fffff1,26))
      .add(147, of(0x03fffff2,26))
      .add(148, of(0x03fffff3,26))
      .add(149, of(0x03fffff4,26))
      .add(150, of(0x03fffff5,26))
      .add(151, of(0x03fffff6,26))
      .add(152, of(0x03fffff7,26))
      .add(153, of(0x03fffff8,26))
      .add(154, of(0x03fffff9,26))
      .add(155, of(0x03fffffa,26))
      .add(156, of(0x03fffffb,26))
      .add(157, of(0x03fffffc,26))
      .add(158, of(0x03fffffd,26))
      .add(159, of(0x03fffffe,26))
      .add(160, of(0x03ffffff,26))
      .add(161, of(0x01ffff80,25))
      .add(162, of(0x01ffff81,25))
      .add(163, of(0x01ffff82,25))
      .add(164, of(0x01ffff83,25))
      .add(165, of(0x01ffff84,25))
      .add(166, of(0x01ffff85,25))
      .add(167, of(0x01ffff86,25))
      .add(168, of(0x01ffff87,25))
      .add(169, of(0x01ffff88,25))
      .add(170, of(0x01ffff89,25))
      .add(171, of(0x01ffff8a,25))
      .add(172, of(0x01ffff8b,25))
      .add(173, of(0x01ffff8c,25))
      .add(174, of(0x01ffff8d,25))
      .add(175, of(0x01ffff8e,25))
      .add(176, of(0x01ffff8f,25))
      .add(177, of(0x01ffff90,25))
      .add(178, of(0x01ffff91,25))
      .add(179, of(0x01ffff92,25))
      .add(180, of(0x01ffff93,25))
      .add(181, of(0x01ffff94,25))
      .add(182, of(0x01ffff95,25))
      .add(183, of(0x01ffff96,25))
      .add(184, of(0x01ffff97,25))
      .add(185, of(0x01ffff98,25))
      .add(186, of(0x01ffff99,25))
      .add(187, of(0x01ffff9a,25))
      .add(188, of(0x01ffff9b,25))
      .add(189, of(0x01ffff9c,25))
      .add(190, of(0x01ffff9d,25))
      .add(191, of(0x01ffff9e,25))
      .add(192, of(0x01ffff9f,25))
      .add(193, of(0x01ffffa0,25))
      .add(194, of(0x01ffffa1,25))
      .add(195, of(0x01ffffa2,25))
      .add(196, of(0x01ffffa3,25))
      .add(197, of(0x01ffffa4,25))
      .add(198, of(0x01ffffa5,25))
      .add(199, of(0x01ffffa6,25))
      .add(200, of(0x01ffffa7,25))
      .add(201, of(0x01ffffa8,25))
      .add(202, of(0x01ffffa9,25))
      .add(203, of(0x01ffffaa,25))
      .add(204, of(0x01ffffab,25))
      .add(205, of(0x01ffffac,25))
      .add(206, of(0x01ffffad,25))
      .add(207, of(0x01ffffae,25))
      .add(208, of(0x01ffffaf,25))
      .add(209, of(0x01ffffb0,25))
      .add(210, of(0x01ffffb1,25))
      .add(211, of(0x01ffffb2,25))
      .add(212, of(0x01ffffb3,25))
      .add(213, of(0x01ffffb4,25))
      .add(214, of(0x01ffffb5,25))
      .add(215, of(0x01ffffb6,25))
      .add(216, of(0x01ffffb7,25))
      .add(217, of(0x01ffffb8,25))
      .add(218, of(0x01ffffb9,25))
      .add(219, of(0x01ffffba,25))
      .add(220, of(0x01ffffbb,25))
      .add(221, of(0x01ffffbc,25))
      .add(222, of(0x01ffffbd,25))
      .add(223, of(0x01ffffbe,25))
      .add(224, of(0x01ffffbf,25))
      .add(225, of(0x01ffffc0,25))
      .add(226, of(0x01ffffc1,25))
      .add(227, of(0x01ffffc2,25))
      .add(228, of(0x01ffffc3,25))
      .add(229, of(0x01ffffc4,25))
      .add(230, of(0x01ffffc5,25))
      .add(231, of(0x01ffffc6,25))
      .add(232, of(0x01ffffc7,25))
      .add(233, of(0x01ffffc8,25))
      .add(234, of(0x01ffffc9,25))
      .add(235, of(0x01ffffca,25))
      .add(236, of(0x01ffffcb,25))
      .add(237, of(0x01ffffcc,25))
      .add(238, of(0x01ffffcd,25))
      .add(239, of(0x01ffffce,25))
      .add(240, of(0x01ffffcf,25))
      .add(241, of(0x01ffffd0,25))
      .add(242, of(0x01ffffd1,25))
      .add(243, of(0x01ffffd2,25))
      .add(244, of(0x01ffffd3,25))
      .add(245, of(0x01ffffd4,25))
      .add(246, of(0x01ffffd5,25))
      .add(247, of(0x01ffffd6,25))
      .add(248, of(0x01ffffd7,25))
      .add(249, of(0x01ffffd8,25))
      .add(250, of(0x01ffffd9,25))
      .add(251, of(0x01ffffda,25))
      .add(252, of(0x01ffffdb,25))
      .add(253, of(0x01ffffdc,25))
      .add(254, of(0x01ffffdd,25))
      .add(255, of(0x01ffffde,25))
      .add(256, of(0x0000000d,5))
      .add(257, of(0x0000000e,5))
      .add(258, of(0x0000000f,5))
      .add(259, of(0x00000010,5))
      .add(260, of(0x00000011,5))
      .add(261, of(0x00000012,5))
      .get();
}
