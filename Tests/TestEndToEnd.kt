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

class TestEndToEnd {
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun compile(name: String, source: String): Path {
        val directory: Path = createSecureTempDirectory("zendtoend")
        val sourceFile: Path = directory.resolve(name + ".z")
        Files.writeString(sourceFile, source)

        val builder: java.lang.ProcessBuilder = java.lang.ProcessBuilder(
            "java",
            "-cp", java.lang.System.getProperty("java.class.path"),
            "src.Main.Main",
            sourceFile.toString(),
            "-o", directory.resolve(name).toString()
        )
            .redirectErrorStream(true)
        securePath(builder)
        val process: java.lang.Process = builder.start()


        val output = String(process.getInputStream().readAllBytes())
        val status: Int = process.waitFor()
        Assertions.assertEquals(0, status, output)
        // Regression test for GitHub issue #167:
        // compilation must not emit Clang's target-triple override warning.
        Assertions.assertFalse(
            output.contains("overriding the module target triple"),
            "Clang emitted a target-triple warning:\n" + output
        )
        return directory.resolve(name)
    }

    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun run(binary: Path): Int {
        val process: java.lang.Process = java.lang.ProcessBuilder(binary.toString()).redirectErrorStream(true).start()
        //final String output = new String(process.getInputStream().readAllBytes());
        return process.waitFor()
    }

    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun runAndCaptureOutput(binary: Path): String {
        val process: java.lang.Process = java.lang.ProcessBuilder(binary.toString()).redirectErrorStream(true).start()
        val output = String(process.getInputStream().readAllBytes())
        process.waitFor()
        return output
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testArithmeticProgramRuns() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile("arith", "proc main() { ret 40 + 2 }")

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testCallToALaterFunctionRuns() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "forward", """
                proc main() {
                    ret twice(21)
                }

                proc twice(int32 x) {
                    ret x * 2
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testMutuallyRecursiveFunctionsRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "mutual", """
                proc main() {
                    ret add(20)
                }

                proc add(int32 n) {
                    ret bump(n)
                }

                proc bump(int32 n) {
                    ret n + 1
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(21, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testLoopRuns() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "loop", """
                proc main() {
                    for (int32 i -> 0 | i < 5 | i + 1) {
                        int32 x -> i * 2
                    }
                    ret 7
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(7, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testReturningFromInsideALoopRuns() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "earlyexit", """
                proc main() {
                    for (int32 i -> 0 | i < 100 | i + 1) {
                        ret 5
                    }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(5, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testBreakAndContinueRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "breakcontinue", """
                proc main() {
                    int32 sum -> 0
                    for (int32 i -> 0 | i < 100 | i + 1) {
                        if (i == 7) {
                            break
                        }
                        if (i % 2 == 1) {
                            continue
                        }
                        sum -> sum + i
                    }
                    ret sum
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(12, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testModuloAndEqualityRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "modulo", """
                proc main() {
                    int32 x -> 17 % 5
                    if (x == 2) {
                        ret 42
                    }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testUnsignedConditionRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "unsigned", """
                proc main() {
                    uint32 a -> 4294967295
                    if (a > 0) {
                        ret 42
                    }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testBooleanReturnRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "boolean", """
                proc yes() -> bool {
                    ret 1
                }

                proc main() {
                    if (yes()) {
                        ret 42
                    }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testBooleanLiteralsRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "boolean-literals", """
                proc main() {
                    bool yes -> true
                    bool no -> false
                    if (yes) {
                        if (no == false) {
                            ret 42
                        }
                    }
                    ret 0
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testVariablesAndConstantsRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "vars", """
                proc main() {
                    int32 a -> 20
                    cn int32 b -> 22
                    ret a + b
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBinaryIsNotProducedForAFailedCompilation() {
        if (OPT == null || CLANG == null) return

        val directory: Path = createSecureTempDirectory("zendtoend")
        val sourceFile: Path = directory.resolve("broken.z")
        Files.writeString(sourceFile, "proc main() { ret nope(1) }")
        val binary: Path = directory.resolve("broken")

        val output = processBuilder(sourceFile, binary)
        Assertions.assertTrue(output.contains("unknown function"), output)
        Assertions.assertFalse(Files.exists(binary), "a binary was produced from IR that never compiled")
    }

    @Throws(IOException::class)
    private fun processBuilder(sourceFile: Path, binary: Path): String {
        val builder: java.lang.ProcessBuilder = java.lang.ProcessBuilder(
            "java",
            "-cp", java.lang.System.getProperty("java.class.path"),
            "src.Main.Main",
            sourceFile.toString(),
            "-o", binary.toString()
        )
            .redirectErrorStream(true)
        securePath(builder)
        val process: java.lang.Process = builder.start()


        return String(process.getInputStream().readAllBytes())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTwoFunctionsWithTheSameNameAreRejected() {
        if (OPT == null || CLANG == null) return

        val directory: Path = createSecureTempDirectory("zendtoend")
        val sourceFile: Path = directory.resolve("dup.z")
        Files.writeString(sourceFile, "proc a() { ret 1 }\nproc a() { ret 2 }\nproc main() { ret a() }")
        val binary: Path = directory.resolve("dup")

        val output = processBuilder(sourceFile, binary)
        Assertions.assertTrue(output.contains("defined more than once"), output)
        Assertions.assertFalse(Files.exists(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAnEmptyFileIsRejected() {
        if (OPT == null || CLANG == null) return

        val directory: Path = createSecureTempDirectory("zendtoend")
        val sourceFile: Path = directory.resolve("empty.z")
        Files.writeString(sourceFile, "")
        val binary: Path = directory.resolve("empty")

        processBuilder(sourceFile, binary)
        Assertions.assertFalse(Files.exists(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testATruncatedSecondFunctionIsRejected() {
        if (OPT == null || CLANG == null) return

        val directory: Path = createSecureTempDirectory("zendtoend")
        val sourceFile: Path = directory.resolve("truncated.z")
        Files.writeString(sourceFile, "proc main() { ret 1 }\nproc broken() { ret 2")
        val binary: Path = directory.resolve("truncated")

        val output = processBuilder(sourceFile, binary)
        Assertions.assertTrue(output.contains("Expected '}'"), output)
        Assertions.assertFalse(Files.exists(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testMissingSourceFileIsReported() {
        val builder: java.lang.ProcessBuilder = java.lang.ProcessBuilder(
            "java",
            "-cp", java.lang.System.getProperty("java.class.path"),
            "src.Main.Main",
            "/nonexistent/path.z"
        )
            .redirectErrorStream(true)
        securePath(builder)
        val process: java.lang.Process = builder.start()

        Assertions.assertNotEquals(0, process.waitFor())
    }

    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun compileFiles(root: String, vararg files: String): Path {
        val directory: Path = createSecureTempDirectory("zendtoend")
        for (file in files) {
            val split: Int = file.indexOf('\n'.code)
            Files.writeString(directory.resolve(file.substring(0, split)), file.substring(split + 1))
        }

        val builder: java.lang.ProcessBuilder = java.lang.ProcessBuilder(
            "java",
            "-cp", java.lang.System.getProperty("java.class.path"),
            "src.Main.Main",
            directory.resolve(root + ".z").toString(),
            "-o", directory.resolve(root).toString()
        )
            .redirectErrorStream(true)
        securePath(builder)
        val process: java.lang.Process = builder.start()

        val output = String(process.getInputStream().readAllBytes())
        Assertions.assertEquals(0, process.waitFor(), output)
        return directory.resolve(root)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testImportedModuleFunctionRuns() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compileFiles(
            "main",
            "main.z\nimport math\n\nproc main() {\n    ret math.twice(21)\n}",
            "math.z\nproc twice(int32 x) {\n    ret x * 2\n}"
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testTransitiveImportsRun() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compileFiles(
            "main",
            "main.z\nimport a\n\nproc main() {\n    ret a.fa(20)\n}",
            "a.z\nimport b\n\nproc fa(int32 x) {\n    ret b.fb(x)\n}",
            "b.z\nproc fb(int32 x) {\n    ret x + 22\n}"
        )

        Assertions.assertEquals(42, run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallingAFunctionTheModuleDoesNotHaveIsRejected() {
        if (OPT == null || CLANG == null) return

        val directory: Path = createSecureTempDirectory("zendtoend")
        Files.writeString(directory.resolve("math.z"), "proc twice(int32 x) { ret x * 2 }")
        val sourceFile: Path = directory.resolve("main.z")
        Files.writeString(sourceFile, "import math\nproc main() { ret math.nope(1) }")
        val binary: Path = directory.resolve("main")

        val output = processBuilder(sourceFile, binary)
        Assertions.assertTrue(output.contains("unknown function: math.nope"), output)
        Assertions.assertFalse(Files.exists(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testProgramOutputIsCaptured() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    printf("Hello, World!")
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("Hello, World!", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testStdPrintln() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                import std

                proc main() {
                    println(42)
                    std.println(7)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("42\n7", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithFlt32() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt32 test -> 3.2
                    printf("%.1f" | test)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("3.2", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithFlt64() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt64 test -> 3.3
                    printf("%.1f" | test)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("3.3", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithInt64() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    int64 test -> 3123123123
                    printf("%lld" | test)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("3123123123", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithMultipleInt() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    int64 test -> 3123123123
                    int32 test2 -> 322123
                    printf("%lld | %d" | test | test2)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("3123123123 | 322123", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithMultipleFloats() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt64 test -> 312.78
                    flt32 test2 -> 15.68
                    printf("%.2f | %.2f" | test | test2)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("312.78 | 15.68", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithMultipleTypes() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    flt64 test -> 312.78
                    int32 test2 -> 15
                    printf("%.2f | %d" | test | test2)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("312.78 | 15", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithFalseBoolean() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    bool test -> false
                    printf("%d" | test)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("0", output.trim())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithTrueBoolean() {
        if (OPT == null || CLANG == null) return

        val binary: Path = compile(
            "output", """
                extern printf(str | ...) -> int32

                proc main() {
                    bool test -> true
                    printf("%d" | test)
                    ret 0
                }
                
                """.trimIndent()
        )

        val output = runAndCaptureOutput(binary)
        Assertions.assertEquals("1", output.trim())
    }

    companion object {
        val OPT: String? = findTool("opt")
        val CLANG: String? = findTool("clang")

        fun findTool(name: String): String? {
            val path = securePath
            if (path == null) return null
            for (directory in path.split(":")) {
                val candidate: Path = Path.of(directory, name)
                if (Files.isExecutable(candidate)) return candidate.toString()
            }
            return null
        }

        private val securePath: String?
            get() {
                val path: String? = java.lang.System.getenv("PATH")
                if (path == null) return null

                val securePath: java.lang.StringBuilder = java.lang.StringBuilder()
                for (directory in path.split(":")) {
                    if (isSecureDirectory(directory)) {
                        if (!securePath.isEmpty()) {
                            securePath.append(":")
                        }
                        securePath.append(directory)
                    }
                }
                return if (!securePath.isEmpty()) securePath.toString() else null
            }

        private fun isSecureDirectory(directory: String): Boolean {
            try {
                val dir: Path = Path.of(directory)
                if (!Files.exists(dir)) return false
                return !Files.isWritable(dir)
            } catch (e: java.lang.Exception) {
                return false
            }
        }

        @Throws(IOException::class)
        private fun createSecureTempDirectory(prefix: String): Path {
            val permissions: MutableSet<PosixFilePermission?> = PosixFilePermissions.fromString("rwx------")
            return Files.createTempDirectory(prefix, PosixFilePermissions.asFileAttribute(permissions))
        }

        private fun securePath(builder: java.lang.ProcessBuilder) {
            val path = securePath
            if (path != null) builder.environment().put("PATH", path)
        }
    }
}