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

class TestZTypeCoverage {
    @org.junit.jupiter.api.Test
    fun testUnifyHandlesMissingVoidAndEqualOperands() {
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(null, ZType.INT32))
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.INT32, null))
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.INT32, ZType.INT32))
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.VOID, ZType.INT32))
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.INT32, ZType.VOID))
        Assertions.assertSame(ZType.BOOL, ZType.Companion.unify(ZType.BOOL, ZType.BOOL))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyPicksTheWiderNumericType() {
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.FLT32, ZType.FLT64))
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.FLT64, ZType.FLT32))
        Assertions.assertSame(ZType.FLT32, ZType.Companion.unify(ZType.FLT32, ZType.FLT32))
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.INT64, ZType.FLT32))
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.FLT64, ZType.INT32))
        Assertions.assertSame(ZType.UINT32, ZType.Companion.unify(ZType.INT32, ZType.UINT32))
        Assertions.assertSame(ZType.UINT64, ZType.Companion.unify(ZType.UINT64, ZType.INT32))
        Assertions.assertSame(ZType.INT64, ZType.Companion.unify(ZType.INT64, ZType.INT32))
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.INT32, ZType.INT32))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyRejectsMixedFamilies() {
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.BOOL, ZType.INT32) })
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.INT32_PTR, ZType.INT32) })
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.STRING, ZType.INT32) })
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.INT32, ZType.STRING) })
    }

    @org.junit.jupiter.api.Test
    fun testIntegerLiteralsAcrossTypes() {
        Assertions.assertEquals("true", ZType.BOOL.literal(3L))
        Assertions.assertEquals("false", ZType.BOOL.literal(0L))
        Assertions.assertEquals("7", ZType.INT32.literal(7L))
        Assertions.assertEquals("7", ZType.UINT32.literal(7L))
        Assertions.assertEquals("7", ZType.INT64.literal(7L))
        Assertions.assertEquals("7", ZType.UINT64.literal(7L))
        Assertions.assertEquals("7.0", ZType.FLT64.literal(7L))
    }

    @org.junit.jupiter.api.Test
    fun testDoubleLiteralsAcrossTypes() {
        Assertions.assertEquals("true", ZType.BOOL.literal(0.5))
        Assertions.assertEquals("false", ZType.BOOL.literal(0.0))
        Assertions.assertEquals("7", ZType.INT32.literal(7.9))
        Assertions.assertEquals("7", ZType.UINT32.literal(7.9))
        Assertions.assertEquals("7", ZType.INT64.literal(7.9))
        Assertions.assertEquals("7", ZType.UINT64.literal(7.9))
        Assertions.assertEquals("7.5", ZType.FLT64.literal(7.5))
        Assertions.assertEquals("7.5", ZType.FLT32.literal(7.5))
    }

    @org.junit.jupiter.api.Test
    fun testFlt32HexLiteralWhenDecimalDoesNotRoundTrip() {
        Assertions.assertEquals("0x3FB99999A0000000", ZType.FLT32.literal(0.1))
    }

    @org.junit.jupiter.api.Test
    fun testTypesWithoutLiteralsThrow() {
        for (type in arrayOf<ZType>(ZType.PTR, ZType.STRING, ZType.VOID)) {
            Assertions.assertThrows<java.lang.IllegalStateException>(
                java.lang.IllegalStateException::class.java,
                org.junit.jupiter.api.function.Executable { type.literal(1L) })
            Assertions.assertThrows<java.lang.IllegalStateException>(
                java.lang.IllegalStateException::class.java,
                org.junit.jupiter.api.function.Executable { type.literal(1.0) })
        }
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.VARARG.literal(1L) })
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.VARARG.literal(1.0) })
    }

    @org.junit.jupiter.api.Test
    fun testPointerToEveryBuiltInType() {
        Assertions.assertSame(ZType.INT32_PTR, ZType.INT32.pointerTo())
        Assertions.assertSame(ZType.INT64_PTR, ZType.INT64.pointerTo())
        Assertions.assertSame(ZType.UINT32_PTR, ZType.UINT32.pointerTo())
        Assertions.assertSame(ZType.UINT64_PTR, ZType.UINT64.pointerTo())
        Assertions.assertSame(ZType.FLT32_PTR, ZType.FLT32.pointerTo())
        Assertions.assertSame(ZType.FLT64_PTR, ZType.FLT64.pointerTo())
        Assertions.assertSame(ZType.BOOL_PTR, ZType.BOOL.pointerTo())
        Assertions.assertSame(ZType.STR_PTR, ZType.STRING.pointerTo())
        Assertions.assertSame(ZType.PTR, ZType.VOID.pointerTo())
        Assertions.assertSame(ZType.PTR, ZType.PTR.pointerTo())
        Assertions.assertSame(ZType.PTR, ZType.VARARG.pointerTo())
    }

    @org.junit.jupiter.api.Test
    fun testElementTypeOfEveryPointer() {
        Assertions.assertSame(ZType.INT32, ZType.INT32_PTR.elementType())
        Assertions.assertSame(ZType.INT64, ZType.INT64_PTR.elementType())
        Assertions.assertSame(ZType.UINT32, ZType.UINT32_PTR.elementType())
        Assertions.assertSame(ZType.UINT64, ZType.UINT64_PTR.elementType())
        Assertions.assertSame(ZType.FLT32, ZType.FLT32_PTR.elementType())
        Assertions.assertSame(ZType.FLT64, ZType.FLT64_PTR.elementType())
        Assertions.assertSame(ZType.BOOL, ZType.BOOL_PTR.elementType())
        Assertions.assertSame(ZType.STRING, ZType.STR_PTR.elementType())
        Assertions.assertNull(ZType.PTR.elementType())
        Assertions.assertNull(ZType.INT32.elementType())
    }

    @org.junit.jupiter.api.Test
    fun testTypePredicates() {
        Assertions.assertTrue(ZType.INT32.isIntegral())
        Assertions.assertTrue(ZType.UINT64.isIntegral())
        Assertions.assertTrue(ZType.BOOL.isIntegral())
        Assertions.assertFalse(ZType.FLT32.isIntegral())
        Assertions.assertTrue(ZType.FLT32.isFloat())
        Assertions.assertFalse(ZType.INT32.isFloat())
        Assertions.assertTrue(ZType.INT32.isSigned())
        Assertions.assertTrue(ZType.UINT32.isUnsigned())
        Assertions.assertTrue(ZType.BOOL.isBoolean())
        Assertions.assertTrue(ZType.STRING.isString())
        Assertions.assertTrue(ZType.VOID.isVoid())
        Assertions.assertTrue(ZType.INT32_PTR.isPointer())
        Assertions.assertTrue(ZType.VARARG.isVararg())
        Assertions.assertFalse(ZType.INT32.isStruct())
        Assertions.assertEquals("int32", ZType.Companion.fromZName("int32").zName())
        Assertions.assertTrue(ZType.Companion.isType("flt64"))
        Assertions.assertFalse(ZType.Companion.isType("nope"))
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.fromZName("nope") })
        Assertions.assertEquals("0", ZType.INT32.initial())
        Assertions.assertEquals(32, ZType.INT32.bits())
    }
}
