package dev.hoyin1600p.sophisticated_vh_compat.compat;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

import static org.junit.jupiter.api.Assertions.*;

class BackportMixinContractTest {
    @Test
    void vanillaHooksHaveProductionMappings() throws Exception {
        try (var reader = java.nio.file.Files.newBufferedReader(java.nio.file.Path.of(System.getProperty("svhc.refmap")))) {
            var mappings = JsonParser.parseReader(reader).getAsJsonObject().getAsJsonObject("mappings");
            String prefix = "dev/hoyin1600p/sophisticated_vh_compat/mixin/";
            assertTrue(mappings.getAsJsonObject(prefix + "StorageContainerMenuMixin").get("doClick").getAsString().contains("m_150430_"));
            assertTrue(mappings.getAsJsonObject(prefix + "JeiTransferMixin")
                    .get("Lnet/minecraft/world/inventory/Slot;remove(I)Lnet/minecraft/world/item/ItemStack;").getAsString().contains("m_6201_"));
            assertNotNull(mappings.getAsJsonObject(prefix + "JeiRemainderPersistenceMixin")
                    .get("Lnet/minecraft/world/item/ItemStack;grow(I)V"));
        }
    }

    @TestFactory
    Stream<DynamicTest> releasedJarsExposeMixinContracts() throws Exception {
        var stream = getClass().getResourceAsStream("/sophisticated_vh_compat.mixins.json");
        assertNotNull(stream);
        var config = JsonParser.parseReader(new InputStreamReader(stream)).getAsJsonObject();
        List<String> names = new ArrayList<>();
        for (String side : List.of("mixins", "client")) {
            config.getAsJsonArray(side).forEach(name -> names.add(name.getAsString()));
        }
        return names.stream().map(name -> DynamicTest.dynamicTest(name, () -> verify(name)));
    }

    private void verify(String name) throws Exception {
        ClassNode mixin = read("dev/hoyin1600p/sophisticated_vh_compat/mixin/" + name);
        AnnotationNode annotation = annotations(mixin.invisibleAnnotations, mixin.visibleAnnotations).stream()
                .filter(a -> a.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;")).findFirst().orElseThrow();
        List<?> targets = (List<?>) value(annotation, "targets");
        String targetName = targets == null ? ((Type) ((List<?>) value(annotation, "value")).get(0)).getInternalName()
                : targets.get(0).toString().replace('.', '/');
        ClassNode target = read(targetName);
        for (var field : mixin.fields) {
            if (annotations(field.visibleAnnotations, field.invisibleAnnotations).stream().anyMatch(a -> a.desc.endsWith("/Shadow;"))) {
                ClassNode current = target;
                boolean found = false;
                while (current != null) {
                    if (current.fields.stream().anyMatch(f -> f.name.equals(field.name) && f.desc.equals(field.desc))) {
                        found = true;
                        break;
                    }
                    current = current.superName == null ? null : read(current.superName);
                }
                assertTrue(found, "Missing shadow field " + field.name + field.desc);
            }
        }
        for (MethodNode handler : mixin.methods) {
            for (AnnotationNode a : annotations(handler.visibleAnnotations, handler.invisibleAnnotations)) {
                if (a.desc.endsWith("/Shadow;")) {
                    assertTrue(target.methods.stream().anyMatch(m -> m.name.equals(handler.name) && m.desc.equals(handler.desc)),
                            "Missing shadow method " + handler.name + handler.desc);
                }
                Object selectors = value(a, "method");
                if (!(selectors instanceof List<?> methods)) {
                    continue;
                }
                for (Object selector : methods) {
                    String s = selector.toString();
                    List<MethodNode> matches = target.methods.stream().filter(m -> s.equals(m.name) || s.equals(m.name + m.desc)).toList();
                    assertFalse(matches.isEmpty(), "Missing target " + s);
                    if (a.desc.endsWith("/Inject;")) {
                        Type[] args = Type.getArgumentTypes(handler.desc);
                        if (args.length > 1) {
                            for (MethodNode match : matches) {
                                Type[] targetArgs = Type.getArgumentTypes(match.desc);
                                assertEquals(targetArgs.length + 1, args.length, handler.name + " callback arity for " + s);
                                for (int i = 0; i < targetArgs.length; i++) {
                                    assertEquals(targetArgs[i], args[i], handler.name + " argument " + i);
                                }
                            }
                        }
                    }
                    Object atValue = value(a, "at");
                    List<?> ats = atValue instanceof List<?> list ? list : atValue == null ? List.of() : List.of(atValue);
                    for (Object at : ats) {
                        AnnotationNode injection = (AnnotationNode) at;
                        String invocation = (String) value(injection, "target");
                        if (!"INVOKE".equals(value(injection, "value")) || invocation == null
                                || Boolean.TRUE.equals(value(injection, "remap"))) {
                            continue;
                        }
                        assertTrue(matches.stream().anyMatch(m -> {
                            for (var instruction : m.instructions) {
                                if (instruction instanceof MethodInsnNode call
                                        && invocation.equals("L" + call.owner + ";" + call.name + call.desc)) {
                                    return true;
                                }
                            }
                            return false;
                        }), handler.name + " missing invocation " + invocation + " in " + s);
                    }
                }
            }
        }
    }

    private static List<AnnotationNode> annotations(List<AnnotationNode> first, List<AnnotationNode> second) {
        List<AnnotationNode> result = new ArrayList<>();
        if (first != null) result.addAll(first);
        if (second != null) result.addAll(second);
        return result;
    }

    private static Object value(AnnotationNode annotation, String name) {
        if (annotation.values != null) {
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if (name.equals(annotation.values.get(i))) return annotation.values.get(i + 1);
            }
        }
        return null;
    }

    private ClassNode read(String name) throws Exception {
        try (var stream = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(stream, name);
            ClassNode node = new ClassNode();
            new ClassReader(stream).accept(node, 0);
            return node;
        }
    }
}
