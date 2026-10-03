package ac.cult.placement;

import java.util.Set;
import org.objectweb.asm.AnnotationVisitor;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.TypePath;

/**
 * Narrows the pinned 26.3 bootstrap to the client model (see {@code ModelScope}).
 *
 * <ul>
 *   <li>{@code BuiltInRegistries}: skipped registries are left empty, and the debug-only
 *       empty/default validation (which would report them) is not run.</li>
 *   <li>{@code Bootstrap}: the command selector, dispenser and creative tab setups, which only
 *       a server or the UI reads, are not run.</li>
 *   <li>{@code DefaultAttributes}: each living entity type's attributes are built on first
 *       request, so registering 93 mob types no longer loads and initializes every mob class.</li>
 *   <li>Shape and block cache fields the startup compaction pass shares are not {@code final}
 *       (otherwise identical), so that pass never mutates a final field.</li>
 * </ul>
 */
final class ModelNarrowing {
    private static final String SCOPE = VanillaClassTransform.RUNTIME + "ModelScope";
    private static final String REGISTRIES = "net/minecraft/core/registries/BuiltInRegistries";
    private static final String BOOTSTRAP = "net/minecraft/server/Bootstrap";
    private static final String ATTRIBUTES = "net/minecraft/world/entity/ai/attributes/DefaultAttributes";
    private static final String SUPPLIER = "net/minecraft/world/entity/ai/attributes/AttributeSupplier";
    private static final String BUILDER_FACTORY = "()L" + SUPPLIER + "$Builder;";
    private static final Set<String> SERVER_BOOTSTRAP_STEPS = Set.of(
            "net/minecraft/commands/arguments/selector/options/EntitySelectorOptions.bootStrap",
            "net/minecraft/core/dispenser/DispenseItemBehavior.bootStrap",
            "net/minecraft/world/item/CreativeModeTabs.validate");

    private ModelNarrowing() {}

    private static final Set<String> COMPACTED_FIELDS = Set.of(
            "net/minecraft/world/phys/shapes/VoxelShape.shape",
            "net/minecraft/world/phys/shapes/ArrayVoxelShape.xs",
            "net/minecraft/world/phys/shapes/ArrayVoxelShape.ys",
            "net/minecraft/world/phys/shapes/ArrayVoxelShape.zs",
            "net/minecraft/world/level/block/state/BlockBehaviour$BlockStateBase$Cache.collisionShape");

    static boolean applies(String internalName) {
        return internalName.equals(REGISTRIES)
                || internalName.equals(BOOTSTRAP)
                || internalName.equals(ATTRIBUTES)
                || COMPACTED_FIELDS.stream().anyMatch(field -> field.startsWith(internalName + "."));
    }

    static ClassVisitor visitor(String internalName, ClassVisitor next) {
        return new ClassVisitor(Opcodes.ASM9, next) {
            @Override
            public org.objectweb.asm.FieldVisitor visitField(
                    int access, String name, String descriptor, String signature, Object value) {
                if (COMPACTED_FIELDS.contains(internalName + "." + name)) access &= ~Opcodes.ACC_FINAL;
                return super.visitField(access, name, descriptor, signature, value);
            }

            @Override
            public MethodVisitor visitMethod(
                    int access, String name, String descriptor, String signature, String[] exceptions) {
                var target = super.visitMethod(access, name, descriptor, signature, exceptions);
                return switch (internalName + "." + name + descriptor) {
                    case REGISTRIES
                            + ".lambda$createContents$0(Lnet/minecraft/resources/Identifier;Ljava/util/function/Supplier;)V" ->
                        skipRegistries(target);
                    case REGISTRIES + ".bootStrap()V" -> withoutValidation(target);
                    case BOOTSTRAP + ".bootStrap()V" -> withoutServerSteps(target);
                    case ATTRIBUTES + ".<clinit>()V" -> deferredAttributes(target);
                    case ATTRIBUTES + ".getSupplier(Lnet/minecraft/world/entity/EntityType;)L" + SUPPLIER + ";" ->
                        replaceBody(target, method -> {
                            method.visitFieldInsn(Opcodes.GETSTATIC, ATTRIBUTES, "SUPPLIERS", "Ljava/util/Map;");
                            method.visitVarInsn(Opcodes.ALOAD, 0);
                            method.visitMethodInsn(
                                    Opcodes.INVOKESTATIC,
                                    SCOPE,
                                    "attributes",
                                    "(Ljava/util/Map;Ljava/lang/Object;)Ljava/lang/Object;",
                                    false);
                            method.visitTypeInsn(Opcodes.CHECKCAST, SUPPLIER);
                            method.visitInsn(Opcodes.ARETURN);
                            method.visitMaxs(2, 1);
                        });
                    default -> target;
                };
            }
        };
    }

    /** Returns early from a registry's bootstrap callback when the model skips that registry. */
    private static MethodVisitor skipRegistries(MethodVisitor target) {
        return new MethodVisitor(Opcodes.ASM9, target) {
            @Override
            public void visitCode() {
                super.visitCode();
                Label bootstrap = new Label();
                super.visitVarInsn(Opcodes.ALOAD, 0);
                super.visitMethodInsn(Opcodes.INVOKESTATIC, SCOPE, "skipsRegistry", "(Ljava/lang/Object;)Z", false);
                super.visitJumpInsn(Opcodes.IFEQ, bootstrap);
                super.visitInsn(Opcodes.RETURN);
                super.visitLabel(bootstrap);
                super.visitFrame(Opcodes.F_SAME, 0, null, 0, null);
            }

            @Override
            public void visitMaxs(int stack, int locals) {
                super.visitMaxs(Math.max(stack, 1), locals);
            }
        };
    }

    private static MethodVisitor withoutValidation(MethodVisitor target) {
        return new MethodVisitor(Opcodes.ASM9, target) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                if (opcode == Opcodes.INVOKESTATIC && owner.equals(REGISTRIES) && name.equals("validate"))
                    super.visitInsn(Opcodes.POP);
                else super.visitMethodInsn(opcode, owner, name, descriptor, itf);
            }
        };
    }

    private static MethodVisitor withoutServerSteps(MethodVisitor target) {
        return new MethodVisitor(Opcodes.ASM9, target) {
            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                if (opcode == Opcodes.INVOKESTATIC
                        && descriptor.equals("()V")
                        && SERVER_BOOTSTRAP_STEPS.contains(owner + "." + name)) return;
                super.visitMethodInsn(opcode, owner, name, descriptor, itf);
            }
        };
    }

    /** {@code X.factory().build()} becomes a token naming the factory; anything else is left as is. */
    private static MethodVisitor deferredAttributes(MethodVisitor target) {
        return new MethodVisitor(Opcodes.ASM9, target) {
            private String owner, factory;

            private void flush() {
                if (owner == null) return;
                super.visitMethodInsn(Opcodes.INVOKESTATIC, owner, factory, BUILDER_FACTORY, false);
                owner = null;
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String descriptor, boolean itf) {
                if (opcode == Opcodes.INVOKESTATIC && descriptor.equals(BUILDER_FACTORY) && !itf) {
                    flush();
                    this.owner = owner;
                    factory = name;
                    return;
                }
                if (this.owner != null
                        && opcode == Opcodes.INVOKEVIRTUAL
                        && owner.equals(SUPPLIER + "$Builder")
                        && name.equals("build")
                        && descriptor.equals("()L" + SUPPLIER + ";")) {
                    super.visitLdcInsn(this.owner);
                    super.visitLdcInsn(factory);
                    super.visitMethodInsn(
                            Opcodes.INVOKESTATIC,
                            SCOPE,
                            "attributes",
                            "(Ljava/lang/String;Ljava/lang/String;)Ljava/lang/Object;",
                            false);
                    this.owner = null;
                    return;
                }
                flush();
                super.visitMethodInsn(opcode, owner, name, descriptor, itf);
            }

            @Override
            public void visitInsn(int opcode) {
                flush();
                super.visitInsn(opcode);
            }

            @Override
            public void visitIntInsn(int opcode, int operand) {
                flush();
                super.visitIntInsn(opcode, operand);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                flush();
                super.visitVarInsn(opcode, var);
            }

            @Override
            public void visitTypeInsn(int opcode, String type) {
                flush();
                super.visitTypeInsn(opcode, type);
            }

            @Override
            public void visitFieldInsn(int opcode, String owner, String name, String descriptor) {
                flush();
                super.visitFieldInsn(opcode, owner, name, descriptor);
            }

            @Override
            public void visitInvokeDynamicInsn(
                    String name, String descriptor, org.objectweb.asm.Handle bsm, Object... args) {
                flush();
                super.visitInvokeDynamicInsn(name, descriptor, bsm, args);
            }

            @Override
            public void visitJumpInsn(int opcode, Label label) {
                flush();
                super.visitJumpInsn(opcode, label);
            }

            @Override
            public void visitLabel(Label label) {
                flush();
                super.visitLabel(label);
            }

            @Override
            public void visitLdcInsn(Object value) {
                flush();
                super.visitLdcInsn(value);
            }

            @Override
            public void visitIincInsn(int var, int increment) {
                flush();
                super.visitIincInsn(var, increment);
            }

            @Override
            public void visitTableSwitchInsn(int min, int max, Label dflt, Label... labels) {
                flush();
                super.visitTableSwitchInsn(min, max, dflt, labels);
            }

            @Override
            public void visitLookupSwitchInsn(Label dflt, int[] keys, Label[] labels) {
                flush();
                super.visitLookupSwitchInsn(dflt, keys, labels);
            }

            @Override
            public void visitMultiANewArrayInsn(String descriptor, int dims) {
                flush();
                super.visitMultiANewArrayInsn(descriptor, dims);
            }

            @Override
            public void visitFrame(int type, int locals, Object[] local, int stack, Object[] stackItems) {
                flush();
                super.visitFrame(type, locals, local, stack, stackItems);
            }

            @Override
            public void visitMaxs(int stack, int locals) {
                flush();
                super.visitMaxs(stack + 1, locals);
            }
        };
    }

    /** Writes a new body; keeps the method's declaration annotations and drops the original code. */
    private static MethodVisitor replaceBody(MethodVisitor target, java.util.function.Consumer<MethodVisitor> body) {
        return new MethodVisitor(Opcodes.ASM9) {
            @Override
            public AnnotationVisitor visitAnnotation(String descriptor, boolean visible) {
                return target.visitAnnotation(descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitTypeAnnotation(int ref, TypePath path, String descriptor, boolean visible) {
                return target.visitTypeAnnotation(ref, path, descriptor, visible);
            }

            @Override
            public AnnotationVisitor visitParameterAnnotation(int parameter, String descriptor, boolean visible) {
                return target.visitParameterAnnotation(parameter, descriptor, visible);
            }

            @Override
            public void visitAnnotableParameterCount(int count, boolean visible) {
                target.visitAnnotableParameterCount(count, visible);
            }

            @Override
            public void visitCode() {
                target.visitCode();
                body.accept(target);
            }

            @Override
            public void visitEnd() {
                target.visitEnd();
            }
        };
    }
}
