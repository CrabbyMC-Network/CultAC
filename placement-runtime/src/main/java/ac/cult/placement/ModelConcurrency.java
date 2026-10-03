package ac.cult.placement;

import java.util.Map;
import java.util.Set;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/** Cache guards for the pinned original 26.3 classes, never the host's Minecraft classes. */
final class ModelConcurrency {
    private static final String SUPPORT = VanillaClassTransform.RUNTIME + "ModelCaches";
    private static final String THREAD_LOCAL = VanillaClassTransform.RUNTIME + "ModelThreadLocal";
    private static final String CRITERIA = "net/minecraft/world/scores/criteria/ObjectiveCriteria";
    private static final Set<String> THREAD_CACHES = Set.of(
            "net/minecraft/world/level/block/Block",
            "net/minecraft/world/level/material/FlowingFluid",
            "net/minecraft/world/level/biome/Biome",
            "net/minecraft/util/profiling/Profiler");
    private static final Map<String, Set<String>> GUARDED = Map.of(
            "net/minecraft/stats/StatType", Set.of("get", "contains", "iterator"),
            "net/minecraft/world/phys/shapes/VoxelShape", Set.of("getFaceShape"),
            "net/minecraft/world/level/material/FlowingFluid", Set.of("getShape"),
            "net/minecraft/util/SingleKeyCache", Set.of("getValue"),
            "net/minecraft/util/ClassTreeIdRegistry", Set.of("getLastIdFor", "getCount", "define"),
            "net/minecraft/world/level/block/Block", Set.of("asItem"),
            "net/minecraft/world/entity/ai/attributes/AttributeInstance", Set.of("getValue"),
            "net/minecraft/core/HolderSet$Direct", Set.of("contains"));

    private ModelConcurrency() {}

    static ClassVisitor visitor(String owner, ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                // Keep identity maps and each cache's original computation. Only the short
                // lazy-cache method is exclusive; the surrounding action remains parallel.
                if (GUARDED.getOrDefault(owner, Set.of()).contains(name)) access |= Opcodes.ACC_SYNCHRONIZED;
                var method = super.visitMethod(access, name, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, method) {
                    @Override
                    public void visitFieldInsn(int opcode, String type, String field, String desc) {
                        if (owner.equals("net/minecraft/SharedConstants")
                                && opcode == Opcodes.PUTSTATIC
                                && type.equals("com/mojang/brigadier/exceptions/CommandSyntaxException")) {
                            // This library can be shared with Paper. Vanilla's assignment
                            // would replace the host's provider and retain this loader.
                            if (field.equals("BUILT_IN_EXCEPTIONS") || field.equals("ENABLE_COMMAND_STACK_TRACES")) {
                                super.visitInsn(Opcodes.POP);
                                return;
                            }
                        }
                        if (owner.equals(CRITERIA)
                                && opcode == Opcodes.PUTSTATIC
                                && type.equals(CRITERIA)
                                && (field.equals("CRITERIA_CACHE") || field.equals("CUSTOM_CRITERIA"))) {
                            // Different StatType monitors can construct stats concurrently.
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    "java/util/Collections",
                                    "synchronizedMap",
                                    "(Ljava/util/Map;)Ljava/util/Map;",
                                    false);
                        }
                        super.visitFieldInsn(opcode, type, field, desc);
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String type, String method, String desc, boolean itf) {
                        if (owner.equals("net/minecraft/stats/StatType")
                                && name.equals("iterator")
                                && type.equals("java/util/Collection")
                                && method.equals("iterator")) {
                            // Returning the map's iterator after releasing the monitor would
                            // still race a later get(). Copy its values while holding it.
                            super.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    SUPPORT,
                                    "snapshotIterator",
                                    "(Ljava/util/Collection;)Ljava/util/Iterator;",
                                    false);
                        } else if (THREAD_CACHES.contains(owner)
                                && opcode == Opcodes.INVOKESTATIC
                                && type.equals("java/lang/ThreadLocal")
                                && method.equals("withInitial")) {
                            // Own these caches in the model instead of leaving native cache
                            // values in long-lived packet threads after a model is retired.
                            super.visitMethodInsn(opcode, THREAD_LOCAL, method, desc, false);
                        } else if (owner.equals("net/minecraft/server/Bootstrap")
                                && opcode == Opcodes.INVOKESTATIC
                                && type.equals(owner)
                                && method.equals("wrapStreams")
                                && desc.equals("()V")) {
                            // A background model bootstrap must not replace the process's
                            // logging streams, even temporarily.
                        } else if (owner.equals("net/minecraft/SharedConstants")
                                && opcode == Opcodes.INVOKESTATIC
                                && type.equals("io/netty/util/ResourceLeakDetector")
                                && method.equals("setLevel")) {
                            // The prediction model must not change the host Netty policy.
                            super.visitInsn(Opcodes.POP);
                        } else {
                            super.visitMethodInsn(opcode, type, method, desc, itf);
                        }
                    }
                };
            }
        };
    }
}
