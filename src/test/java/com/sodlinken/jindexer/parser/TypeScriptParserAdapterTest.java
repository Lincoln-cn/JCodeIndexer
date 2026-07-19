package com.sodlinken.jindexer.parser;

import com.sodlinken.jindexer.config.Config;
import com.sodlinken.jindexer.model.Symbol;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TypeScript 解析器测试
 */
class TypeScriptParserAdapterTest {

    @TempDir
    Path tempDir;

    private Config config;
    private TypeScriptParserAdapter parser;

    @BeforeEach
    void setUp() {
        config = new Config();
        config.setProjectRoot(tempDir);
        parser = new TypeScriptParserAdapter(config);
    }

    @Test
    void isTypeScriptFile() {
        assertTrue(TypeScriptParserAdapter.isTypeScriptFile("test.ts"));
        assertTrue(TypeScriptParserAdapter.isTypeScriptFile("test.tsx"));
        assertTrue(TypeScriptParserAdapter.isTypeScriptFile("test.mts"));
        assertTrue(TypeScriptParserAdapter.isTypeScriptFile("test.cts"));
        assertFalse(TypeScriptParserAdapter.isTypeScriptFile("test.js"));
        assertFalse(TypeScriptParserAdapter.isTypeScriptFile("test.java"));
    }

    @Test
    void parseClassDeclaration() throws Exception {
        Path file = tempDir.resolve("UserService.ts");
        Files.writeString(file, """
            export class UserService {
                private name: string;

                constructor(name: string) {
                    this.name = name;
                }

                public getName(): string {
                    return this.name;
                }
            }
            """);

        var result = parser.parse("UserService.ts", file);

        assertFalse(result.symbols().isEmpty());
        var classSymbol = result.symbols().stream()
            .filter(s -> s.name().equals("UserService"))
            .findFirst();
        assertTrue(classSymbol.isPresent());
        assertEquals(Symbol.SymbolKind.CLASS, classSymbol.get().kind());
    }

    @Test
    void parseInterfaceDeclaration() throws Exception {
        Path file = tempDir.resolve("IUserService.ts");
        Files.writeString(file, """
            export interface IUserService {
                getName(): string;
                setName(name: string): void;
            }
            """);

        var result = parser.parse("IUserService.ts", file);

        assertFalse(result.symbols().isEmpty());
        var interfaceSymbol = result.symbols().stream()
            .filter(s -> s.name().equals("IUserService"))
            .findFirst();
        assertTrue(interfaceSymbol.isPresent());
        assertEquals(Symbol.SymbolKind.CLASS, interfaceSymbol.get().kind());
    }

    @Test
    void parseFunctionDeclaration() throws Exception {
        Path file = tempDir.resolve("utils.ts");
        Files.writeString(file, """
            export function formatDate(date: Date): string {
                return date.toISOString();
            }

            export function add(a: number, b: number): number {
                return a + b;
            }
            """);

        var result = parser.parse("utils.ts", file);

        assertFalse(result.symbols().isEmpty());
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("formatDate")));
        assertTrue(result.symbols().stream().anyMatch(s -> s.name().equals("add")));
    }

    @Test
    void parseImportStatements() throws Exception {
        Path file = tempDir.resolve("App.ts");
        Files.writeString(file, """
            import { UserService } from './UserService';
            import React from 'react';

            export class App {
                private userService: UserService;
            }
            """);

        var result = parser.parse("App.ts", file);

        assertFalse(result.references().isEmpty());
        assertTrue(result.references().stream().anyMatch(r -> r.context().contains("UserService")));
    }

    @Test
    void parseEnumDeclaration() throws Exception {
        Path file = tempDir.resolve("Status.ts");
        Files.writeString(file, """
            export enum Status {
                ACTIVE = 'ACTIVE',
                INACTIVE = 'INACTIVE',
                PENDING = 'PENDING'
            }
            """);

        var result = parser.parse("Status.ts", file);

        assertFalse(result.symbols().isEmpty());
        var enumSymbol = result.symbols().stream()
            .filter(s -> s.name().equals("Status"))
            .findFirst();
        assertTrue(enumSymbol.isPresent());
        assertEquals(Symbol.SymbolKind.CLASS, enumSymbol.get().kind());
    }
}
