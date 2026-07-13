package com.sodlinken.jindexer.chunker;

import com.sodlinken.jindexer.model.Chunk;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChunkerTest {

    @TempDir
    Path tempDir;

    private Chunker chunker;

    @BeforeEach
    void setUp() {
        chunker = new Chunker();
    }

    @Test
    void smallClassWholeChunk() throws IOException {
        Path file = tempDir.resolve("Small.java");
        Files.writeString(file, """
            package com.example;
            
            public class Small {
                private int x;
                
                public int getX() {
                    return x;
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Small.java", file, "com.example");
        
        assertFalse(chunks.isEmpty());
        // 应该有一个 FILE_HEADER 和一个 CLASS 块（小类整体切分）
        assertTrue(chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.FILE_HEADER));
        assertTrue(chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.CLASS));
    }

    @Test
    void largeClassHeaderOnly() throws IOException {
        // 创建一个大类（超过 1500 tokens）
        StringBuilder sb = new StringBuilder();
        sb.append("package com.example;\n\npublic class LargeClass {\n");
        for (int i = 0; i < 200; i++) {
            sb.append("    private String field").append(i).append(";\n");
        }
        sb.append("}\n");
        
        Path file = tempDir.resolve("Large.java");
        Files.writeString(file, sb.toString());

        List<Chunk> chunks = chunker.chunkFile("Large.java", file, "com.example");
        
        assertFalse(chunks.isEmpty());
        // 大类应该只提取头部
        Chunk classChunk = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.CLASS)
            .findFirst()
            .orElse(null);
        assertNotNull(classChunk);
    }

    @Test
    void methodChunking() throws IOException {
        Path file = tempDir.resolve("Methods.java");
        Files.writeString(file, """
            package com.example;
            
            public class Methods {
                public void method1() {
                    System.out.println("hello");
                }
                
                public void method2() {
                    System.out.println("world");
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Methods.java", file, "com.example");
        
        // 应该有方法块
        List<Chunk> methodChunks = chunks.stream()
            .filter(c -> c.type() == Chunk.ChunkType.METHOD)
            .toList();
        assertTrue(methodChunks.size() >= 2);
    }

    @Test
    void fileHeaderChunk() throws IOException {
        Path file = tempDir.resolve("WithImports.java");
        Files.writeString(file, """
            package com.example;
            
            import java.util.List;
            import java.util.Map;
            
            public class WithImports {
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("WithImports.java", file, "com.example");
        
        // 应该有 FILE_HEADER 块
        assertTrue(chunks.stream().anyMatch(c -> c.type() == Chunk.ChunkType.FILE_HEADER));
    }

    @Test
    void recordType() throws IOException {
        Path file = tempDir.resolve("Person.java");
        Files.writeString(file, """
            package com.example;
            
            public record Person(String name, int age) {
                public String greet() {
                    return "Hello, " + name;
                }
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Person.java", file, "com.example");
        
        assertFalse(chunks.isEmpty());
        // 应该有 Record 块（类型为 CLASS）
        assertTrue(chunks.stream().anyMatch(c -> c.name() != null && c.name().equals("Person")));
    }

    @Test
    void packageNameExtracted() throws IOException {
        Path file = tempDir.resolve("Pkg.java");
        Files.writeString(file, """
            package com.example.sub;
            
            public class Pkg {
            }
            """);

        List<Chunk> chunks = chunker.chunkFile("Pkg.java", file, "com.example.sub");
        
        assertFalse(chunks.isEmpty());
        // 所有块应该有正确的包名
        for (Chunk c : chunks) {
            assertEquals("com.example.sub", c.packageName());
        }
    }
}
