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

class TestGeneratedIrIsAccepted {
    @Throws(java.lang.InterruptedException::class, IOException::class)
    private fun assertLlvmAccepts(source: String) {
        val module: String? = ZFixtures.module(source)
        Assertions.assertNotNull(module, "the module did not generate")

        if (LLVM_AS == null) return

        val sourceFile: Path = java.nio.file.Files.createTempFile("zcheck", ".ll")
        val `object`: Path = java.nio.file.Files.createTempFile("zcheck", ".bc")
        try {
            java.nio.file.Files.writeString(sourceFile, module)
            val process: java.lang.Process = java.lang.ProcessBuilder(
                LLVM_AS, sourceFile.toString(), "-o", `object`.toString()
            )
                .redirectErrorStream(true)
                .start()
            val diagnostics = String(process.getInputStream().readAllBytes())
            Assertions.assertEquals(0, process.waitFor(), "llvm-as rejected the module:\n" + diagnostics + module)
        } finally {
            java.nio.file.Files.deleteIfExists(sourceFile)
            java.nio.file.Files.deleteIfExists(`object`)
        }
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun arithmeticIsAccepted() {
        assertLlvmAccepts("proc main() { ret 1 + 2 * 3 - 4 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun floatingArithmeticIsAccepted() {
        assertLlvmAccepts("proc main() { ret 1.5 + 2.5 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun unaryMinusOnFloatIsAccepted() {
        assertLlvmAccepts("proc main() { ret -5.5 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun comparisonIsAccepted() {
        assertLlvmAccepts("proc main() { ret 3 < 4 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun comparisonBetweenFloatsIsAccepted() {
        assertLlvmAccepts("proc main() { ret 1.5 < 2.5 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun comparisonInAForHeaderIsAccepted() {
        assertLlvmAccepts("proc main() {\n for (flt64 i -> 0.0 | i < 2.0 | i + 0.5) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun forHeaderComparingAnIntegerAgainstAFloatIsAccepted() {
        assertLlvmAccepts("proc main() {\n for (int32 i -> 0 | i < 2.5 | i + 1) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun int64ArithmeticIsAccepted() {
        assertLlvmAccepts("proc main() { int64 a -> 2\n int64 b -> 3\n ret a + b }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun mixedWidthsAreAccepted() {
        assertLlvmAccepts("proc main() { ret 1 + 2.5 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun floatVariableIsAccepted() {
        assertLlvmAccepts("proc main() { flt32 a -> 1.5\n ret a }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun argumentConversionIsAccepted() {
        assertLlvmAccepts("proc half(flt64 x) { ret x / 2 }\nproc main() { ret half(21) }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun constantDeclarationIsAccepted() {
        assertLlvmAccepts("proc main() { cn int32 k -> 2\n ret k }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun mutuallyRecursiveCallsAreAccepted() {
        assertLlvmAccepts("proc a(int32 n) { ret b(n) }\nproc b(int32 n) { ret a(n) }\nproc main() { ret a(1) }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun variablesAreAccepted() {
        assertLlvmAccepts("proc main() { int32 a -> 1\n flt64 b -> 2.5\n ret a }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun constantsAreAccepted() {
        assertLlvmAccepts("proc main() { cn int32 a -> 1\n cn flt64 b -> 2.5\n ret a }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun forwardCallIsAccepted() {
        assertLlvmAccepts("proc main() { ret twice(21) }\nproc twice(int32 x) { ret x * 2 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun recursiveCallIsAccepted() {
        assertLlvmAccepts("proc fact(int32 n) { ret n * fact(n - 1) }\nproc main() { ret fact(5) }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun callWithArgumentsIsAccepted() {
        assertLlvmAccepts("proc add(int32 x | int32 y) { ret x + y }\nproc main() { ret add(1 | 2) }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun nestedCallIsAccepted() {
        assertLlvmAccepts("proc a(int32 n) { ret n + 1 }\nproc b(int32 n) { ret a(a(n)) }\nproc main() { ret b(1) }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun remainderIsAccepted() {
        assertLlvmAccepts("proc main() { ret 7 % 3 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun equalityIsAccepted() {
        assertLlvmAccepts("proc main() { ret 3 == 3 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun inequalityIsAccepted() {
        assertLlvmAccepts("proc main() { ret 3 != 4 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun lessOrEqualIsAccepted() {
        assertLlvmAccepts("proc main() { ret 3 <= 4 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun greaterOrEqualIsAccepted() {
        assertLlvmAccepts("proc main() { ret 3 >= 4 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun booleanReturnIsAccepted() {
        assertLlvmAccepts("proc f() -> bool { ret 1 }\nproc main() { ret 0 }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun booleanLiteralsAreAccepted() {
        assertLlvmAccepts("proc f() -> bool { ret false }\nproc main() { ret true }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun forLoopIsAccepted() {
        assertLlvmAccepts("proc main() {\n for (int32 i -> 0 | i < 10 | i + 1) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun floatForLoopIsAccepted() {
        assertLlvmAccepts("proc main() {\n for (flt64 i -> 0.0 | i < 2.0 | i + 0.5) { }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun returnInsideForLoopIsAccepted() {
        assertLlvmAccepts("proc find(int32 n) {\n for (int32 i -> 0 | i < n | i + 1) {\n ret i\n }\n ret 0\n}")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun breakAndContinueAreAccepted() {
        assertLlvmAccepts(
            """
                proc main() {
                  for (int32 i -> 0 | i < 10 | i + 1) {
                    if (i == 2) { continue }
                    if (i == 5) { break }
                  }
                  ret 0
                }
                """.trimIndent()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun lastStatementBreakAndContinueAreAccepted() {
        assertLlvmAccepts(
            """
                proc main() {
                  for (int32 i -> 0 | i < 3 | i + 1) {
                    for (int32 j -> 0 | j < 3 | j + 1) {
                      continue
                    }
                    break
                  }
                  ret 0
                }
                """.trimIndent()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun wideIntegersAreAccepted() {
        assertLlvmAccepts("proc main() { int64 big -> 9007199254740993\n ret big }")
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun severalFunctionsInOneFileAreAccepted() {
        assertLlvmAccepts("proc a() { ret 1 }\nproc b() { ret a() }\nproc main() { ret b() }")
    }

    companion object {
        private val LLVM_AS = findTool("llvm-as")

        private fun findTool(name: String): String? {
            val path: String? = java.lang.System.getenv("PATH")
            if (path == null) return null
            for (directory in path.split(":")) {
                val candidate: Path = java.nio.file.Path.of(directory, name)
                if (java.nio.file.Files.isExecutable(candidate)) return candidate.toString()
            }
            return null
        }
    }
}