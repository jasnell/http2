package snell.http2.headers.delta;

import static snell.http2.headers.delta.Operation.makeClone;
import static snell.http2.headers.delta.Operation.makeKvsto;
import static snell.http2.headers.delta.Operation.makeToggl;
import static snell.http2.headers.delta.Operation.makeTrang;
import static snell.http2.headers.delta.Operation.Code.CLONE;
import static snell.http2.headers.delta.Operation.Code.ECLONE;
import static snell.http2.headers.delta.Operation.Code.EKVSTO;
import static snell.http2.headers.delta.Operation.Code.ETOGGL;
import static snell.http2.headers.delta.Operation.Code.ETRANG;
import static snell.http2.headers.delta.Operation.Code.KVSTO;
import static snell.http2.headers.delta.Operation.Code.TOGGL;
import static snell.http2.headers.delta.Operation.Code.TRANG;
import static snell.http2.headers.delta.Operation.Code.get;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import snell.http2.headers.HeaderSet;
import snell.http2.headers.HeaderSetter;
import snell.http2.headers.ValueSupplier;
import snell.http2.headers.delta.Operation.Code;
import snell.http2.headers.delta.Operation.Toggl;
import snell.http2.utils.IntPair;
import snell.http2.utils.Pair;

import com.google.common.base.Objects;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;

@SuppressWarnings("rawtypes")
public final class Delta {

  private final Context context = 
    new Context();
  private final Context resp_context = 
    new Context();
  private final byte group_id;
  
  public Delta(byte group_id) {
    this.group_id = group_id;
  }
  
  public String toString() {
    return Objects.toStringHelper("Delta")
      .add("Request", context)
      .add("Response", resp_context)
      .toString();
  }
  
  private static final Code[] EPHS = {ETOGGL,ETRANG,ECLONE};
  
  public void decodeFrom(
    InputStream in, 
    HeaderSetter<?> set) throws IOException {
      // Read Group ID
      byte g_id = (byte) in.read();
      ImmutableMultimap.Builder<Code,Operation> ops = 
        ImmutableMultimap.builder();
      byte[] header = new byte[2];
      while(in.read(header) > -1) {
        Operation.Code code = 
          get(header[0]);
        while (header[1]-- >= 0)
          ops.put(
            code, 
            code.parse(in));
      }
      HeaderGroup group = 
        resp_context
          .headerGroup(g_id, true);
      Multimap<Code,Operation> instructions = 
        ops.build();
      Delta.executeInstructions(
        group.storage(), 
        group, 
        instructions);
      Set<Pair<String,ValueSupplier>> keys_to_turn_off = 
        new HashSet<Pair<String,ValueSupplier>>();
      for (Code code : EPHS)
        for (Operation op : instructions.get(code))
          op.ephemeralExecute(group, keys_to_turn_off,set);
      adjustHeaderGroupEntries(group, group.storage());
      group.set(set, keys_to_turn_off);
      for (Operation op : instructions.get(EKVSTO))
        op.ephemeralExecute(group, keys_to_turn_off, set);
  }
  
  // Serialization of Operations...
  
  private final Operation.Code[] SER_ORDER = {
    TOGGL,
    ETOGGL,
    TRANG,
    ETRANG,
    ECLONE,
    EKVSTO,
    CLONE,
    KVSTO
  };
  
  public void encodeTo(
    OutputStream buffer, 
    HeaderSet<?> headers) 
      throws IOException {
    Multimap<Code,Operation> instructions = 
      makeOperations(headers,group_id);
    preprocessToggles(instructions);
    buffer.write(group_id);
    for (Operation.Code code : SER_ORDER)
      outputOps(
        buffer, 
        instructions.get(code), 
        code.code());
  }
  
  /**
   * Take the input set of toggls and attempt to collapse
   * them into trangs if possible, otherwise keep them 
   * as toggls. In theory, this ought to reduce the 
   * overall serialization bits so long as the index
   * identifiers are sequential.
   * 
   * This has not yet been optimized for performance... 
   * the implementation may change significantly over
   * time but the basic idea is straightforward..
   * 
   * Note: one optimization we make is that we skip
   * conversion to trans if the number of toggls is
   * less than 7... this is because the overhead of 
   * encoding the trangs is >= 7 additional bytes, 
   * so we wouldn't actually be saving anything, 
   * in fact we could end up wasting space
   */
  private void preprocessToggles(
    Multimap<Code,Operation> instructions) {
      Collection<Operation> toggls = 
        instructions.get(TOGGL);
      if (toggls.size() < 7) {
        // it's likely not going to be worth the extra opcode overhead
        // so let's skip out and not worry about compacting them
        return;
      }
      Range<Integer> range = null;
      List<Operation> toggs = 
        new ArrayList<Operation>();
      Collection<Operation> trang = 
        instructions.get(TRANG);
      Iterator<Operation> ops = 
        toggls.iterator();
      while(ops.hasNext()) {
        Toggl toggl = (Toggl) ops.next();
        Range<Integer> tr = 
          Range.singleton(toggl.index());
        if (range == null) {
          range = tr;
        } else if (tr.lowerEndpoint() - range.upperEndpoint() == 1) {
          range = range.span(tr);
          if (!ops.hasNext())
            rangeToOperation(
              range, 
              toggs, 
              trang);
        } else {
          rangeToOperation(
            range, 
            toggs, 
            trang);
          if (ops.hasNext()) {
            range = Range.singleton(toggl.index());
          } else {
            toggs.add(toggl);
          }
        }
      }
      instructions.replaceValues(TOGGL, toggs);
      instructions.putAll(TRANG, trang);
  }
  
  private void rangeToOperation(
    Range<Integer> r, 
    Collection<Operation> toggls, 
    Collection<Operation> trang) {
    if (r.upperEndpoint() - r.lowerEndpoint() > 0) {
      trang.add(makeTrang(r.lowerEndpoint(), r.upperEndpoint()));
    } else {
      toggls.add(makeToggl(r.lowerEndpoint()));
    }
  }
  
  private void outputOps(
    OutputStream buffer, 
    Collection<Operation> ops, 
    byte opcode) 
      throws IOException {
    if (ops == null) return;
    int ops_idx = 0;
    int ops_len = ops.size();
    Iterator<Operation> i = 
      ops.iterator();
    while (ops_len > ops_idx) {
      int ops_to_go = ops_len - ops_idx;
      int batch = Math.min(ops_to_go, 256);
      int iteration_end = 
        batch + ops_idx;
      buffer.write(opcode);
      buffer.write((byte)(batch - 1));
      for (int x = ops_idx; x < iteration_end; x++) {
        i.next().writeTo(buffer);
        ops_idx++;
      }
    }
  }
  
  private Multimap<Code,Operation> makeOperations(
    HeaderSet<?> headers, 
    byte group_id) {
    return makeOperations(
      headers, 
      context.headerGroup(
        group_id, true));
  }
  
  private static Multimap<Code,Operation> makeOperations(
    HeaderSet<?> headers, 
    HeaderGroup group) {
      Storage storage = 
        group.storage();
      Multimap<Code,Operation> instructions =
        HashMultimap.create();
      for (int idx : group.getIndices()) {
        Pair<String,ValueSupplier> pair =
          storage.lookup(idx);
        // If our headers do not contain an existing key-value pair,
        // Create a TOGGL to remove it...
        if (!headers.contains(
          pair.one(), 
          pair.two()))
          instructions.put(
            TOGGL, 
            Operation.makeToggl(idx));
      }
      // Now go through the current header set to see what we're turning on
      for (String key : headers)
        for (ValueSupplier<?> val : headers.get(key))
          processKv(
            group, 
            storage, 
            key, 
            val,
            instructions);
      // Once we have constructed our set of operations,
      // execute them to update our header group state
      executeInstructions(
        storage, 
        group, 
        instructions);
      // Adjust the existing set of entry indices in the storage
      // based on use... 
      adjustHeaderGroupEntries(group, storage);
      return instructions;
  }
   
    private static final ImmutableSet<String> ALWAYS_EREF = 
      ImmutableSet.of(
        "referer", 
        ":path", 
        "authorization", 
        "www-authenticate",
        "proxy-authenticate",
        "date",
        "last-modified");
  
    private static boolean processKv(
      HeaderGroup group, 
      Storage storage, 
      String key, 
      ValueSupplier<?> val, 
      Multimap<Code,Operation> instructions) {
      IntPair pair = 
        storage.locate(key, val);
      int kidx = pair.one();
      int vidx = pair.two();
      // There's nothing to do, the header group already contains this pair
      if (vidx > -1 && group.hasEntry(vidx))
        return false;
      if (ALWAYS_EREF.contains(key)) {
        if (kidx > -1) {
          instructions.put(
            ECLONE, 
            makeClone(kidx,val));
        } else {
          instructions.put(
            EKVSTO, 
            makeKvsto(key,val));
        }
        return true;
      }
      if (vidx > -1) // TOGGL ON
        instructions.put(
          TOGGL, 
          makeToggl(vidx));
      else if (kidx > -1)
        instructions.put(
          CLONE, 
          makeClone(kidx, val));
      else
        instructions.put(
          KVSTO, 
          makeKvsto(key, val));
      return true;
    }
    
    private static final Code[] EXECUTE_ORDER = {
      TOGGL,
      TRANG,
      CLONE,
      KVSTO};
    
    private static void executeInstructions(
      Storage storage, 
      HeaderGroup group, 
      Multimap<Code,Operation> instructions) {
        for (Code code : EXECUTE_ORDER)
          for (Operation op : instructions.get(code))
            op.execute(storage,group);
    }
    
    private static void adjustHeaderGroupEntries(
      HeaderGroup group, 
      Storage storage) {
//        IntMap ri = new IntMap();
//        for (int i : group.getIndices()) {
//          if (!storage.touchIdx(i)) {
//            Pair<String,ValueSupplier> pair =
//              storage.lookupfromIndex(i);
//            int ni = storage.insertVal(pair.one(), pair.two());
//            ri.put(i, ni);
//          }
//        }
//        group.reindexed(ri);
//        storage.reindex();
    }
  
}
