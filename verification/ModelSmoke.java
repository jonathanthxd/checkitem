import java.lang.reflect.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;
import org.objectweb.asm.*;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

/** Isolated smoke test of the real expansion. Bukkit/PAPI infrastructure is simulated. */
public class ModelSmoke implements Opcodes {
  static final String MAIN = "com.extendedclip.papi.expansion.checkitem.CheckItemExpansion";
  static final Map<String, byte[]> fixtures = new HashMap<>();
  static ClassWriter writer(String name, String parent, String... interfaces) {
    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
    cw.visit(V1_8, ACC_PUBLIC, name.replace('.', '/'), null, parent, interfaces);
    return cw;
  }
  static MethodVisitor method(ClassWriter cw, int access, String name, String desc) {
    MethodVisitor mv = cw.visitMethod(access, name, desc, null, null);
    mv.visitCode(); return mv;
  }
  static void end(MethodVisitor mv) { mv.visitMaxs(0,0); mv.visitEnd(); }
  static void save(String name, ClassWriter cw) { cw.visitEnd(); fixtures.put(name, cw.toByteArray()); }
  static void fixture(String name, String iface, String field, String desc, String getter, String ctorDesc) {
    String owner = name.replace('.', '/');
    ClassWriter cw = writer(name, "java/lang/Object", iface);
    cw.visitField(ACC_PUBLIC, field, desc, null, null).visitEnd();
    MethodVisitor mv = method(cw, ACC_PUBLIC, "<init>", ctorDesc);
    mv.visitVarInsn(ALOAD,0); mv.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false);
    mv.visitVarInsn(ALOAD,0); mv.visitVarInsn(ALOAD,1); mv.visitFieldInsn(PUTFIELD,owner,field,desc);
    mv.visitInsn(RETURN); end(mv);
    mv = method(cw, ACC_PUBLIC, getter, "()" + desc);
    mv.visitVarInsn(ALOAD,0); mv.visitFieldInsn(GETFIELD,owner,field,desc); mv.visitInsn(ARETURN); end(mv);
    if (iface.equals("org/bukkit/inventory/meta/ItemMeta")) {
      mv = method(cw, ACC_PUBLIC, "hasItemModel", "()Z");
      mv.visitVarInsn(ALOAD,0); mv.visitFieldInsn(GETFIELD,owner,field,desc);
      mv.visitMethodInsn(INVOKESTATIC,"java/util/Objects","nonNull","(Ljava/lang/Object;)Z",false);
      mv.visitInsn(IRETURN); end(mv);
    }
    if (iface.equals("org/bukkit/inventory/PlayerInventory")) {
      mv = method(cw, ACC_PUBLIC, "getItem", "(I)Lorg/bukkit/inventory/ItemStack;");
      mv.visitVarInsn(ALOAD,0); mv.visitFieldInsn(GETFIELD,owner,field,desc);
      mv.visitVarInsn(ILOAD,1); mv.visitInsn(AALOAD); mv.visitInsn(ARETURN); end(mv);
      mv = method(cw, ACC_PUBLIC, "getHeldItemSlot", "()I"); mv.visitInsn(ICONST_0); mv.visitInsn(IRETURN); end(mv);
    }
    save(name,cw);
  }
  static void setup(String version) {
    ClassWriter cw = writer("fixture.Server", "java/lang/Object", "org/bukkit/Server");
    MethodVisitor mv = method(cw, ACC_PUBLIC, "<init>", "()V");
    mv.visitVarInsn(ALOAD,0); mv.visitMethodInsn(INVOKESPECIAL,"java/lang/Object","<init>","()V",false); mv.visitInsn(RETURN); end(mv);
    mv = method(cw, ACC_PUBLIC, "getBukkitVersion", "()Ljava/lang/String;"); mv.visitLdcInsn(version); mv.visitInsn(ARETURN); end(mv);
    save("fixture.Server",cw);
    cw = writer("org.bukkit.Bukkit", "java/lang/Object");
    mv = method(cw, ACC_PUBLIC|ACC_STATIC, "getServer", "()Lorg/bukkit/Server;");
    mv.visitTypeInsn(NEW,"fixture/Server"); mv.visitInsn(DUP); mv.visitMethodInsn(INVOKESPECIAL,"fixture/Server","<init>","()V",false); mv.visitInsn(ARETURN); end(mv);
    save("org.bukkit.Bukkit",cw);
    cw = writer("me.clip.placeholderapi.PlaceholderAPI", "java/lang/Object");
    mv = method(cw, ACC_PUBLIC|ACC_STATIC, "setBracketPlaceholders", "(Lorg/bukkit/entity/Player;Ljava/lang/String;)Ljava/lang/String;");
    mv.visitVarInsn(ALOAD,1); mv.visitInsn(ARETURN); end(mv); save("me.clip.placeholderapi.PlaceholderAPI",cw);
    fixture("fixture.Meta", "org/bukkit/inventory/meta/ItemMeta", "model", "Lorg/bukkit/NamespacedKey;", "getItemModel", "(Lorg/bukkit/NamespacedKey;)V");
    fixture("fixture.Inventory", "org/bukkit/inventory/PlayerInventory", "items", "[Lorg/bukkit/inventory/ItemStack;", "getContents", "([Lorg/bukkit/inventory/ItemStack;)V");
    fixture("fixture.Player", "org/bukkit/entity/Player", "inventory", "Lorg/bukkit/inventory/PlayerInventory;", "getInventory", "(Lorg/bukkit/inventory/PlayerInventory;)V");
    cw = writer("fixture.Expansion", MAIN.replace('.','/'));
    mv = method(cw, ACC_PUBLIC, "<init>", "()V"); mv.visitVarInsn(ALOAD,0); mv.visitMethodInsn(INVOKESPECIAL,MAIN.replace('.','/'),"<init>","()V",false); mv.visitInsn(RETURN); end(mv);
    cw.visitField(ACC_PUBLIC,"warnings","I",null,null).visitEnd();
    mv = method(cw, ACC_PUBLIC, "log", "(Ljava/util/logging/Level;Ljava/lang/String;)V");
    mv.visitVarInsn(ALOAD,0); mv.visitInsn(DUP); mv.visitFieldInsn(GETFIELD,"fixture/Expansion","warnings","I"); mv.visitInsn(ICONST_1); mv.visitInsn(IADD); mv.visitFieldInsn(PUTFIELD,"fixture/Expansion","warnings","I"); mv.visitInsn(RETURN); end(mv); save("fixture.Expansion",cw);
  }
  static class Loader extends URLClassLoader {
    Loader(String jar) throws Exception { super(new URL[]{Path.of(jar).toUri().toURL()}, ModelSmoke.class.getClassLoader()); }
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
      synchronized(getClassLoadingLock(name)) {
        Class<?> c = findLoadedClass(name);
        if(c == null) {
          if(fixtures.containsKey(name)) { byte[] b = fixtures.get(name); c = defineClass(name,b,0,b.length); }
          else if(name.startsWith(MAIN)) c = findClass(name);
          else c = super.loadClass(name,false);
        }
        if(resolve) resolveClass(c); return c;
      }
    }
  }
  static void require(boolean c, String why) { if(!c) throw new AssertionError(why); }
  static ItemStack item(Material mat, int amount, ItemMeta meta) {
    return new ItemStack() {
      public Material getType() { return mat; }
      public int getAmount() { return amount; }
      public ItemMeta getItemMeta() { return meta; }
      public boolean hasItemMeta() { return meta != null; }
    };
  }
  public static void main(String[] args) throws Exception {
    setup(args[1]);
    try(Loader loader = new Loader(args[0])) {
      Class<?> cls = loader.loadClass(MAIN), wrap = loader.loadClass(MAIN + "$ItemWrapper");
      Object expansion = loader.loadClass("fixture.Expansion").getConstructor().newInstance();
      Method parser = cls.getDeclaredMethod("getWrapper",wrap,String.class,Player.class,boolean.class); parser.setAccessible(true);
      Method count = cls.getDeclaredMethod("getItemAmount",wrap,Player.class,ItemStack[].class); count.setAccessible(true);
      Method check = cls.getDeclaredMethod("checkItem",wrap,Player.class,ItemStack[].class); check.setAccessible(true);
      java.util.function.Supplier<Object> fresh = () -> { try { return wrap.getConstructor(cls).newInstance(expansion); } catch(Exception e) { throw new RuntimeException(e); } };
      Object wrapper = parser.invoke(expansion,fresh.get(),"mat:BONE,amt:50,itemmodel:custom:zombie_bone",null,false);
      NamespacedKey parsed = (NamespacedKey)wrap.getMethod("getItemModel").invoke(wrapper);
      NamespacedKey sameValue = NamespacedKey.fromString("custom:zombie_bone");
      require(parsed != sameValue && parsed.equals(sameValue),"Test must use distinct key objects");
      Constructor<?> metaCtor = loader.loadClass("fixture.Meta").getConstructor(NamespacedKey.class);
      ItemMeta good = (ItemMeta)metaCtor.newInstance(sameValue);
      ItemMeta bad = (ItemMeta)metaCtor.newInstance(NamespacedKey.fromString("custom:other"));
      ItemMeta none = (ItemMeta)metaCtor.newInstance(new Object[]{null});
      ItemStack[] items = {item(Material.BONE,25,good),item(Material.BONE,25,good),item(Material.BONE,64,bad),item(Material.BONE,64,none),item(Material.STONE,64,good)};
      require((Integer)count.invoke(expansion,wrapper,null,(Object)items) == 50,"Count only matching bone models");
      require((Boolean)check.invoke(expansion,wrapper,null,(Object)items),"50 matching bones must pass");
      require(!(Boolean)check.invoke(expansion,wrapper,null,(Object)new ItemStack[]{item(Material.BONE,49,good)}),"49 bones must fail");
      for(String invalid : new String[]{"itemmodel:","itemmodel:Custom:bone","itemmodel:custom:bad key","itemmodel:custom:bone:extra"}) {
        require(parser.invoke(expansion,fresh.get(),invalid,null,false) == null,"Reject invalid key: " + invalid);
      }
      require(expansion.getClass().getField("warnings").getInt(expansion) == 4,"Each rejected key must warn");
      require(parser.invoke(expansion,fresh.get(),"itemmodel:Custom:bone",null,true) == null,"getinfo rejects nonempty invalid keys");
      Object selector = parser.invoke(expansion,fresh.get(),"itemmodel:",null,true);
      require(selector != null && (Boolean)wrap.getMethod("shouldCheckItemModel").invoke(selector),"getinfo must accept empty model selector");
      PlayerInventory inv = (PlayerInventory)loader.loadClass("fixture.Inventory").getConstructor(ItemStack[].class).newInstance((Object)new ItemStack[]{item(Material.BONE,50,good)});
      Player p = (Player)loader.loadClass("fixture.Player").getConstructor(PlayerInventory.class).newInstance(inv);
      Method request = cls.getMethod("onPlaceholderRequest",Player.class,String.class);
      require("custom:zombie_bone".equals(request.invoke(expansion,p,"getinfo:mainhand_itemmodel:")),"Read model from mainhand");
      require(request.invoke(expansion,p,"getinfo:mainhand_itemmodel:Bad Key") == null,"Invalid getinfo key must not throw");
      require("2.7.9-tflfix1".equals(cls.getMethod("getVersion").invoke(expansion)),"Version");
      require(cls.getMethod("canRegister").invoke(expansion).equals(true),"canRegister");
      System.out.println("PASS " + args[1] + ": distinct equal model keys; matching count; 50/49 threshold; wrong/no model; wrong material; invalid/empty keys and warnings; experimental getinfo and invalid getinfo; getVersion/canRegister.");
      System.out.println("Isolated fixtures simulate infrastructure; no live PAPI registration, NBT calls or MythicMobs integration.");
    }
  }
}
