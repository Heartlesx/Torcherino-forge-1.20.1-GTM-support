import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 按规则表把 torcherino-7.6.jar 中已有的方法调用替换为 hook 类的静态方法调用。
 *
 * 替换采用「同形替换」：hook 方法在栈上与被替换的调用完全等价
 * （实例调用的接收者变成静态方法的第一个参数，其余参数与返回值不变），
 * 因此不需要重算 max stack / stackmap frames（ClassWriter 仍以 0 标志构造）。
 * 规则在 {@link #buildRules()} 里集中维护，每条规则都断言命中数。
 *
 * args:
 *   0) 原 jar
 *   1) hook 类目录（编译输出目录，含包结构；自动包含内部类如 Xxx$Inner.class）
 *   2) 输出 jar
 *   3) 可选 overlay 目录：目录内文件按相对路径覆盖/追加进新 jar
 */
public class Patcher {

    public static void main(String[] args) throws Exception {
        Path inJar = Paths.get(args[0]);
        Path hookDir = Paths.get(args[1]);
        Path outJar = Paths.get(args[2]);
        Path overlayDir = args.length > 3 ? Paths.get(args[3]) : null;

        List<Rule> rules = buildRules();
        Map<String, byte[]> hookClasses = readDirectory(hookDir);
        for (Rule rule : rules) {
            String hookClassEntry = rule.hookOwner + ".class";
            if (!hookClasses.containsKey(hookClassEntry)) {
                throw new IllegalStateException("hook class missing in " + hookDir + ": " + hookClassEntry
                        + " (rule " + rule.id + ")");
            }
        }

        Map<String, Path> overlayFiles = new HashMap<String, Path>();
        if (overlayDir != null && Files.isDirectory(overlayDir)) {
            Stream<Path> walk = Files.walk(overlayDir);
            try {
                walk.filter(Files::isRegularFile).forEach(path -> overlayFiles.put(
                        overlayDir.relativize(path).toString().replace('\\', '/'), path));
            } finally {
                walk.close();
            }
        }

        List<String> report = new ArrayList<String>();
        int[] totalReplacements = new int[1];

        ZipInputStream in = new ZipInputStream(Files.newInputStream(inJar));
        ZipOutputStream out = new ZipOutputStream(Files.newOutputStream(outJar));
        try {
            ZipEntry entry;
            while ((entry = in.getNextEntry()) != null) {
                String name = entry.getName();
                if ("META-INF/fml_cache_annotation.json".equals(name)
                        || "META-INF/fml_cache_class_versions.json".equals(name)) {
                    report.add("dropped stale FML scan cache: " + name);
                    continue;
                }
                byte[] original = readAll(in);
                byte[] data = original;
                Path overlay = overlayFiles.remove(name);
                if (overlay != null) {
                    data = Files.readAllBytes(overlay);
                    report.add("overlaid " + name);
                }
                if (name.endsWith(".class")) {
                    data = patchClass(data, name, rules, totalReplacements, report);
                }
                ZipEntry copy = new ZipEntry(name);
                copy.setTime(entry.getTime());
                out.putNextEntry(copy);
                out.write(data);
                out.closeEntry();
            }

            for (Map.Entry<String, byte[]> hook : hookClasses.entrySet()) {
                ZipEntry hookEntry = new ZipEntry(hook.getKey());
                out.putNextEntry(hookEntry);
                out.write(hook.getValue());
                out.closeEntry();
                report.add("added hook class " + hook.getKey());
            }

            for (Map.Entry<String, Path> extra : overlayFiles.entrySet()) {
                ZipEntry added = new ZipEntry(extra.getKey());
                out.putNextEntry(added);
                out.write(Files.readAllBytes(extra.getValue()));
                out.closeEntry();
                report.add("added " + extra.getKey());
            }
        } finally {
            out.close();
            in.close();
        }

        for (String line : report) {
            System.out.println(line);
        }

        boolean mismatch = false;
        for (Rule rule : rules) {
            boolean ok = rule.hits == rule.expected;
            mismatch |= !ok;
            System.out.println("rule " + rule.id + ": " + rule.hits + "/" + rule.expected + (ok ? " ok" : " MISMATCH")
                    + "   " + rule.owner + "." + rule.name + rule.desc
                    + " -> " + rule.hookOwner + "." + rule.hookName + rule.hookDesc);
        }
        System.out.println("total replacements: " + totalReplacements[0]);
        if (mismatch) {
            System.err.println("ERROR: rule hit count mismatch (expected values listed above)");
            System.exit(1);
        }
        System.out.println("written: " + outJar);
    }

    /** 所有替换规则集中在这里维护；新增功能时在这里加一条。 */
    private static List<Rule> buildRules() {
        List<Rule> rules = new ArrayList<Rule>();
        // GTCE 兼容层：额外 tick 循环中调用机器的 ITickable.update()
        rules.add(new Rule("gtce-tile-update",
                Opcodes.INVOKEINTERFACE, "net/minecraft/util/ITickable", "func_73660_a", "()V",
                "com/sci/torcherino/GTCECompat", "tickTile", 1));
        // 范围功能：每 tick 的扫描边界（私有方法，因此是 invokespecial）
        rules.add(new Rule("torch-bounds",
                Opcodes.INVOKESPECIAL, "com/sci/torcherino/blocks/tiles/TileTorcherino", "updateCachedModeIfNeeded", "()V",
                "com/sci/torcherino/RangeHooks", "updateBounds", 1));
        // 范围功能：右键交互（if/else 两个分支都会命中，钩子内部按 modifier 区分开界面 / 显示范围）
        rules.add(new Rule("torch-changemode",
                Opcodes.INVOKEVIRTUAL, "com/sci/torcherino/blocks/tiles/TileTorcherino", "changeMode", "(Z)V",
                "com/sci/torcherino/RangeHooks", "changeMode", 2));
        // 范围功能：动作条文本（显示真实形状与折算后的速度）
        rules.add(new Rule("torch-describe",
                Opcodes.INVOKEVIRTUAL, "com/sci/torcherino/blocks/tiles/TileTorcherino", "getDescription",
                "()Lnet/minecraft/util/text/TextComponentString;",
                "com/sci/torcherino/RangeHooks", "describe", 1));
        // 范围功能：注册新网络包（沿用原通道 id 1/2）
        rules.add(new Rule("packets-init",
                Opcodes.INVOKESTATIC, "com/sci/torcherino/network/PacketHandler", "preInit", "()V",
                "com/sci/torcherino/RangeHooks", "initPackets", 1));
        // 范围功能：捕获右键交互的玩家（owner 是事件的内部类，不是外层 PlayerInteractEvent）
        rules.add(new Rule("player-capture",
                Opcodes.INVOKEVIRTUAL, "net/minecraftforge/event/entity/player/PlayerInteractEvent$RightClickBlock",
                "getEntityPlayer", "()Lnet/minecraft/entity/player/EntityPlayer;",
                "com/sci/torcherino/RangeHooks", "capturePlayer", 1));
        // 二期：世界内绿色方框（客户端专属注入点，服务端不会加载这个类）
        rules.add(new Rule("client-hooks-init",
                Opcodes.INVOKESTATIC, "com/sci/torcherino/network/KeyHandler", "preInit", "()V",
                "com/sci/torcherino/client/RangeClientHooks", "init", 1));
        return rules;
    }

    /** 一条替换规则：命中 (opcode, owner, name, desc) 的调用替换为 hook 的静态方法。 */
    private static final class Rule {
        final String id;
        final int opcode;
        final String owner;
        final String name;
        final String desc;
        final String hookOwner;
        final String hookName;
        final String hookDesc;
        final int expected;
        int hits;

        Rule(String id, int opcode, String owner, String name, String desc,
             String hookOwner, String hookName, int expected) {
            this.id = id;
            this.opcode = opcode;
            this.owner = owner;
            this.name = name;
            this.desc = desc;
            this.hookOwner = hookOwner;
            this.hookName = hookName;
            this.hookDesc = staticEquivalent(owner, opcode, desc);
            this.expected = expected;
        }
    }

    /**
     * 同形替换要求 hook 方法与原调用在栈上等价：
     * 实例调用的接收者（owner 类型）成为静态方法的第一个参数，参数与返回值不变。
     */
    private static String staticEquivalent(String owner, int opcode, String desc) {
        if (opcode == Opcodes.INVOKESTATIC) {
            return desc;
        }
        return "(L" + owner + ";" + desc.substring(1);
    }

    private static Map<String, byte[]> readDirectory(Path dir) throws Exception {
        if (!Files.isDirectory(dir)) {
            throw new IllegalArgumentException("hook directory not found: " + dir);
        }
        Map<String, byte[]> files = new LinkedHashMap<String, byte[]>();
        Stream<Path> walk = Files.walk(dir);
        try {
            walk.filter(Files::isRegularFile).forEach(path -> {
                String rel = dir.relativize(path).toString().replace('\\', '/');
                try {
                    files.put(rel, Files.readAllBytes(path));
                } catch (Exception failure) {
                    throw new RuntimeException(failure);
                }
            });
        } finally {
            walk.close();
        }
        return files;
    }

    private static byte[] patchClass(byte[] classBytes, String name, List<Rule> rules, int[] counter, List<String> report) {
        final int[] hits = new int[1];
        ClassReader reader = new ClassReader(classBytes);
        ClassWriter writer = new ClassWriter(reader, 0);
        ClassVisitor visitor = new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String methodName, String methodDesc,
                                            String signature, String[] exceptions) {
                MethodVisitor mv = super.visitMethod(access, methodName, methodDesc, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, mv) {
                    @Override
                    public void visitMethodInsn(int opcode, String owner, String instName, String desc, boolean itf) {
                        for (Rule rule : rules) {
                            if (rule.opcode == opcode && rule.owner.equals(owner)
                                    && rule.name.equals(instName) && rule.desc.equals(desc)) {
                                rule.hits++;
                                hits[0]++;
                                counter[0]++;
                                super.visitMethodInsn(Opcodes.INVOKESTATIC, rule.hookOwner, rule.hookName,
                                        rule.hookDesc, false);
                                return;
                            }
                        }
                        super.visitMethodInsn(opcode, owner, instName, desc, itf);
                    }
                };
            }
        };
        reader.accept(visitor, 0);
        if (hits[0] > 0) {
            report.add("patched " + hits[0] + " call site(s) in " + name);
            return writer.toByteArray();
        }
        return classBytes;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }
}
