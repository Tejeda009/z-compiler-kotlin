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

class TestBinding {
    private val end: TestEndToEnd = TestEndToEnd()

    @org.junit.jupiter.api.Test
    @Throws(java.lang.InterruptedException::class, IOException::class)
    fun testBasic() {
        if (verifyTools()) return

        val binary: Path = end.compile(
            "test", """
                extern isalpha(int32) -> bool
                
                proc main() {
                    int32 test -> 60
                    bool result -> isalpha(60)
                    ret result
                }
                
                """.trimIndent()
        )
        Assertions.assertEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testSqrt() {
        if (verifyTools()) return

        val binary: Path = end.compile(
            "test2", """
                extern sqrt(flt64) -> flt64
                
                proc main () {
                    flt64 test -> 60
                    flt64 test2 -> sqrt(test)
                    ret test2
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(7, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testIsDigit() {
        if (verifyTools()) return

        val binary: Path = end.compile(
            "test3", """
                extern isdigit(int32) -> int32
                
                proc main () {
                    int32 test -> 60
                    int32 test2 -> isdigit(test)
                    ret test2
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(0, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testExit() {
        if (verifyTools()) return

        val binary: Path = end.compile(
            "test", """
                extern exit(int32) -> void
                
                proc main () {
                    int32 test -> 60
                    exit(test)
                    ret test
                }
                
                """.trimIndent()
        )


        Assertions.assertEquals(60, end.run(binary))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class, java.lang.InterruptedException::class)
    fun testPrintfWithVarargs() {
        if (verifyTools()) return

        val binary: Path = end.compile(
            "test4", """
                extern printf(str | ...) -> int32
                
                proc main () {
                    int32 written -> printf("%d" | 42)
                    ret written
                }
                
                """.trimIndent()
        )

        Assertions.assertEquals(2, end.run(binary))
    }

    companion object {
        private fun verifyTools(): Boolean {
            return TestEndToEnd.Companion.OPT == null || TestEndToEnd.Companion.CLANG == null
        }
    }
}
