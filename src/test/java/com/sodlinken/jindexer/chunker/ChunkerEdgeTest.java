package com.sodlinken.jindexer.chunker;

import com.sodlinken.jindexer.model.Chunk;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerEdgeTest {

    private final Chunker chunker = new Chunker();

    @Test
    void chunkEmptyFile(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Empty.java");
        Files.writeString(javaFile, "");

        List<Chunk> chunks = chunker.chunkFile("Empty.java", javaFile, "com.test");
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }

    @Test
    void chunkPackageOnly(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("PackageOnly.java");
        Files.writeString(javaFile, """
            package com.test;
            """);

        List<Chunk> chunks = chunker.chunkFile("PackageOnly.java", javaFile, "com.test");
        assertNotNull(chunks);
        // Should have at least a file header chunk
    }

    @Test
    void chunkSimpleClass(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Simple.java");
        Files.writeString(javaFile, """
            package com.test;

            public class Simple {
                private String name;

                public String getName() {
                    return name;
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Simple.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());

        // Should have file header + class + method
        boolean hasClass = chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.CLASS);
        boolean hasMethod = chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.METHOD);
        assertTrue(hasClass);
        assertTrue(hasMethod);
    }

    @Test
    void chunkInterface(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("MyInterface.java");
        Files.writeString(javaFile, """
            package com.test;

            public interface MyInterface {
                void doSomething();
                String getValue();
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("MyInterface.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());

        boolean hasClass = chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.CLASS);
        assertTrue(hasClass);
    }

    @Test
    void chunkAbstractClass(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("AbstractBase.java");
        Files.writeString(javaFile, """
            package com.test;

            public abstract class AbstractBase {
                protected String field;

                public abstract void abstractMethod();

                public void concreteMethod() {
                    System.out.println("concrete");
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("AbstractBase.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void chunkClassWithMultipleMethods(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("MultiMethod.java");
        Files.writeString(javaFile, """
            package com.test;

            public class MultiMethod {
                public void method1() {}
                public void method2() {}
                public void method3() {}
                public void method4() {}
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("MultiMethod.java", javaFile, "com.test");

        long methodCount = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.METHOD)
            .count();
        assertEquals(4, methodCount);
    }

    @Test
    void chunkNestedClass(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Nested.java");
        Files.writeString(javaFile, """
            package com.test;

            public class Nested {
                public class Inner {
                    public void innerMethod() {}
                }

                public void outerMethod() {}
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Nested.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void chunkEnumClass(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("MyEnum.java");
        Files.writeString(javaFile, """
            package com.test;

            public enum MyEnum {
                VALUE1, VALUE2, VALUE3;
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("MyEnum.java", javaFile, "com.test");
        assertNotNull(chunks);
        // Enum is not a class/interface, so might not be chunked by ClassOrInterfaceDeclaration
    }

    @Test
    void chunkRecord(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Point.java");
        Files.writeString(javaFile, """
            package com.test;

            public record Point(int x, int y) {
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Point.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());

        boolean hasClass = chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.CLASS);
        assertTrue(hasClass);
    }

    @Test
    void chunkRecordWithMethods(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("PointWithMethods.java");
        Files.writeString(javaFile, """
            package com.test;

            public record PointWithMethods(int x, int y) {
                public int sum() {
                    return x + y;
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("PointWithMethods.java", javaFile, "com.test");

        long methodCount = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.METHOD)
            .count();
        assertTrue(methodCount >= 1);
    }

    @Test
    void chunkLambdaExpressions(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Lambda.java");
        Files.writeString(javaFile, """
            package com.test;

            import java.util.function.Function;

            public class Lambda {
                public void process() {
                    Function<String, Integer> fn = s -> s.length();
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Lambda.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void chunkClassWithAnnotations(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Annotated.java");
        Files.writeString(javaFile, """
            package com.test;

            @Deprecated
            @SuppressWarnings("unchecked")
            public class Annotated {
                @Override
                public String toString() {
                    return "Annotated";
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Annotated.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void chunkGenericClass(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Generic.java");
        Files.writeString(javaFile, """
            package com.test;

            public class Generic<T, U> {
                private T value;
                private U other;

                public Generic(T value, U other) {
                    this.value = value;
                    this.other = other;
                }

                public T getValue() { return value; }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Generic.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void chunkImplementsInterface(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Impl.java");
        Files.writeString(javaFile, """
            package com.test;

            public class Impl implements Runnable {
                @Override
                public void run() {
                    System.out.println("running");
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Impl.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());

        Chunk classChunk = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.CLASS)
            .findFirst().orElse(null);
        assertNotNull(classChunk);
        assertTrue(classChunk.signature().contains("Runnable"));
    }

    @Test
    void chunkExtendsClass(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Child.java");
        Files.writeString(javaFile, """
            package com.test;

            public class Child extends Parent {
                @Override
                public void greet() {
                    System.out.println("hello");
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Child.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());

        Chunk classChunk = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.CLASS)
            .findFirst().orElse(null);
        assertNotNull(classChunk);
        assertTrue(classChunk.signature().contains("Parent"));
    }

    @Test
    void chunkClassWithConstructors(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("WithConstructors.java");
        Files.writeString(javaFile, """
            package com.test;

            public class WithConstructors {
                private String name;
                private int age;

                public WithConstructors() {
                    this.name = "";
                    this.age = 0;
                }

                public WithConstructors(String name, int age) {
                    this.name = name;
                    this.age = age;
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("WithConstructors.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());
    }

    @Test
    void chunkFileWithImports(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("WithImports.java");
        Files.writeString(javaFile, """
            package com.test;

            import java.util.List;
            import java.util.Map;
            import java.util.HashMap;

            public class WithImports {
                private Map<String, List<String>> data = new HashMap<>();
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("WithImports.java", javaFile, "com.test");
        assertFalse(chunks.isEmpty());

        boolean hasHeader = chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.FILE_HEADER);
        assertTrue(hasHeader);
    }

    @Test
    void chunkStaticMethods(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("StaticMethods.java");
        Files.writeString(javaFile, """
            package com.test;

            public class StaticMethods {
                public static void staticMethod() {}
                private static int staticHelper() { return 0; }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("StaticMethods.java", javaFile, "com.test");

        long methodCount = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.METHOD)
            .count();
        assertEquals(2, methodCount);
    }

    @Test
    void chunkGeneratesPackageName(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("Test.java");
        Files.writeString(javaFile, """
            package com.example.project;

            public class Test {
                public void doStuff() {}
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Test.java", javaFile, "com.example.project");

        Chunk classChunk = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.CLASS)
            .findFirst().orElse(null);
        assertNotNull(classChunk);
        assertEquals("com.example.project", classChunk.packageName());
    }

    @Test
    void chunkGeneratesClassName(@TempDir Path tempDir) throws Exception {
        Path javaFile = tempDir.resolve("MyClass.java");
        Files.writeString(javaFile, """
            package com.test;

            public class MyClass {
                public void method() {}
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("MyClass.java", javaFile, "com.test");

        Chunk classChunk = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.CLASS)
            .findFirst().orElse(null);
        assertNotNull(classChunk);
        assertEquals("MyClass", classChunk.className());
    }

    @Test
    void chunkNonExistentFile() {
        List<Chunk> chunks = chunker.chunkFile("NonExistent.java", Path.of("nonexistent"), "com.test");
        assertNotNull(chunks);
        assertTrue(chunks.isEmpty());
    }
}
