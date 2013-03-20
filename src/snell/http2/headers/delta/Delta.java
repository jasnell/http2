package snell.http2.headers.delta;

import static snell.http2.headers.delta.Operation.makeClone;
import static snell.http2.headers.delta.Operation.makeKvsto;
import static snell.http2.headers.delta.Operation.makeToggl;
import static snell.http2.headers.delta.Operation.Code.*;
import static snell.http2.headers.delta.Operation.Code.ECLONE;

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
import snell.http2.headers.ValueProvider;
import snell.http2.headers.delta.Operation.Clone;
import snell.http2.headers.delta.Operation.Kvsto;
import snell.http2.headers.delta.Operation.Toggl;
import snell.http2.headers.delta.Operation.Trang;
import snell.http2.utils.IntMap;
import snell.http2.utils.Pair;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;
import com.google.common.collect.Range;
import com.google.common.collect.TreeMultimap;

public final class Delta {

  private final Context context = 
    new Context();
  private final Context resp_context = 
    new Context();
  private final int group_id;
  
  public Delta(int group_id) {
    this.group_id = group_id;
  }
  
  public String toString() {
    return Objects.toStringHelper("Delta")
      .add("Request", context)
      .add("Response", resp_context)
      .toString();
  }
  
  public void decodeFrom(
    InputStream in, 
    HeaderSet<?> set) throws IOException {
      // Read Group ID
      int g_id = in.read(); // group id!
      if (g_id < 0)
        throw new IllegalStateException(); //Obviously that's not gonna work   
      ImmutableMultimap.Builder<String,Operation> ops = 
        ImmutableMultimap.builder();
      byte[] header = new byte[2];   
      while(in.read(header) > -1) {
        Operation.Code code = 
          get(header[0]);
        while (header[1]-- >= 0)
          ops.put(code.label(), code.parse(in));
      }
      HeaderGroup group = 
        resp_context.headerGroup(g_id, true);
      Multimap<String,Operation> instructions = 
        ops.build();
      Delta.executeInstructions(
        group.storage(), 
        group, 
        instructions);
      // ephemeral toggls...... bleh, this is ugly... need to improve this
      // issue: we need to grab the value references before calling
      // adjustHeaderGroupEntries, then we need to go through and determine
      // if it's a toggl on or toggl off... will refactor this so it's a 
      // cleaner implementation later on.
      Set<Pair<String,ValueProvider>> keys_to_turn_off = 
        new HashSet<Pair<String,ValueProvider>>();
      for (Operation op : instructions.get("etoggl")) {
        Toggl toggl = (Toggl) op;
        int idx = toggl.index();
        Pair<String,ValueProvider> pair = 
            group.storage().lookupfromIndex(idx);
          if (pair == null)
            throw new RuntimeException("Crap!");
        if (group.hasEntry(idx)) {
          // going to turn it off
          keys_to_turn_off.add(pair);
        } else {
          // going to turn it on, temporarily, just for his headerset
          set.set(pair.one(),pair.two());
        }
      }
      // ephemeral clones
      for (Operation op : instructions.get("eclone")) {
        Clone clone = (Clone) op;
        int idx = clone.index();
        String key = group.storage().lookupKeyFromIndex(idx);
        if (key == null)
          throw new RuntimeException("Crap!");
        set.set(key, clone.val());   
      }
      adjustHeaderGroupEntries(group, group.storage());
      group.set(set, keys_to_turn_off);
      // ephemeral kvsto operation...
      for (Operation op : instructions.get("ekvsto")) {
        Kvsto ref = (Kvsto) op;
        set.set(ref.key(), ref.val());
      }
  }
  
  // Serialization of Operations...
  
  private final Operation.Code[] SER_ORDER = {
    STOGGL,
    ETOGGL,
    STRANG,
    ETRANG,
    ECLONE,
    EKVSTO,
    SCLONE,
    SKVSTO
  };
  
  public void encodeTo(
    OutputStream buffer, 
    HeaderSet<?> headers) 
      throws IOException {
    Multimap<String,Operation> instructions = 
      makeOperations(headers,group_id);
    preprocessToggles(instructions);
    buffer.write((byte)group_id);
    for (Operation.Code code : SER_ORDER)
      outputOps(
        buffer, 
        instructions.get(code.label()), 
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
    Multimap<String,Operation> instructions) {
      Collection<Operation> toggls = 
        instructions.get("stoggl");
      if (toggls.size() < 7) {
        // it's likely not going to be worth the extra opcode overhead
        // so let's skip out and not worry about compacting them
        return;
      }
      Range<Integer> range = null;
      List<Operation> toggs = 
        new ArrayList<Operation>();
      Collection<Operation> trang = 
        instructions.get("strang");
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
      instructions.replaceValues("stoggl", toggs);
      instructions.putAll("strang", trang);
  }
  
  private void rangeToOperation(
    Range<Integer> r, 
    Collection<Operation> toggls, 
    Collection<Operation> trang) {
    if (r.upperEndpoint() - r.lowerEndpoint() > 0) {
      trang.add(Operation.makeTrang(r.lowerEndpoint(), r.upperEndpoint()));
    } else {
      toggls.add(Operation.makeToggl(r.lowerEndpoint()));
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
    Iterator<Operation> i = ops.iterator();
    while (ops_len > ops_idx) {
      int ops_to_go = ops_len - ops_idx;
      int iteration_end = 
        Math.min(ops_to_go, 256) + ops_idx;
      buffer.write(opcode);
      buffer.write((byte)(Math.min(256, ops_to_go) - 1));
      for (int x = ops_idx; x < iteration_end; x++) {
        i.next().writeTo(buffer);
        ops_idx++;
      }
    }
  }
  
  private Multimap<String,Operation> makeOperations(
    HeaderSet<?> headers, 
    int group_id) {
    return makeOperations(
      headers, 
      context.headerGroup(
        group_id, true));
  }
  
  public static Multimap<String,Operation> makeOperations(
    HeaderSet<?> headers, 
    HeaderGroup group) {
      Storage storage = 
        group.storage();
      Multimap<String,Operation> instructions =
        TreeMultimap.create();
      for (int idx : group.getIndices()) {
        Pair<String,ValueProvider> pair =
          storage.lookupfromIndex(idx);
        if (!headers.contains(pair.one(), pair.two()))
          instructions.put(
            "stoggl", 
            Operation.makeToggl(idx));
      }
      for (String key : headers)
        for (ValueProvider val : headers.get(key))
          processKv(
            group, 
            storage, 
            key, 
            val,
            instructions);
      executeInstructions(
        storage, 
        group, 
        instructions);
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
      ValueProvider val, 
      Multimap<String,Operation> instructions) {
      if (group.hasKV(key, val))
        return false;
      Pair<Integer,Integer> pair = 
        storage.findEntryIdx(key, val);
      if (ALWAYS_EREF.contains(key)) {
        if (pair.one() > -1) {
          instructions.put(
            "eclone", 
            makeClone(pair.one(),val));
        } else {
          instructions.put(
            "ekvsto", 
            makeKvsto(key, val));
        }
        return true;
      }
      if (pair.two() > -1 && 
          !group.hasEntry(pair.two())) {
        instructions.put(
          "stoggl", 
          makeToggl(pair.two()));
      } else if (pair.one() > -1) {
        instructions.put(
          "sclone", 
          makeClone(pair.one(), val));
      } else {
        instructions.put(
          "skvsto", 
          makeKvsto(key, val));
      }
      return true;
    }
    
    private static void executeInstructions(
      Storage storage, 
      HeaderGroup group, 
      Multimap<String,Operation> instructions) {
        for (Operation op : instructions.get("stoggl"))
          executeOperation(storage, group, op);
        for (Operation op : instructions.get("strang"))
          executeOperation(storage, group, op);
        for (Operation op : instructions.get("sclone")) {
          Operation.Clone clone = (Clone) op;
          int index = clone.index();
          String key = 
            storage.lookupKeyFromIndex(index);
          if (key == null)
            throw new RuntimeException("Crap!");
          executeOperation(storage, group, clone.asKvsto(key));        
        }
        for (Operation op : instructions.get("skvsto"))
          executeOperation(storage, group, op);
    }
    
    private static void executeOperation(
      Storage storage, 
      HeaderGroup group, 
      Operation op) {
        if (op instanceof Operation.Toggl) {
          Operation.Toggl toggl = (Toggl) op;
          group.toggle(toggl.index());
        } else if (op instanceof Operation.Trang) {
          Operation.Trang trang = (Trang) op;
          for (int n = trang.start(); n <= trang.end(); n++)
            group.toggle(n);
        } else if (op instanceof Operation.Clone) {
          throw new RuntimeException();
        } else if (op instanceof Operation.Kvsto) {
          Operation.Kvsto kvsto = (Kvsto) op;
          int index = storage.insertVal(kvsto.key(), kvsto.val());
          group.toggle(index);
        }
        
    }
    
    private static void adjustHeaderGroupEntries(
      HeaderGroup group, 
      Storage storage) {
        IntMap ri = new IntMap();
        for (int i : group.getIndices()) {
          if (!storage.touchIdx(i)) {
            Pair<String,ValueProvider> pair =
              storage.lookupfromIndex(i);
            int ni = storage.insertVal(pair.one(), pair.two());
            ri.put(i, ni);
          }
        }
        group.reindexed(ri);
        storage.reindex();
    }
  
}
