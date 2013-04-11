package snell.http2.utils;
import java.io.IOException;
import java.io.OutputStream;

import com.google.common.primitives.Ints;
import com.google.common.primitives.Longs;
import com.google.common.primitives.Shorts;
import static java.lang.Math.min;


public final class BitBucket {

  private byte[] bucket;
  private int bsa_boff;
  private int num_bits;
  private int last_idx;
  private int idx_byte;
  private int idx_boff;
  
  public BitBucket(byte[] bucket) {
    this.bucket = bucket;
    bsa_boff = 0;
    idx_byte = 0;
    idx_boff = 0;
    num_bits = 0;
    last_idx = 0;    
  }
  
  public BitBucket(int s) {
    this(Ints.toByteArray(s));
  }
  
  public BitBucket() {
    reset(300);
  }
  
  public boolean getBit() {
    if (idx_byte >= bucket.length) 
      throw new IllegalStateException();
    byte mask = (byte)(0x80 >> idx_boff);
    boolean bit = (bucket[idx_byte] & mask) == mask;
    ++idx_boff;
    if (idx_boff >= 8) {
      ++idx_byte;
      idx_boff -= 8;
    }
    return bit;
  }
  
  public byte[] getBits(int numbits) {
    int output_bytes = (numbits+7)/8;
    int pos = 0;
    byte[] output = new byte[output_bytes];
    if (numbits > num_bits) 
      throw new IllegalStateException();
    int bits_left = numbits;
    if (idx_boff == 0) {
      System.arraycopy(bucket, idx_byte, output, 0, output_bytes);
      idx_byte += numbits / 8;
      idx_boff = numbits % 8;
      if (idx_boff > 0)
        output[output_bytes-1] &= ~(0xFF >> idx_boff);
    } else {
      int idx_leftover = 8 - idx_boff;
      while(bits_left >= 8) {
        byte c = (byte)(bucket[idx_byte] << idx_boff);
        ++idx_byte;
        c |= bucket[idx_byte] >> idx_leftover;
        output[pos++] = c;
        bits_left -= 8;
      }
      if (bits_left > 0) {
        int cur_boff = 0;
        byte cur_byte = 0;
        while(true) {
          int bits_to_consume = min(min(8-cur_boff,idx_leftover), bits_left);
          int mask = ~(0xff >> bits_to_consume);
          cur_byte |= ((bucket[idx_byte] << idx_boff) & mask) >> cur_boff;
          bits_left -= bits_to_consume;
          idx_boff += bits_to_consume;
          if (idx_boff >= 8) {
            ++idx_byte;
            idx_boff -= 8;
          }
          cur_boff += bits_to_consume;
          if (cur_boff >= 8)
            throw new IllegalStateException();
          if (bits_left == 0) {
            output[pos++] = cur_byte;
            break;
          }
        }
      }
    }
    return output;
  }
  
  public void writeTo(OutputStream out) throws IOException {
    out.write(bucket,0,last_idx+1+(bsa_boff != 0?1:0));
  }
  
  public void reset() {
    reset(200);
  }
  
  private void reset(int cap) {
    bucket = new byte[cap]; // initial capacity
    bsa_boff = 0;
    idx_byte = 0;
    idx_boff = 0;
    num_bits = 0;
    last_idx = 0;
  }
  
  public BitBucket storeBit(boolean on) {
    ++num_bits;
    int byte_idx = ((num_bits + 7) / 8) - 1;
    if (byte_idx >= bucket.length)
      resize();
    bucket[byte_idx] |= (on?1:0) << (7-bsa_boff);
    ++bsa_boff;
    bsa_boff %= 8;
    return this;
  }

  public BitBucket storeBit8(byte val) {
    num_bits += 8;
    if (last_idx + 2 >= bucket.length)
      resize();
    if (bsa_boff == 0) {
      bucket[last_idx++] = val;
    } else {
      int left = 8 - bsa_boff;
      bucket[last_idx++] |= (byte)((val&0xFF) >>> bsa_boff);
      bucket[last_idx] = (byte)((val&0xFF) << left);
    }
    return this;
  }
  
  public BitBucket storeBits(byte val, int count) {
    count = min(8,count);
    if (last_idx + 2 >= bucket.length)
      resize();
    val = (byte)(val & ~(0xFF >>> count));
    num_bits += count;
    if (bsa_boff == 0) {
      bucket[last_idx] = val;
      if (count >= 8)
        last_idx++;
    } else {
      int left = 8 - bsa_boff;
      bucket[last_idx] |= (byte)((val&0xFF) >>> bsa_boff);
      if (count >= left)
        bucket[++last_idx] = (byte)((val&0xFF) << left);
    }
    bsa_boff += count;
    bsa_boff %= 8;
    return this;
  }
  
  public BitBucket storeBit16(short val) {
    return storeBits(Shorts.toByteArray(val));
  }

  public BitBucket storeBit32(int val) {
    byte[] bytes = Ints.toByteArray(val);
    for (byte b : bytes)
      storeBit8(b);
    return this;
  }
  
  public BitBucket storeBit64(long val, int numbits) {
    if (numbits > 64 || numbits < 0)
      throw new IllegalArgumentException();
    return storeBits(Longs.toByteArray(val),numbits);
  }
  
  public BitBucket storeBit16(short val, int numbits) {
    if (numbits > 16 || numbits < 0)
      throw new IllegalArgumentException();
    return storeBits(Shorts.toByteArray(val),numbits);
  }
  
  public BitBucket storeBit32(IntPair pair) {
    if (pair == null)
      throw new NullPointerException();
    return storeBit32(pair.one(),pair.two());
  }
  
  public BitBucket storeBit32(int val, int numbits) {
    if (numbits > 32 || numbits < 0)
      throw new IllegalArgumentException();
    return storeBits(Ints.toByteArray(val),numbits);
  }
  
  public BitBucket storeBit64(long val) {
    return storeBits(Longs.toByteArray(val));
  }
  
  public BitBucket storeBits(byte[] bytes) {
    return storeBits(bytes,0,bytes.length,bytes.length*8);
  }
  
  public BitBucket storeBits(byte[] bytes, int numbits) {
    return storeBits(bytes,0,bytes.length,numbits);
  }
  
  public BitBucket storeBits(byte[] bytes, int s, int count, int numbits) {
    if (bytes == null)
      throw new NullPointerException();
    if (s+count > bytes.length || s < 0)
      throw new IndexOutOfBoundsException();
    if (count < 0 || numbits < 0)
      throw new IllegalArgumentException();
    int p = s;
    while (numbits > 0 && p < bytes.length && p < (s+count)) {
      int r = min(8, numbits);
      storeBits(bytes[p++],r);
      numbits -= r;
    }
    return this;
  }
  
  private void resize() {
    // there are likely more efficient scales we can use.. but this works for now
    byte[] new_bucket = new byte[bucket.length + 8];
    System.arraycopy(bucket, 0, new_bucket, 0, bucket.length);
    this.bucket = new_bucket;
  }
  
}
