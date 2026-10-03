package ac.cult.placement.runtime;

import java.lang.invoke.CallSite;
import java.lang.invoke.ConstantCallSite;
import java.lang.invoke.LambdaConversionException;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandleInfo;
import java.lang.invoke.MethodHandleProxies;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.MutableCallSite;
import java.lang.invoke.WrongMethodTypeException;
import java.lang.reflect.Modifier;

/**
 * Bootstrap for non-capturing lambda sites in classes defined by the isolated vanilla loader.
 *
 * <p>{@link LambdaMetafactory} spins one hidden class per call site; the vanilla model links
 * about 9,300, ~80% of them non-capturing. Each non-capturing site here evaluates to one
 * constant instance of the JDK's single {@link MethodHandleProxies} class for its interface,
 * holding the implementation adapted to the instantiated interface type (the metafactory's
 * boxing, unboxing, widening and casts). Interfaces the proxy cannot implement keep the
 * JDK path.
 *
 * <p>{@link #lazyMetafactory} receives a method reference to another class symbolically
 * and resolves it with the caller's lookup, so with the caller's access, on first call.
 *
 * <p>Deliberately lambda-free: the loader rewrites every other class to bootstrap here.
 */
public final class SharedLambdas {
    private static final MethodHandle RESOLVE;

    static {
        try {
            RESOLVE = MethodHandles.lookup()
                    .findStatic(
                            SharedLambdas.class,
                            "resolveAndInvoke",
                            MethodType.methodType(Object.class, Reference.class, Object[].class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private SharedLambdas() {}

    public static CallSite metafactory(
            MethodHandles.Lookup caller,
            String name,
            MethodType factoryType,
            MethodType interfaceMethodType,
            MethodHandle implementation,
            MethodType dynamicMethodType)
            throws LambdaConversionException {
        Class<?> type = factoryType.returnType();
        if (factoryType.parameterCount() == 0 && shareable(type, interfaceMethodType)) {
            try {
                return constant(type, implementation.asType(dynamicMethodType));
            } catch (WrongMethodTypeException incompatible) {
                // Fall through to the metafactory, which reports the incompatibility itself.
            }
        }
        return LambdaMetafactory.metafactory(
                caller, name, factoryType, interfaceMethodType, implementation, dynamicMethodType);
    }

    public static CallSite lazyMetafactory(
            MethodHandles.Lookup caller,
            String name,
            MethodType factoryType,
            MethodType interfaceMethodType,
            int kind,
            String owner,
            String implementationName,
            String implementationDescriptor,
            String dynamicDescriptor)
            throws ReflectiveOperationException, LambdaConversionException {
        var reference =
                new Reference(caller, kind, owner, implementationName, implementationDescriptor, dynamicDescriptor);
        Class<?> type = factoryType.returnType();
        if (!shareable(type, interfaceMethodType))
            return LambdaMetafactory.metafactory(
                    caller,
                    name,
                    factoryType,
                    interfaceMethodType,
                    reference.implementation(),
                    reference.dynamicType());
        var site = new MutableCallSite(interfaceMethodType);
        reference.site = site;
        site.setTarget(RESOLVE.bindTo(reference)
                .asCollector(Object[].class, interfaceMethodType.parameterCount())
                .asType(interfaceMethodType));
        return constant(type, site.dynamicInvoker());
    }

    private static CallSite constant(Class<?> type, MethodHandle target) {
        return new ConstantCallSite(
                MethodHandles.constant(type, MethodHandleProxies.asInterfaceInstance(type, target)));
    }

    private static Object resolveAndInvoke(Reference reference, Object[] arguments) throws Throwable {
        return reference.resolve().invokeWithArguments(arguments);
    }

    private static boolean shareable(Class<?> type, MethodType interfaceMethodType) {
        if (!type.isInterface() || !Modifier.isPublic(type.getModifiers()) || type.isSealed() || type.isHidden())
            return false;
        try {
            // Also proves the interface has one abstract method of this erased type.
            MethodHandleProxies.asInterfaceInstance(type, MethodHandles.empty(interfaceMethodType));
            return true;
        } catch (IllegalArgumentException | WrongMethodTypeException unsupported) {
            return false;
        }
    }

    /** A method reference named by the class file; resolved with the caller's access. */
    private static final class Reference {
        private final MethodHandles.Lookup caller;
        private final int kind;
        private final String owner, name, descriptor, dynamicDescriptor;
        private MutableCallSite site;
        private MethodHandle resolved;

        Reference(
                MethodHandles.Lookup caller,
                int kind,
                String owner,
                String name,
                String descriptor,
                String dynamicDescriptor) {
            this.caller = caller;
            this.kind = kind;
            this.owner = owner;
            this.name = name;
            this.descriptor = descriptor;
            this.dynamicDescriptor = dynamicDescriptor;
        }

        MethodType dynamicType() {
            return MethodType.fromMethodDescriptorString(
                    dynamicDescriptor, caller.lookupClass().getClassLoader());
        }

        synchronized MethodHandle resolve() throws ReflectiveOperationException {
            if (resolved == null) {
                resolved = implementation().asType(dynamicType()).asType(site.type());
                site.setTarget(resolved);
                MutableCallSite.syncAll(new MutableCallSite[] {site});
            }
            return resolved;
        }

        MethodHandle implementation() throws ReflectiveOperationException {
            Class<?> type = caller.findClass(owner.replace('/', '.'));
            var signature = MethodType.fromMethodDescriptorString(
                    descriptor, caller.lookupClass().getClassLoader());
            return switch (kind) {
                case MethodHandleInfo.REF_invokeStatic -> caller.findStatic(type, name, signature);
                case MethodHandleInfo.REF_invokeVirtual, MethodHandleInfo.REF_invokeInterface ->
                    caller.findVirtual(type, name, signature);
                case MethodHandleInfo.REF_invokeSpecial ->
                    caller.findSpecial(type, name, signature, caller.lookupClass());
                case MethodHandleInfo.REF_newInvokeSpecial -> caller.findConstructor(type, signature);
                default -> throw new IllegalArgumentException("Unsupported method reference kind " + kind);
            };
        }
    }
}
