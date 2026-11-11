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

class TestTypes {
    @org.junit.jupiter.api.Test
    fun testTypeNames() {
        Assertions.assertSame(ZType.INT32, ZType.Companion.fromZName("int32"))
        Assertions.assertSame(ZType.INT64, ZType.Companion.fromZName("int64"))
        Assertions.assertSame(ZType.FLT32, ZType.Companion.fromZName("flt32"))
        Assertions.assertSame(ZType.FLT64, ZType.Companion.fromZName("flt64"))
        Assertions.assertSame(ZType.BOOL, ZType.Companion.fromZName("bool"))
    }

    @org.junit.jupiter.api.Test
    fun testUnknownTypeName() {
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.fromZName("int7") })
        Assertions.assertFalse(ZType.Companion.isType("int7"))
        Assertions.assertTrue(ZType.Companion.isType("flt64"))
    }

    @org.junit.jupiter.api.Test
    fun testLlvmNames() {
        Assertions.assertEquals("i32", ZType.INT32.llvm())
        Assertions.assertEquals("i64", ZType.INT64.llvm())
        Assertions.assertEquals("float", ZType.FLT32.llvm())
        Assertions.assertEquals("double", ZType.FLT64.llvm())
        Assertions.assertEquals("i1", ZType.BOOL.llvm())
        Assertions.assertEquals("void", ZType.VOID.llvm())
    }

    @org.junit.jupiter.api.Test
    fun testWidths() {
        Assertions.assertEquals(32, ZType.INT32.bits())
        Assertions.assertEquals(64, ZType.INT64.bits())
        Assertions.assertEquals(32, ZType.FLT32.bits())
        Assertions.assertEquals(64, ZType.FLT64.bits())
    }

    @org.junit.jupiter.api.Test
    fun testClassification() {
        Assertions.assertTrue(ZType.INT32.isIntegral())
        Assertions.assertTrue(ZType.INT64.isIntegral())
        Assertions.assertFalse(ZType.FLT32.isIntegral())
        Assertions.assertTrue(ZType.FLT32.isFloat())
        Assertions.assertTrue(ZType.FLT64.isFloat())
        Assertions.assertTrue(ZType.BOOL.isBoolean())
        Assertions.assertTrue(ZType.VOID.isVoid())
    }

    @org.junit.jupiter.api.Test
    fun testUnifyPicksTheWiderOfTwoIntegers() {
        Assertions.assertSame(ZType.INT64, ZType.Companion.unify(ZType.INT32, ZType.INT64))
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.INT32, ZType.INT32))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyPicksTheWiderOfTwoFloats() {
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.FLT32, ZType.FLT64))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyWidensAnIntegerToFloat() {
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.INT64, ZType.FLT32))
        Assertions.assertSame(ZType.FLT32, ZType.Companion.unify(ZType.INT32, ZType.FLT32))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyIgnoresVoid() {
        Assertions.assertSame(ZType.INT32, ZType.Companion.unify(ZType.VOID, ZType.INT32))
        Assertions.assertSame(ZType.FLT64, ZType.Companion.unify(ZType.FLT64, null))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyRefusesToMixATruthValueWithANumber() {
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.BOOL, ZType.INT32) })
    }

    @org.junit.jupiter.api.Test
    fun testInitialValues() {
        Assertions.assertEquals("0", ZType.INT32.initial())
        Assertions.assertEquals("0", ZType.INT64.initial())
        Assertions.assertEquals("0.0", ZType.FLT32.initial())
        Assertions.assertEquals("0.0", ZType.FLT64.initial())
    }

    @org.junit.jupiter.api.Test
    fun testWholeNumberLiteralsKeepEveryDigit() {
        Assertions.assertEquals("9007199254740993", ZType.INT64.literal(9007199254740993L))
        Assertions.assertEquals("2147483647", ZType.INT32.literal(2147483647L))
        Assertions.assertEquals("-5", ZType.INT32.literal(-5L))
    }

    @org.junit.jupiter.api.Test
    fun testFloatLiterals() {
        Assertions.assertEquals("3.14", ZType.FLT64.literal(3.14))
        Assertions.assertEquals("4.0", ZType.FLT64.literal(4L))
        Assertions.assertEquals("4.0", ZType.FLT32.literal(4L))
        Assertions.assertEquals("4", ZType.INT64.literal(4L))
    }

    @org.junit.jupiter.api.Test
    fun testBooleanLiterals() {
        Assertions.assertEquals("true", ZType.BOOL.literal(1L))
        Assertions.assertEquals("false", ZType.BOOL.literal(0L))
    }

    @org.junit.jupiter.api.Test
    fun testVoidHasNoLiteral() {
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.VOID.literal(1L) })
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.VOID.literal(1.0) })
    }

    @org.junit.jupiter.api.Test
    fun testNewTypeNames() {
        Assertions.assertSame(ZType.UINT32, ZType.Companion.fromZName("uint32"))
        Assertions.assertSame(ZType.UINT64, ZType.Companion.fromZName("uint64"))
        Assertions.assertSame(ZType.STRING, ZType.Companion.fromZName("str"))
    }

    @org.junit.jupiter.api.Test
    fun testSignedness() {
        Assertions.assertTrue(ZType.INT32.isSigned())
        Assertions.assertTrue(ZType.INT64.isSigned())
        Assertions.assertFalse(ZType.INT32.isUnsigned())
        Assertions.assertTrue(ZType.UINT32.isUnsigned())
        Assertions.assertTrue(ZType.UINT64.isUnsigned())
        Assertions.assertFalse(ZType.UINT32.isSigned())
    }

    @org.junit.jupiter.api.Test
    fun testNewLlvmNames() {
        Assertions.assertEquals("i32", ZType.UINT32.llvm())
        Assertions.assertEquals("i64", ZType.UINT64.llvm())
        Assertions.assertEquals("{ i8*, i64 }", ZType.STRING.llvm())
    }

    @org.junit.jupiter.api.Test
    fun testStringIsNeitherIntegralNorFloat() {
        Assertions.assertFalse(ZType.STRING.isIntegral())
        Assertions.assertFalse(ZType.STRING.isFloat())
        Assertions.assertTrue(ZType.STRING.isString())
    }

    @org.junit.jupiter.api.Test
    fun testUnifySameWidthPrefersUnsigned() {
        Assertions.assertSame(ZType.UINT32, ZType.Companion.unify(ZType.INT32, ZType.UINT32))
        Assertions.assertSame(ZType.UINT64, ZType.Companion.unify(ZType.INT64, ZType.UINT64))
    }

    @org.junit.jupiter.api.Test
    fun testUnifyDifferentWidthsPicksTheWider() {
        Assertions.assertSame(ZType.INT64, ZType.Companion.unify(ZType.INT32, ZType.INT64))
        Assertions.assertSame(ZType.UINT64, ZType.Companion.unify(ZType.UINT32, ZType.UINT64))
        Assertions.assertSame(ZType.UINT64, ZType.Companion.unify(ZType.UINT32, ZType.INT64))
    }

    @org.junit.jupiter.api.Test
    fun testStringCannotBeCombined() {
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.STRING, ZType.INT32) })
        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.Companion.unify(ZType.FLT64, ZType.STRING) })
    }

    @org.junit.jupiter.api.Test
    fun testStringInitialIsNull() {
        Assertions.assertEquals("zeroinitializer", ZType.STRING.initial())
    }

    @org.junit.jupiter.api.Test
    fun testUnsignedInitialIsZero() {
        Assertions.assertEquals("0", ZType.UINT32.initial())
        Assertions.assertEquals("0", ZType.UINT64.initial())
    }

    @org.junit.jupiter.api.Test
    fun testStringHasNoLiteral() {
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.STRING.literal(1L) })
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { ZType.STRING.literal(1.0) })
    }

    @org.junit.jupiter.api.Test
    fun testZTypeIsStructAndIsVararg() {
        Assertions.assertFalse(ZType.INT32.isStruct())
        Assertions.assertFalse(ZType.INT32.isVararg())
        Assertions.assertTrue(ZType.VARARG.isVararg())
    }

    @org.junit.jupiter.api.Test
    fun testZTypePointerToReturnsZType() {
        Assertions.assertSame(ZType.INT32_PTR, ZType.INT32.pointerTo())
        Assertions.assertSame(ZType.PTR, ZType.VOID.pointerTo())
    }
}