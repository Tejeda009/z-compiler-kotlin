package Tests

import src.AST.ExprAST
import src.Codegen.IRBuilder
import src.Parser.ZType
import src.AST.LLVMTextUnit
import src.AST.Conditions
import src.Parser.TypeRef
import src.AST.ForAST
import src.Codegen.SymbolTable.SymbolInfo
import java.util.Objects
import src.AST.NumberExprAST
import src.AST.BlockAST
import src.AST.DeclaratorExprAST
import src.AST.ImportAST
import src.AST.ExternExprAST
import src.AST.FunctionAST
import java.util.Collections
import src.AST.BinaryExprAST
import src.Codegen.IRBuilder.ResolvedSignature
import src.AST.PrototypeAST
import src.AST.CallExprAST
import src.Parser.StructType
import src.Parser.StructRegistry
import src.AST.AddressOfExprAST
import src.AST.VarRefExprAST
import src.AST.FreeExprAST
import kotlin.streams.asStream
import src.AST.LoopJumpAST
import src.AST.AllocExprAST
import src.AST.DerefExprAST
import src.AST.AssignExprAST
import src.Codegen.IRBuilder.ExternDeclaration
import src.AST.StructLiteralAST
import src.AST.FieldAccessExprAST
import src.AST.AssignThroughPointerExprAST
import java.util.concurrent.Callable
import java.io.File
import java.nio.file.Files
import src.Main.CompilerCmd
import java.io.ByteArrayInputStream
import java.util.Locale
import java.io.IOException
import java.nio.file.Path
import src.AST.ModuleAST
import src.Main.ModuleLoader
import java.util.LinkedHashMap
import java.util.HashSet
import src.AST.StructDeclAST
import java.util.HashMap
import src.Parser.ParserContext
import src.Parser.TypeParser
import src.Parser.BranchesParser
import src.Parser.LoopParser
import src.Parser.RetParser
import src.Parser.StructParser
import src.Parser.VariableParser
import src.Parser.DefinitionParser
import src.AST.IfAST
import src.AST.RetAST
import src.AST.ArrayAST
import src.AST.ParenExprAST
import src.AST.ArrayIdxExprAST
import src.AST.StringExprAST
import src.AST.BoolExprAST
import src.AST.UnaryExprAST
import java.util.Deque
import src.Codegen.TypeLayout
import org.junit.jupiter.api.Assertions
import Tests.ZFixtures
import Tests.ZFixtures.ParsedUnit
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import org.junit.jupiter.api.io.TempDir
import Tests.TestEndToEnd
import Tests.TestStdLib
import Tests.TestBinding
import Tests.TestCodeGen
import org.junit.jupiter.api.Disabled
import Tests.TestImports
import org.junit.jupiter.api.AfterEach
import Tests.TestAstNodes.VariableNode
import Tests.TestAstNodes
import java.nio.file.attribute.PosixFilePermission
import java.nio.file.attribute.PosixFilePermissions
import org.junit.jupiter.api.TestFactory
import org.junit.jupiter.api.DynamicTest
import Tests.TestExamples
import java.util.Optional
import java.util.stream.Collectors
import Tests.TestIRBuilder
import Tests.TestMoreLexer
import Tests.TestAstCodeGen
import Tests.TestAstCodeGen.ImpureConstant
import Tests.TestMoreParser
import src.Main.CompilerCmdTest
import Tests.TestAstCoverage
import src.AST.AstInternalsTest.NullCoerce
import Tests.TestModuleLoader
import src.Parser.ParserInternalsTest
import Tests.TestGeneratedIrIsAccepted

class TestStdLib {
    @TempDir
    var directory: Path? = null

    private val end: TestEndToEnd = TestEndToEnd()

    @Throws(IOException::class)
    private fun compile(source: String): String? {
        val file: Path = directory.resolve("main.z")
        Files.writeString(file, source, java.nio.charset.StandardCharsets.UTF_8)
        val lexer: src.Lexer.Lexer = src.Lexer.Lexer(Files.newInputStream(file))
        return generateModule(src.Parser.Parser(lexer), file)
    }

    @Throws(IOException::class)
    private fun compileFailure(source: String): String? {
        val captured: ByteArrayOutputStream = ByteArrayOutputStream()
        val diagnostics: PrintStream? = java.lang.System.err
        java.lang.System.setErr(PrintStream(captured, true, java.nio.charset.StandardCharsets.UTF_8))
        try {
            return if (compile(source) == null) captured.toString(java.nio.charset.StandardCharsets.UTF_8) else null
        } finally {
            java.lang.System.setErr(diagnostics)
        }
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun everyWrapperCanBeCalledWithThePrefix() {
        val ir = compile(ALL_WRAPPERS)

        Assertions.assertNotNull(ir)
        for (name in arrayOf<String>(
            "abs", "labs", "llabs", "fabs", "atof", "atoi", "atol", "atoll",
            "exit", "abort", "system", "rand", "srand",
            "isalnum", "isalpha", "isdigit", "islower", "ispunct", "isspace",
            "isupper", "isxdigit", "tolower", "toupper",
            "strlen", "strcmp", "strncmp", "getchar", "putchar", "puts",
            "acos", "acosh", "asin", "asinh", "atan", "atan2", "atanh", "cbrt",
            "ceil", "cos", "cosh", "exp", "exp2", "floor", "fmax", "fmin", "fmod",
            "hypot", "log", "log10", "log2", "pow", "round", "sin", "sinh", "sqrt",
            "tan", "tanh", "trunc", "println"
        )) {
            Assertions.assertEquals(
                2,
                Companion.count(ir!!, "@std_" + name + "("),
                name + " should be defined once and called once:\n" + ir
            )
        }
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun wrappersCallTheirCFunction() {
        val ir = compile(ALL_WRAPPERS)

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call i32 @abs(i32"), ir)
        Assertions.assertTrue(ir.contains("call i64 @llabs(i64"), ir)
        Assertions.assertTrue(ir.contains("call double @fabs(double"), ir)
        Assertions.assertTrue(ir.contains("call i64 @strlen(i8*"), ir)
        Assertions.assertTrue(ir.contains("call void @exit(i32"), ir)
        Assertions.assertTrue(ir.contains("call double @sqrt(double"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun stdExternsWorkWithThePrefix() {
        val ir = compile(ALL_WRAPPERS)

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call i32 (i8*, ...) @printf(i8*"), ir)
        Assertions.assertTrue(ir.contains("call i32 (i8*, ...) @scanf(i8*"), ir)
        Assertions.assertEquals(1, Companion.count(ir!!, "declare i32 @printf("), ir)
        Assertions.assertEquals(1, Companion.count(ir, "declare i32 @scanf("), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun mathLibraryIsLinkedAutomatically() {
        val command: java.lang.Class<*> = java.lang.Class.forName("src.Main.CompilerCmd")
        val builder: java.lang.reflect.Method = command.getDeclaredMethod(
            "createProcessBuilder",
            String::class.java,
            String::class.java,
            MutableList::class.java,
            MutableList::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java
        )
        builder.setAccessible(true)
        val process: java.lang.ProcessBuilder = builder.invoke(
            null,
            "in.ll",
            "out",
            java.util.List.of<Any?>(),
            java.util.List.of<Any?>(),
            false,
            null
        ) as java.lang.ProcessBuilder

        val windows: Boolean = java.lang.System.getProperty("os.name").toLowerCase().contains("win")
        Assertions.assertEquals(if (windows) "out" else "-lm", process.command().getLast())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun rootExternsKeepTheirCReturnType() {
        val ir = compile(
            """
                extern printf(str | ...) -> int32
                extern puts(str) -> int32

                proc main() {
                    printf("%d" | 1)
                    puts("text")
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertNotNull(ir)
        Assertions.assertTrue(ir.contains("call i32 (i8*, ...) @printf(i8*"), ir)
        Assertions.assertTrue(ir.contains("call i32 @puts(i8*"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun stringConcatenationUsesTheLengthInTheStruct() {
        val ir = compile(
            """
                import std

                proc main() {
                    str left -> "foo"
                    str right -> "bar"
                    str joined -> left + right
                    puts(joined)
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertNotNull(ir)
        // The fat string carries its length, so concatenation does not
        // call strlen. The stdlib's std_strlen wrapper does, but that is
        // not the concatenation path.
        val mainStart: Int = ir.indexOf("define dso_local i32 @main()")
        val mainBody: String = ir.substring(mainStart)
        Assertions.assertFalse(mainBody.contains("call i64 @strlen"), ir)
        Assertions.assertTrue(ir.contains("call void @llvm.memcpy.p0.p0.i64(ptr"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun missingStdFunctionIsRejected() {
        val diagnostics = compileFailure("import std\nproc main() { ret std.nope(1) }")
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("unknown function: std.nope"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun numericFunctionsRun() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-numeric", """
                import std

                proc main() {
                    if (std.abs(-7) != 7) { ret 1 }
                    if (std.labs(-2000000000) != 2000000000) { ret 2 }
                    if (std.llabs(-2000000000) != 2000000000) { ret 3 }
                    if (std.fabs(-2.5) != 2.5) { ret 4 }
                    if (std.atoi("42") != 42) { ret 5 }
                    if (std.atol("123456") != 123456) { ret 6 }
                    if (std.atoll("1234567890123") != 1234567890123) { ret 7 }
                    if (std.atof("2.5") != 2.5) { ret 8 }
                    if (std.sqrt(9.0) != 3.0) { ret 9 }
                    if (std.fabs(std.cbrt(27.0) - 3.0) > 0.000001) { ret 10 }
                    if (std.pow(2.0 | 3.0) != 8.0) { ret 11 }
                    if (std.ceil(2.1) != 3.0) { ret 12 }
                    if (std.floor(2.9) != 2.0) { ret 13 }
                    if (std.round(2.6) != 3.0) { ret 14 }
                    if (std.trunc(2.9) != 2.0) { ret 15 }
                    if (std.fmod(7.0 | 3.0) != 1.0) { ret 16 }
                    if (std.fmin(2.0 | 3.0) != 2.0) { ret 17 }
                    if (std.fmax(2.0 | 3.0) != 3.0) { ret 18 }
                    if (std.hypot(3.0 | 4.0) != 5.0) { ret 19 }
                    if (std.log(1.0) != 0.0) { ret 20 }
                    if (std.log2(8.0) != 3.0) { ret 21 }
                    if (std.log10(100.0) != 2.0) { ret 22 }
                    if (std.exp(0.0) != 1.0) { ret 23 }
                    if (std.exp2(3.0) != 8.0) { ret 24 }
                    if (std.sin(0.0) != 0.0) { ret 25 }
                    if (std.cos(0.0) != 1.0) { ret 26 }
                    if (std.tan(0.0) != 0.0) { ret 27 }
                    if (std.asin(0.0) != 0.0) { ret 28 }
                    if (std.acos(1.0) != 0.0) { ret 29 }
                    if (std.atan(0.0) != 0.0) { ret 30 }
                    if (std.atan2(0.0 | 1.0) != 0.0) { ret 31 }
                    if (std.sinh(0.0) != 0.0) { ret 32 }
                    if (std.cosh(0.0) != 1.0) { ret 33 }
                    if (std.tanh(0.0) != 0.0) { ret 34 }
                    if (std.asinh(0.0) != 0.0) { ret 35 }
                    if (std.acosh(1.0) != 0.0) { ret 36 }
                    if (std.atanh(0.0) != 0.0) { ret 37 }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun characterFunctionsRun() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-ctype", """
                import std

                proc main() {
                    if (std.isalnum(48) == 0) { ret 1 }
                    if (std.isalpha(65) == 0) { ret 2 }
                    if (std.isdigit(48) == 0) { ret 3 }
                    if (std.islower(97) == 0) { ret 4 }
                    if (std.ispunct(33) == 0) { ret 5 }
                    if (std.isspace(32) == 0) { ret 6 }
                    if (std.isupper(65) == 0) { ret 7 }
                    if (std.isxdigit(70) == 0) { ret 8 }
                    if (std.tolower(65) != 97) { ret 9 }
                    if (std.toupper(97) != 65) { ret 10 }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun stringFunctionsRun() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-string", """
                import std

                proc main() {
                    if (std.strlen("hello") != 5) { ret 1 }
                    if (std.strcmp("abc" | "abc") != 0) { ret 2 }
                    if (std.strcmp("abc" | "abd") >= 0) { ret 3 }
                    if (std.strncmp("abcx" | "abcy" | 3) != 0) { ret 4 }
                    if (std.strncmp("abcx" | "abcy" | 4) >= 0) { ret 5 }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun inputFunctionsRun() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-input", """
                import std

                proc main() {
                    int32 number -> 0
                    if (std.scanf("%d" | &number) != 1) { ret 1 }
                    if (number != 42) { ret 2 }
                    if (std.getchar() != 10) { ret 3 }
                    ret 0
                }
                
                """.trimIndent()
        )

        val process: java.lang.Process = java.lang.ProcessBuilder(binary.toString()).redirectErrorStream(true).start()
        process.getOutputStream().use { input ->
            input.write("42\n".getBytes(java.nio.charset.StandardCharsets.UTF_8))
        }
        val output = String(process.getInputStream().readAllBytes(), java.nio.charset.StandardCharsets.UTF_8)

        Assertions.assertEquals(0, process.waitFor(), output)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun randomFunctionsRun() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-random", """
                import std

                proc main() {
                    std.srand(1)
                    int32 first -> std.rand()
                    int32 second -> std.rand()
                    if (first < 0) { ret 1 }
                    if (second < 0) { ret 2 }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun outputFunctionsRun() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-output", """
                import std

                proc main() {
                    puts("C")
                    putchar(68)
                    println(4)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output: String = end.runAndCaptureOutput(binary).replace("\r\n", "\n").trim()
        Assertions.assertEquals("C\nD4", output)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun exitStopsTheProgram() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-exit", """
                import std

                proc main() {
                    std.exit(7)
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(7, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun abortStopsTheProgram() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-abort", """
                import std

                proc main() {
                    std.abort()
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertNotEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun systemRunsACommand() {
        if (toolsMissing()) return

        val binary: Path = end.compile(
            "stdlib-system", """
                import std

                proc main() {
                    if (std.system("exit 0") != 0) { ret 1 }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(0, end.run(binary))
    }

    companion object {
        private fun toolsMissing(): Boolean {
            return TestEndToEnd.Companion.OPT == null || TestEndToEnd.Companion.CLANG == null
        }

        private fun count(ir: String, fragment: String): Int {
            var occurrences = 0
            var at: Int = ir.indexOf(fragment)
            while (at >= 0) {
                occurrences++
                at = ir.indexOf(fragment, at + 1)
            }
            return occurrences
        }

        private val ALL_WRAPPERS: String = """
            import std

            proc main() {
                int32 number -> 0
                str text -> "text"
                std.printf("%d" | 1)
                std.scanf("%d" | &number)
                std.abs(-1)
                std.labs(-1)
                std.llabs(-1)
                std.fabs(-1.0)
                std.atof("1.0")
                std.atoi("1")
                std.atol("1")
                std.atoll("1")
                std.exit(0)
                std.abort()
                std.system("true")
                std.rand()
                std.srand(1)
                std.isalnum(48)
                std.isalpha(65)
                std.isdigit(48)
                std.islower(97)
                std.ispunct(33)
                std.isspace(32)
                std.isupper(65)
                std.isxdigit(70)
                std.tolower(65)
                std.toupper(97)
                std.strlen(text)
                std.strcmp(text | text)
                std.strncmp(text | text | 1)
                std.getchar()
                std.putchar(65)
                std.puts(text)
                std.acos(1.0)
                std.acosh(1.0)
                std.asin(0.0)
                std.asinh(0.0)
                std.atan(0.0)
                std.atan2(0.0 | 1.0)
                std.atanh(0.0)
                std.cbrt(1.0)
                std.ceil(1.0)
                std.cos(0.0)
                std.cosh(0.0)
                std.exp(0.0)
                std.exp2(0.0)
                std.floor(1.0)
                std.fmax(1.0 | 2.0)
                std.fmin(1.0 | 2.0)
                std.fmod(1.0 | 2.0)
                std.hypot(3.0 | 4.0)
                std.log(1.0)
                std.log10(1.0)
                std.log2(1.0)
                std.pow(2.0 | 3.0)
                std.round(1.0)
                std.sin(0.0)
                std.sinh(0.0)
                std.sqrt(1.0)
                std.tan(0.0)
                std.tanh(0.0)
                std.trunc(1.0)
                std.println(1)
                ret 0
            }
            
            """.trimIndent()
    }
}
