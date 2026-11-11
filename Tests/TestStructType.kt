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

class TestStructType {
    @AfterEach
    fun clean() {
        StructRegistry.clear()
    }

    @org.junit.jupiter.api.Test
    fun testBasics() {
        val st: StructType = StructType(
            "Point", java.util.List.of<src.Parser.StructType.Field>(
                src.Parser.StructType.Field("x", ZType.INT32),
                src.Parser.StructType.Field("y", ZType.INT64)
            )
        )

        Assertions.assertEquals("Point", st.zName())
        Assertions.assertEquals("%Point", st.llvm())
        Assertions.assertEquals("zeroinitializer", st.initial())
        Assertions.assertEquals(0, st.bits())
        Assertions.assertTrue(st.isStruct())
        Assertions.assertFalse(st.isIntegral())
        Assertions.assertFalse(st.isFloat())
        Assertions.assertFalse(st.isSigned())
        Assertions.assertFalse(st.isUnsigned())
        Assertions.assertFalse(st.isBoolean())
        Assertions.assertFalse(st.isString())
        Assertions.assertFalse(st.isVoid())
        Assertions.assertFalse(st.isPointer())
        Assertions.assertFalse(st.isVararg())
    }

    @org.junit.jupiter.api.Test
    fun testIndexAndFieldType() {
        val st: StructType = StructType(
            "Point", java.util.List.of<src.Parser.StructType.Field>(
                src.Parser.StructType.Field("x", ZType.INT32),
                src.Parser.StructType.Field("y", ZType.FLT64)
            )
        )
        Assertions.assertEquals(0, st.indexOf("x"))
        Assertions.assertEquals(1, st.indexOf("y"))
        Assertions.assertEquals(-1, st.indexOf("z"))
        Assertions.assertSame(ZType.INT32, st.fieldType("x"))
        Assertions.assertSame(ZType.FLT64, st.fieldType("y"))
        Assertions.assertNull(st.fieldType("z"))
    }

    @org.junit.jupiter.api.Test
    fun testPointerHelpers() {
        val st: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        Assertions.assertSame(ZType.PTR, st.pointerTo())
        Assertions.assertNull(st.elementType())
    }

    @org.junit.jupiter.api.Test
    fun testLiteralThrows() {
        val st: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { st.literal(1L) })
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { st.literal(1.0) })
    }

    @org.junit.jupiter.api.Test
    fun testRegistry() {
        val st: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        Assertions.assertFalse(StructRegistry.isStruct("Point"))
        StructRegistry.register(st)
        Assertions.assertTrue(StructRegistry.isStruct("Point"))
        Assertions.assertSame(st, StructRegistry.lookup("Point"))
        Assertions.assertEquals(1, StructRegistry.all().size())
        StructRegistry.clear()
        Assertions.assertFalse(StructRegistry.isStruct("Point"))
        Assertions.assertTrue(StructRegistry.all().isEmpty())
    }

    @org.junit.jupiter.api.Test
    fun testStructDeclTypeIsVoid() {
        val st: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        val decl: StructDeclAST = StructDeclAST(st)
        Assertions.assertSame(ZType.VOID, decl.type(null))
    }

    @org.junit.jupiter.api.Test
    fun testStructWithMissingFieldNameFails() {
        val diagnostics: String? = ZFixtures.failureOf(
            "struct Point { int32 }"
        )
        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("Expected field name"), diagnostics)
    }
}