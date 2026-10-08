import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Checks references without starting Bukkit or initializing plugin classes. */
public class StaticCompatibility {
  static final String MAIN = "com/extendedclip/papi/expansion/checkitem/CheckItemExpansion";
  static final Map<String, ClassNode> cache = new HashMap<>();
  static final Set<String> verified = new TreeSet<>();

  static ClassNode type(String name) throws IOException {
    if (cache.containsKey(name)) return cache.get(name);
    try (InputStream in = ClassLoader.getSystemResourceAsStream(name + ".class")) {
      if (in == null) throw new AssertionError("Missing class: " + name);
      ClassNode node = new ClassNode();
      new ClassReader(in).accept(node, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
      cache.put(name, node);
      return node;
    }
  }

  static boolean member(String owner, String name, String desc, boolean field, Set<String> seen) throws IOException {
    if (owner == null || !seen.add(owner)) return false;
    ClassNode node = type(owner);
    if (field) {
      for (FieldNode f : node.fields) if (f.name.equals(name) && f.desc.equals(desc)) return true;
    } else {
      for (MethodNode m : node.methods) if (m.name.equals(name) && m.desc.equals(desc)) return true;
      if (name.equals("<init>")) return false;
    }
    for (String parent : node.interfaces) if (member(parent, name, desc, field, seen)) return true;
    return member(node.superName, name, desc, field, seen);
  }

  static boolean relevant(String owner) {
    return owner.startsWith("org/bukkit/") || owner.startsWith("me/clip/placeholderapi/")
        || owner.startsWith("de/shaded/checkitem/nbtapi/") || owner.startsWith(MAIN);
  }

  static void reference(String owner, String name, String desc, boolean field) throws IOException {
    if (!relevant(owner)) return;
    String ref = owner + "." + name + desc;
    if (!member(owner, name, desc, field, new HashSet<>())) throw new AssertionError("Missing member: " + ref);
    verified.add(ref);
  }

  static void require(boolean condition, String message) {
    if (!condition) throw new AssertionError(message);
  }

  public static void main(String[] args) throws Exception {
    int classes = 0;
    try (JarFile jar = new JarFile(args[0])) {
      require(jar.getJarEntry("plugin.yml") == null && jar.getJarEntry("paper-plugin.yml") == null,
          "Must remain an expansion, not a standalone plugin");
      require(jar.getJarEntry("org/bukkit/Bukkit.class") == null, "Bukkit must stay provided");
      require(jar.getJarEntry("me/clip/placeholderapi/PlaceholderAPI.class") == null, "PAPI must stay provided");
      Properties nbt = new Properties();
      nbt.load(jar.getInputStream(jar.getJarEntry("META-INF/maven/de.tr7zw/item-nbt-api/pom.properties")));
      require("2.16.1".equals(nbt.getProperty("version")), "Wrong NBT-API version");
      Properties project = new Properties();
      project.load(jar.getInputStream(jar.getJarEntry("META-INF/maven/Expansion-CheckItem/Expansion-CheckItem/pom.properties")));
      require("2.7.9-tflfix1".equals(project.getProperty("version")), "Wrong project version");
      require(type(MAIN).superName.equals("me/clip/placeholderapi/expansion/PlaceholderExpansion"), "Wrong superclass");
      ClassNode versions = type("de/shaded/checkitem/nbtapi/utils/MinecraftVersion");
      require(versions.fields.stream().anyMatch(f -> f.name.equals("MC26_2")), "Missing MC26_2");
      require(versions.fields.stream().anyMatch(f -> f.name.equals("MC26_3")), "Missing MC26_3");
      for (JarEntry entry : Collections.list(jar.entries())) {
        require(!entry.getName().startsWith("de/tr7zw/changeme/nbtapi/"), "NBT package was not relocated");
        if (!entry.getName().startsWith(MAIN) || !entry.getName().endsWith(".class")) continue;
        classes++;
        byte[] bytes = jar.getInputStream(entry).readAllBytes();
        int major = ((bytes[6] & 255) << 8) | (bytes[7] & 255);
        require(major == 52, "Expansion must retain Java 8 bytecode target");
        ClassNode node = type(entry.getName().substring(0, entry.getName().length() - 6));
        for (MethodNode m : node.methods) {
          for (AbstractInsnNode i : m.instructions) {
            if (i instanceof MethodInsnNode) {
              MethodInsnNode call = (MethodInsnNode) i;
              reference(call.owner, call.name, call.desc, false);
            } else if (i instanceof FieldInsnNode) {
              FieldInsnNode f = (FieldInsnNode) i;
              reference(f.owner, f.name, f.desc, true);
            } else if (i instanceof TypeInsnNode) {
              String name = ((TypeInsnNode) i).desc;
              if (relevant(name)) type(name);
            }
          }
        }
      }
      Class<?> expansion = Class.forName(MAIN.replace('/', '.'), false, ClassLoader.getSystemClassLoader());
      expansion.getDeclaredMethods();
      expansion.getDeclaredConstructors();
      require(type(MAIN).methods.stream().anyMatch(m -> m.name.equals("getVersion") &&
          java.util.stream.StreamSupport.stream(m.instructions.spliterator(), false).anyMatch(i ->
              i instanceof LdcInsnNode && "2.7.9-tflfix1".equals(((LdcInsnNode)i).cst))), "Wrong getVersion()");
      System.out.println("PASS: " + args[1] + "; expansion classes=" + classes + "; resolved references=" + verified.size());
      System.out.println("PASS: PAPI subclass, class loading without initialization, Java 8 target, provided APIs, NBT-API 2.16.1 relocated, MC26_2/MC26_3, version 2.7.9-tflfix1");
      System.out.println("Scope: static linking only; no live server, PAPI registration, NBT reflection or MythicMobs/resource-pack integration was executed.");
    }
  }
}
