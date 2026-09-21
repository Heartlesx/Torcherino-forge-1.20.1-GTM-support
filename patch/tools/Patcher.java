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
 * 把 torcherino-7.6.jar 中所有对 ITickable.update()（func_73660_a）的调用
 * 替换为 GTCECompat.tickTile(...)。
 *
 * args:
 *   0) 原 jar
 *   1) hook 类目录（编译输出目录，含包结构；自动包含内部类如 Xxx$Inner.class）
 *   2) 输出 jar
 *   3) 可选 overlay 目录：目录内文件按相对路径覆盖/追加进新 jar
 */
public class Patcher {

    private static final String HOOK_MAIN_CLASS = "com/sci/torcherino/GTCECompat.class";
    private static final String HOOK_CLASS = "com/sci/torcherino/GTCECompat";
    private static final String HOOK_METHOD = "tickTile";
    private static final String HOOK_DESC = "(Lnet/minecraft/util/ITickable;)V";

    private static final String TILE_OWNER = "net/minecraft/util/ITickable";
    private static final String TILE_METHOD = "func_73660_a";
    private static final String TILE_DESC = "()V";

    public static void main(String[] args) throws Exception {
        Path inJar = Paths.get(args[0]);
        Path hookDir = Paths.get(args[1]);
        Path outJar = Paths.get(args[2]);
        Path overlayDir = args.length > 3 ? Paths.get(args[3]) : null;

        Map<String, byte[]> hookClasses = readDirectory(hookDir);
        if (!hookClasses.containsKey(HOOK_MAIN_CLASS)) {
            throw new IllegalStateException("hook class missing in " + hookDir + ": " + HOOK_MAIN_CLASS);
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
                    data = patchClass(data, name, totalReplacements, report);
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
        System.out.println("total replacements: " + totalReplacements[0]);
        if (totalReplacements[0] != 1) {
            System.err.println("ERROR: expected exactly 1 call site, found " + totalReplacements[0]);
            System.exit(1);
        }
        System.out.println("written: " + outJar);
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

    private static byte[] patchClass(byte[] classBytes, String name, int[] counter, List<String> report) {
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
                    public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                        if (opcode == Opcodes.INVOKEINTERFACE && TILE_OWNER.equals(owner)
                                && TILE_METHOD.equals(name) && TILE_DESC.equals(desc)) {
                            hits[0]++;
                            counter[0]++;
                            super.visitMethodInsn(Opcodes.INVOKESTATIC, HOOK_CLASS, HOOK_METHOD, HOOK_DESC, false);
                        } else {
                            super.visitMethodInsn(opcode, owner, name, desc, itf);
                        }
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
