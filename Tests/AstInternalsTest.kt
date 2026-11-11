package src.AST

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

class AstInternalsTest {
    private class Literal(type: ZType, text: String) : ExprAST() {
        private val type: ZType
        private val text: String

        init {
            this.type = type
            this.text = text
        }

        override fun type(builder: IRBuilder?): ZType {
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
    fun testComparisonDetection() {
        for (op in java.util.List.of<String?>("<", "<=", ">", ">=", "==", "!=")) {
            Assertions.assertTrue(
                BinaryExprAST(
                    op,
                    src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                    src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
                ).isComparison()
            )
        }
        Assertions.assertFalse(
            BinaryExprAST(
                "+",
                src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
            ).isComparison()
        )
    }

    @org.junit.jupiter.api.Test
    fun testFloatPredicates() {
        Assertions.assertEquals(
            "olt",
            BinaryExprAST(
                "<",
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "1"),
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "2")
            ).predicate(true, ZType.FLT64)
        )
        Assertions.assertEquals(
            "ole",
            BinaryExprAST(
                "<=",
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "1"),
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "2")
            ).predicate(true, ZType.FLT64)
        )
        Assertions.assertEquals(
            "ogt",
            BinaryExprAST(
                ">",
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "1"),
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "2")
            ).predicate(true, ZType.FLT64)
        )
        Assertions.assertEquals(
            "oge",
            BinaryExprAST(
                ">=",
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "1"),
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "2")
            ).predicate(true, ZType.FLT64)
        )
        Assertions.assertEquals(
            "oeq",
            BinaryExprAST(
                "==",
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "1"),
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "2")
            ).predicate(true, ZType.FLT64)
        )
        Assertions.assertEquals(
            "une",
            BinaryExprAST(
                "!=",
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "1"),
                src.AST.AstInternalsTest.Literal(ZType.FLT64, "2")
            ).predicate(true, ZType.FLT64)
        )
    }

    @org.junit.jupiter.api.Test
    fun testUnsignedPredicates() {
        Assertions.assertEquals(
            "ult",
            BinaryExprAST(
                "<",
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "2")
            ).predicate(false, ZType.UINT32)
        )
        Assertions.assertEquals(
            "ule",
            BinaryExprAST(
                "<=",
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "2")
            ).predicate(false, ZType.UINT32)
        )
        Assertions.assertEquals(
            "ugt",
            BinaryExprAST(
                ">",
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "2")
            ).predicate(false, ZType.UINT32)
        )
        Assertions.assertEquals(
            "uge",
            BinaryExprAST(
                ">=",
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "2")
            ).predicate(false, ZType.UINT32)
        )
        Assertions.assertEquals(
            "eq",
            BinaryExprAST(
                "==",
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "2")
            ).predicate(false, ZType.UINT32)
        )
        Assertions.assertEquals(
            "ne",
            BinaryExprAST(
                "!=",
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.UINT32, "2")
            ).predicate(false, ZType.UINT32)
        )
    }

    @org.junit.jupiter.api.Test
    fun testSignedPredicates() {
        Assertions.assertEquals(
            "slt",
            BinaryExprAST(
                "<",
                src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
            ).predicate(false, ZType.INT32)
        )
        Assertions.assertEquals(
            "sle",
            BinaryExprAST(
                "<=",
                src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
            ).predicate(false, ZType.INT32)
        )
        Assertions.assertEquals(
            "sgt",
            BinaryExprAST(
                ">",
                src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
            ).predicate(false, ZType.INT32)
        )
        Assertions.assertEquals(
            "sge",
            BinaryExprAST(
                ">=",
                src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
            ).predicate(false, ZType.INT32)
        )
    }

    @org.junit.jupiter.api.Test
    fun testPredicateRejectsNonComparisons() {
        val addition: BinaryExprAST = BinaryExprAST(
            "+",
            src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
            src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
        )

        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { addition.predicate(true, ZType.FLT64) })
        Assertions.assertThrows<java.lang.IllegalStateException>(
            java.lang.IllegalStateException::class.java,
            org.junit.jupiter.api.function.Executable { addition.predicate(false, ZType.INT32) })
    }

    @org.junit.jupiter.api.Test
    fun testConditionComparisonNeedsBothSides() {
        val builder: IRBuilder = IRBuilder()
        val leftMissing: BinaryExprAST =
            BinaryExprAST("<", VarRefExprAST("ghost"), src.AST.AstInternalsTest.Literal(ZType.INT32, "1"))
        val rightMissing: BinaryExprAST =
            BinaryExprAST("<", src.AST.AstInternalsTest.Literal(ZType.INT32, "1"), VarRefExprAST("ghost"))

        Assertions.assertNull(Conditions.emitI1(builder, leftMissing))
        Assertions.assertNull(Conditions.emitI1(builder, rightMissing))
    }

    @org.junit.jupiter.api.Test
    fun testConditionRejectsMixedTruthValues() {
        val builder: IRBuilder = IRBuilder()
        val mixed: BinaryExprAST =
            BinaryExprAST("<", BoolExprAST(true), src.AST.AstInternalsTest.Literal(ZType.INT32, "1"))

        Assertions.assertNull(Conditions.emitI1(builder, mixed))
    }

    @org.junit.jupiter.api.Test
    fun testConditionRejectsStructs() {
        val point: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("p", SymbolInfo("p", point, "%p", false))
        val comparison: BinaryExprAST = BinaryExprAST("<", VarRefExprAST("p"), VarRefExprAST("p"))

        Assertions.assertNull(Conditions.emitI1(builder, comparison))
    }

    @org.junit.jupiter.api.Test
    fun testConditionEmitsIntegerAndFloatTests() {
        val builder: IRBuilder = IRBuilder()
        Assertions.assertNotNull(Conditions.emitI1(builder, src.AST.AstInternalsTest.Literal(ZType.INT32, "1")))
        Assertions.assertNotNull(Conditions.emitI1(builder, src.AST.AstInternalsTest.Literal(ZType.FLT64, "1.0")))
        Assertions.assertNotNull(
            Conditions.emitI1(
                builder,
                BinaryExprAST(
                    "<",
                    src.AST.AstInternalsTest.Literal(ZType.INT32, "1"),
                    src.AST.AstInternalsTest.Literal(ZType.INT32, "2")
                )
            )
        )
    }

    @org.junit.jupiter.api.Test
    fun testFieldPointerNeedsAVariableOrFieldBase() {
        val builder: IRBuilder = IRBuilder()
        val access: FieldAccessExprAST = FieldAccessExprAST(src.AST.AstInternalsTest.Literal(ZType.INT32, "1"), "x")

        Assertions.assertNull(access.emitFieldPointer(builder))
    }

    @org.junit.jupiter.api.Test
    fun testFieldPointerNeedsAKnownStruct() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))

        Assertions.assertNull(FieldAccessExprAST(VarRefExprAST("x"), "field").emitFieldPointer(builder))
        Assertions.assertNull(FieldAccessExprAST(VarRefExprAST("ghost"), "field").emitFieldPointer(builder))
    }

    @org.junit.jupiter.api.Test
    fun testFieldPointerForANestedStruct() {
        val inner: StructType = StructType(
            "Inner",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("v", ZType.INT32))
        )
        val outer: StructType = StructType(
            "Outer",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("inner", inner))
        )
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("o", SymbolInfo("o", outer, "%o", false))

        val access: FieldAccessExprAST = FieldAccessExprAST(
            FieldAccessExprAST(VarRefExprAST("o"), "inner"), "v"
        )

        Assertions.assertNotNull(access.emitFieldPointer(builder))
    }

    private class NullCoerce : ExprAST() {
        override fun type(builder: IRBuilder?): TypeRef {
            return ZType.INT32
        }

        override fun codeGen(builder: IRBuilder?): LLVMTextUnit {
            return LLVMTextUnit("1", ZType.INT32)
        }

        override fun coerce(builder: IRBuilder?, llvmTextUnit: LLVMTextUnit?, target: TypeRef?): LLVMTextUnit? {
            return null
        }
    }

    @org.junit.jupiter.api.Test
    fun testConditionStopsWhenACoercionFails() {
        val builder: IRBuilder = IRBuilder()
        val comparison: BinaryExprAST =
            BinaryExprAST("<", NullCoerce(), src.AST.AstInternalsTest.Literal(ZType.INT32, "2"))

        Assertions.assertNull(Conditions.emitI1(builder, comparison))
    }

    @org.junit.jupiter.api.Test
    fun testNestedFieldPointerFailures() {
        val nestedMissing: IRBuilder = IRBuilder()
        Assertions.assertNull(
            FieldAccessExprAST(FieldAccessExprAST(VarRefExprAST("ghost"), "x"), "y").codeGen(
                nestedMissing
            )
        )

        val point: StructType = StructType(
            "Point",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("x", ZType.INT32))
        )
        val notAStruct: IRBuilder = IRBuilder()
        notAStruct.symbols().define("p", SymbolInfo("p", point, "%p", false))
        Assertions.assertNull(FieldAccessExprAST(FieldAccessExprAST(VarRefExprAST("p"), "x"), "y").codeGen(notAStruct))
    }

    @org.junit.jupiter.api.Test
    fun testNestedFieldTypesAndReads() {
        val inner: StructType = StructType(
            "Inner",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("v", ZType.INT64))
        )
        val outer: StructType = StructType(
            "Outer",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("inner", inner))
        )
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("o", SymbolInfo("o", outer, "%o", false))

        Assertions.assertSame(
            ZType.INT64,
            FieldAccessExprAST(FieldAccessExprAST(VarRefExprAST("o"), "inner"), "v").type(builder)
        )
        val llvmTextUnit: LLVMTextUnit? =
            FieldAccessExprAST(FieldAccessExprAST(VarRefExprAST("o"), "inner"), "v").codeGen(builder)
        Assertions.assertNotNull(llvmTextUnit)
        Assertions.assertSame(ZType.INT64, llvmTextUnit.type)
    }
}
