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

class TestOperators {
    @Throws(IOException::class)
    private fun codeGen(code: String): String? {
        return ZFixtures.module(code)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIntegerRemainderUsesSrem() {
        Assertions.assertTrue(codeGen("proc main() { ret 7 % 3 }").contains("srem i32 7, 3"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedRemainderUsesUrem() {
        val ir = codeGen("proc main() { uint32 a -> 7\n ret a % 3 }")
        Assertions.assertTrue(ir.contains("urem i32"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatingRemainderUsesFrem() {
        Assertions.assertTrue(codeGen("proc main() { ret 7.0 % 3.0 }").contains("frem contract double 7.0, 3.0"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEqualityUsesEq() {
        val ir = codeGen("proc main() { ret 3 == 3 }")
        Assertions.assertTrue(ir.contains("icmp eq i32 3, 3"), ir)
        Assertions.assertTrue(ir.contains("zext i1"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testInequalityUsesNe() {
        Assertions.assertTrue(codeGen("proc main() { ret 3 != 4 }").contains("icmp ne i32 3, 4"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLessOrEqualUsesSle() {
        Assertions.assertTrue(codeGen("proc main() { ret 3 <= 4 }").contains("icmp sle i32 3, 4"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testGreaterOrEqualUsesSge() {
        Assertions.assertTrue(codeGen("proc main() { ret 3 >= 4 }").contains("icmp sge i32 3, 4"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedLessOrEqualUsesUle() {
        val ir = codeGen("proc main() { uint32 a -> 3\n ret a <= 4 }")
        Assertions.assertTrue(ir.contains("icmp ule i32"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnsignedConditionUsesUnsignedPredicate() {
        val ir = codeGen(
            "proc main() { uint32 a -> 4294967295\n if (a > 0) { ret 1 }\n ret 0 }"
        )
        Assertions.assertTrue(ir.contains("icmp ugt i32"), ir)
        Assertions.assertFalse(ir.contains("icmp sgt i32"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatingEqualityUsesOeq() {
        Assertions.assertTrue(codeGen("proc main() { ret 1.5 == 2.5 }").contains("fcmp oeq double"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatingInequalityUsesUne() {
        Assertions.assertTrue(codeGen("proc main() { ret 1.5 != 2.5 }").contains("fcmp une double"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBooleanReturnIsTruncated() {
        val ir = codeGen("proc f() -> bool { ret 1 }\nproc main() { ret 0 }")
        Assertions.assertTrue(ir.contains("trunc i32 1 to i1"), ir)
        Assertions.assertTrue(ir.contains("ret i1"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBooleanLiteralHasBooleanType() {
        val ir = codeGen("proc yes() -> bool { ret true }\nproc main() { ret 0 }")
        Assertions.assertTrue(ir.contains("ret i1 true"), ir)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBooleanLiteralWidensWithZext() {
        val ir = codeGen("proc main() { ret true }")
        Assertions.assertTrue(ir.contains("zext i1 true to i32"), ir)
    }
}
