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

class TestAstCoverage {
    private class Literal(type: TypeRef, text: String) : ExprAST() {
        private val type: TypeRef
        private val text: String

        init {
            this.type = type
            this.text = text
        }

        override fun type(builder: IRBuilder?): TypeRef {
            return type
        }

        override fun codeGen(builder: IRBuilder?): LLVMTextUnit {
            return LLVMTextUnit(text, type)
        }

        override fun constantValue(builder: IRBuilder?): LLVMTextUnit {
            return codeGen(builder)
        }
    }

    @AfterEach
    fun clean() {
        StructRegistry.clear()
    }

    @org.junit.jupiter.api.Test
    fun testBaseCoercionGuardsAndConversions() {
        val builder: IRBuilder = IRBuilder()
        val literal: Literal = Tests.TestAstCoverage.Literal(ZType.INT32, "1")
        val llvmTextUnit: LLVMTextUnit = LLVMTextUnit("1", ZType.INT32)

        Assertions.assertNull(literal.coerce(builder, null, ZType.INT32))
        Assertions.assertSame(llvmTextUnit, literal.coerce(builder, llvmTextUnit, null))

        assertEqualsType(ZType.FLT32, literal.coerce(builder, LLVMTextUnit("1.5", ZType.FLT64), ZType.FLT32))
        Assertions.assertTrue(builder.getIR().contains("fptrunc double 1.5 to float"))
        assertEqualsType(ZType.FLT64, literal.coerce(builder, LLVMTextUnit("1.5", ZType.FLT32), ZType.FLT64))
        Assertions.assertTrue(builder.getIR().contains("fpext float 1.5 to double"))
        assertEqualsType(ZType.UINT32, literal.coerce(builder, LLVMTextUnit("1.5", ZType.FLT64), ZType.UINT32))
        Assertions.assertTrue(builder.getIR().contains("fptoui double 1.5 to i32"))
        assertEqualsType(ZType.INT32, literal.coerce(builder, LLVMTextUnit("1", ZType.INT64), ZType.INT32))
        Assertions.assertTrue(builder.getIR().contains("trunc i64 1 to i32"))
        assertEqualsType(ZType.INT64, literal.coerce(builder, LLVMTextUnit("1", ZType.UINT32), ZType.INT64))
        Assertions.assertTrue(builder.getIR().contains("zext i32 1 to i64"))
    }

    @org.junit.jupiter.api.Test
    fun testFloatRemainderIsSupported() {
        val builder: IRBuilder = IRBuilder()
        val llvmTextUnit: LLVMTextUnit? = BinaryExprAST(
            "%",
            Tests.TestAstCoverage.Literal(ZType.FLT64, "1.0"),
            Tests.TestAstCoverage.Literal(ZType.FLT64, "2.0")
        ).codeGen(builder)

        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertTrue(builder.getIR().contains("frem contract double 1.0, 2.0"))
    }

    @org.junit.jupiter.api.Test
    fun testFloatSubtractionIsSupported() {
        val builder: IRBuilder = IRBuilder()
        val llvmTextUnit: LLVMTextUnit? = BinaryExprAST(
            "-",
            Tests.TestAstCoverage.Literal(ZType.FLT64, "3.0"),
            Tests.TestAstCoverage.Literal(ZType.FLT64, "2.0")
        ).codeGen(builder)

        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertTrue(builder.getIR().contains("fsub contract double 3.0, 2.0"))
    }

    @org.junit.jupiter.api.Test
    fun testFloatFastMathFlag() {
        val builder: IRBuilder = IRBuilder()
        builder.setFastMathFlags("fast")
        val llvmTextUnit: LLVMTextUnit? = BinaryExprAST(
            "+",
            Tests.TestAstCoverage.Literal(ZType.FLT64, "1.0"),
            Tests.TestAstCoverage.Literal(ZType.FLT64, "2.0")
        ).codeGen(builder)

        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertTrue(builder.getIR().contains("fadd fast double 1.0, 2.0"))
    }

    @org.junit.jupiter.api.Test
    fun testUnknownStructParameterTypeIsRejected() {
        val builder: IRBuilder = IRBuilder()
        builder.declare(
            PrototypeAST(
                "take",
                java.util.List.of<src.AST.PrototypeAST.Param?>(src.AST.PrototypeAST.Param("Missing", "p", false)),
                ZType.INT32
            )
        )

        Assertions.assertThrows<java.lang.IllegalArgumentException>(
            java.lang.IllegalArgumentException::class.java,
            org.junit.jupiter.api.function.Executable {
                CallExprAST(
                    "take",
                    java.util.List.of<ExprAST?>(NumberExprAST("1", 1))
                ).codeGen(builder)
            })
    }

    @org.junit.jupiter.api.Test
    fun testTypedVariadicCalls() {
        val builder: IRBuilder = IRBuilder()
        val sum: PrototypeAST = PrototypeAST(
            "sum",
            java.util.List.of<src.AST.PrototypeAST.Param>(
                src.AST.PrototypeAST.Param("int32", "count", false),
                src.AST.PrototypeAST.Param("int32", "rest", true)
            )
        )
        builder.declare(sum)

        val llvmTextUnit: LLVMTextUnit? = CallExprAST(
            "sum",
            java.util.List.of<ExprAST>(NumberExprAST("1", 1), NumberExprAST("2", 2))
        ).codeGen(builder)
        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertTrue(builder.getIR().contains("call double (i32, ...) @sum(i32 1, i32 2)"), builder.getIR())
        Assertions.assertTrue(builder.getIR().contains("(i32, ...)"))
    }

    @org.junit.jupiter.api.Test
    fun testVariadicCallWithSeveralFixedParameters() {
        val builder: IRBuilder = IRBuilder()
        val sum: PrototypeAST = PrototypeAST(
            "sum",
            java.util.List.of<src.AST.PrototypeAST.Param?>(
                src.AST.PrototypeAST.Param("int32", "a", false),
                src.AST.PrototypeAST.Param("int32", "b", false),
                src.AST.PrototypeAST.Param("int32", "rest", true)
            )
        )
        builder.declare(sum)

        val llvmTextUnit: LLVMTextUnit? = CallExprAST(
            "sum",
            java.util.List.of<ExprAST?>(NumberExprAST("1", 1), NumberExprAST("2", 2), NumberExprAST("3", 3))
        ).codeGen(builder)

        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertTrue(builder.getIR().contains("(i32, i32, ...)"))
    }

    @org.junit.jupiter.api.Test
    fun testCallResolvesAStructParameterType() {
        val point: StructType = StructType(
            "Point",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("x", ZType.INT32))
        )
        StructRegistry.register(point)
        val builder: IRBuilder = IRBuilder()
        builder.declare(
            PrototypeAST(
                "take",
                java.util.List.of<src.AST.PrototypeAST.Param?>(src.AST.PrototypeAST.Param("Point", "p", false)),
                ZType.INT32
            )
        )

        val llvmTextUnit: LLVMTextUnit? = CallExprAST(
            "take",
            java.util.List.of<ExprAST?>(Tests.TestAstCoverage.Literal(point, "zeroinitializer"))
        ).codeGen(builder)

        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertSame(ZType.INT32, llvmTextUnit.type)
        Assertions.assertTrue(builder.getIR().contains("%Point zeroinitializer"))
    }

    @org.junit.jupiter.api.Test
    fun testMissingVariableFieldAccessFallsBackToInt32() {
        Assertions.assertSame(ZType.INT32, FieldAccessExprAST(VarRefExprAST("ghost"), "x").type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testIfTypeAndElseFailure() {
        val conditional: IfAST = IfAST(
            java.util.List.of<src.AST.IfAST.Branch?>(
                src.AST.IfAST.Branch(
                    NumberExprAST("1", 1),
                    NumberExprAST("2", 2)
                )
            ), VarRefExprAST("ghost")
        )

        Assertions.assertSame(ZType.VOID, conditional.type(IRBuilder()))
        val builder: IRBuilder = IRBuilder()
        Assertions.assertNull(conditional.codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testAssignmentStopsWhenTheValueFails() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))

        Assertions.assertNull(AssignExprAST("x", VarRefExprAST("ghost")).codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testUnaryConstantWithAnUnavailableOperand() {
        Assertions.assertNull(UnaryExprAST(VarRefExprAST("ghost")).constantValue(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testMainWithAnUnsupportedReturnTypeFails() {
        val function: FunctionAST = FunctionAST(
            PrototypeAST("main", java.util.List.of<src.AST.PrototypeAST.Param?>(), ZType.VARARG),
            BlockAST(java.util.List.of<ExprAST?>())
        )
        val builder: IRBuilder = IRBuilder()

        Assertions.assertNull(function.codeGen(builder))
        Assertions.assertTrue(builder.hasFailed())
        Assertions.assertFalse(builder.isTerminated())
    }

    companion object {
        private fun assertEqualsType(expected: TypeRef, llvmTextUnit: LLVMTextUnit?) {
            Assertions.assertNotNull(llvmTextUnit)
            Assertions.assertSame(expected, llvmTextUnit.type)
        }
    }
}
