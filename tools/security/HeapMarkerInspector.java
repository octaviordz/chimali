import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import shark.CloseableHeapGraph;
import shark.GcRoot;
import shark.HeapField;
import shark.HeapGraph;
import shark.HeapObject;
import shark.HeapValue;
import shark.HprofHeapGraph;
import shark.HprofRecord;

/** Exact synthetic marker search and Java-heap root reachability inspection for gated HPROFs. */
public final class HeapMarkerInspector {
  private record Marker(String kind, String text, byte[] bytes) {}
  private record Parent(long objectId, String edge, String rootType) {}

  public static void main(String[] args) throws Exception {
    if (args.length != 2) {
      throw new IllegalArgumentException("Usage: HeapMarkerInspector <heap.hprof> <expected-markers.txt>");
    }
    File heapFile = new File(args[0]);
    List<Marker> markers = readMarkers(new File(args[1]));
    try (CloseableHeapGraph graph = HprofHeapGraph.Companion.openHeapGraph(
        heapFile, null, Collections.emptySet())) {
      System.out.printf("objects=%d classes=%d instances=%d arrays=%d roots=%d%n",
          graph.getObjectCount(), graph.getClassCount(), graph.getInstanceCount(),
          graph.getObjectArrayCount() + graph.getPrimitiveArrayCount(), graph.getGcRoots().size());
      Map<Long, Parent> reachable = findReachable(graph);
      List<Hit> hits = findHits(graph, markers);
      for (int markerIndex = 0; markerIndex < markers.size(); markerIndex++) {
        Marker marker = markers.get(markerIndex);
        List<Hit> markerHits = new ArrayList<>();
        for (Hit hit : hits) if (hit.markerIndex == markerIndex) markerHits.add(hit);
        long rooted = markerHits.stream().filter(hit -> reachable.containsKey(hit.objectId)).count();
        System.out.printf("marker=%d kind=%s hits=%d rooted=%d%n",
            markerIndex + 1, marker.kind, markerHits.size(), rooted);
        Set<Long> emitted = new HashSet<>();
        for (Hit hit : markerHits) {
          if (reachable.containsKey(hit.objectId) && emitted.add(hit.objectId)) {
            System.out.println("  " + describe(graph, hit.objectId) + " via " + path(hit.objectId, reachable, graph));
          }
        }
      }
    }
  }

  private static List<Marker> readMarkers(File file) throws Exception {
    List<Marker> result = new ArrayList<>();
    int markerNumber = 0;
    for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
      if (line.isEmpty()) continue;
      if (line.startsWith("B64:")) {
        result.add(new Marker("bytes", null, Base64.getDecoder().decode(line.substring(4))));
      } else {
        boolean control = line.startsWith("CONTROL:");
        String value = control ? line.substring("CONTROL:".length()) : line;
        result.add(new Marker(control ? "positive-control" : "synthetic-text-" + (++markerNumber),
            value, value.getBytes(StandardCharsets.UTF_8)));
      }
    }
    return result;
  }

  private static Map<Long, Parent> findReachable(HeapGraph graph) {
    Map<Long, Parent> parents = new HashMap<>(graph.getObjectCount());
    ArrayDeque<Long> pending = new ArrayDeque<>();
    for (GcRoot root : graph.getGcRoots()) {
      long id = root.getId();
      if (graph.objectExists(id) && parents.putIfAbsent(id,
          new Parent(-1L, root.getClass().getSimpleName(), root.getClass().getSimpleName())) == null) {
        pending.addLast(id);
      }
    }
    while (!pending.isEmpty()) {
      long ownerId = pending.removeFirst();
      HeapObject owner = graph.findObjectById(ownerId);
      if (owner instanceof HeapObject.HeapInstance instance) {
        Iterator<HeapField> fields = instance.readFields().iterator();
        while (fields.hasNext()) {
          HeapField field = fields.next();
          addChild(field.getValue(), ownerId,
              instance.getInstanceClassName() + "." + field.getName(), graph, parents, pending);
        }
      } else if (owner instanceof HeapObject.HeapObjectArray array) {
        int index = 0;
        Iterator<HeapValue> elements = array.readElements().iterator();
        while (elements.hasNext()) {
          HeapValue value = elements.next();
          addChild(value, ownerId, array.getArrayClassName() + "[" + index++ + "]",
              graph, parents, pending);
        }
      } else if (owner instanceof HeapObject.HeapClass heapClass) {
        Iterator<HeapField> fields = heapClass.readStaticFields().iterator();
        while (fields.hasNext()) {
          HeapField field = fields.next();
          addChild(field.getValue(), ownerId,
              heapClass.getName() + ".static." + field.getName(), graph, parents, pending);
        }
      }
    }
    return parents;
  }

  private static void addChild(HeapValue value, long ownerId, String edge, HeapGraph graph,
      Map<Long, Parent> parents, ArrayDeque<Long> pending) {
    Long childId = value.getAsObjectId();
    if (childId == null || !graph.objectExists(childId)) return;
    Parent owner = parents.get(ownerId);
    if (parents.putIfAbsent(childId, new Parent(ownerId, edge, owner.rootType)) == null) {
      pending.addLast(childId);
    }
  }

  private static List<Hit> findHits(HeapGraph graph, List<Marker> markers) {
    List<Hit> hits = new ArrayList<>();
    Iterator<HeapObject> objects = graph.getObjects().iterator();
    while (objects.hasNext()) {
      HeapObject object = objects.next();
      if (object instanceof HeapObject.HeapInstance instance
          && "java.lang.String".equals(instance.getInstanceClassName())) {
        String value;
        try { value = instance.readAsJavaString(); } catch (RuntimeException ignored) { continue; }
        for (int i = 0; i < markers.size(); i++) {
          Marker marker = markers.get(i);
          if (marker.text != null && value.contains(marker.text)) {
            hits.add(new Hit(i, object.getObjectId()));
          }
        }
      } else if (object instanceof HeapObject.HeapPrimitiveArray array) {
        HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord record =
            (HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord) array.readRecord();
        if (record instanceof HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.CharArrayDump chars) {
          String value = new String(chars.getArray());
          for (int i = 0; i < markers.size(); i++) {
            Marker marker = markers.get(i);
            if (marker.text != null && value.contains(marker.text)) hits.add(new Hit(i, object.getObjectId()));
          }
        } else if (record instanceof HprofRecord.HeapDumpRecord.ObjectRecord.PrimitiveArrayDumpRecord.ByteArrayDump bytes) {
          byte[] value = bytes.getArray();
          for (int i = 0; i < markers.size(); i++) {
            if (contains(value, markers.get(i).bytes)) hits.add(new Hit(i, object.getObjectId()));
          }
        }
      }
    }
    return hits;
  }

  private static boolean contains(byte[] source, byte[] target) {
    if (target.length == 0 || target.length > source.length) return false;
    outer: for (int i = 0; i <= source.length - target.length; i++) {
      for (int j = 0; j < target.length; j++) if (source[i + j] != target[j]) continue outer;
      return true;
    }
    return false;
  }

  private static String describe(HeapGraph graph, long id) {
    HeapObject object = graph.findObjectById(id);
    if (object instanceof HeapObject.HeapInstance instance) return instance.getInstanceClassName();
    if (object instanceof HeapObject.HeapPrimitiveArray array) return array.getArrayClassName();
    if (object instanceof HeapObject.HeapObjectArray array) return array.getArrayClassName();
    if (object instanceof HeapObject.HeapClass heapClass) return "class " + heapClass.getName();
    return object.getClass().getSimpleName();
  }

  private static String path(long target, Map<Long, Parent> parents, HeapGraph graph) {
    List<String> edges = new ArrayList<>();
    long current = target;
    Parent parent;
    while ((parent = parents.get(current)) != null && parent.objectId != -1L) {
      edges.add(parent.edge);
      current = parent.objectId;
    }
    Collections.reverse(edges);
    String rootType = parents.get(current).rootType;
    return rootType + (edges.isEmpty() ? "" : " -> " + String.join(" -> ", edges))
        + " -> " + describe(graph, target);
  }

  private record Hit(int markerIndex, long objectId) {}
}
