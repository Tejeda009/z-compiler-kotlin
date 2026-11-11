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

class TestAstNodes {
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
    fun testArrayAccessors() {
        val size: NumberExprAST = NumberExprAST("3", 3)
        val depth: NumberExprAST = NumberExprAST("1", 1)
        val array: ArrayAST =
            ArrayAST(ZType.INT32_PTR, size, depth, "values", java.util.List.of<NumberExprAST?>(NumberExprAST("1", 1)))

        Assertions.assertSame(ZType.INT32_PTR, array.type(null))
        Assertions.assertSame(size, array.getSize())
        Assertions.assertSame(depth, array.getDepth())
        Assertions.assertEquals("values", array.getName())
        Assertions.assertEquals(1, array.getValues().size())
        Assertions.assertEquals("placeholder", array.codeGen(null).text)
        Assertions.assertEquals(ZType.VOID, array.codeGen(null).type)
    }

    @org.junit.jupiter.api.Test
    fun testArrayWithoutValuesKeepsAnEmptyList() {
        val array: ArrayAST = ArrayAST(ZType.INT32_PTR, NumberExprAST("0", 0), NumberExprAST("1", 1), "values", null)

        Assertions.assertTrue(array.getValues().isEmpty())
    }

    @org.junit.jupiter.api.Test
    fun testArrayIndexAccessors() {
        val element = VariableNode()
        val index: ArrayIdxExprAST = ArrayIdxExprAST(element, java.util.List.of<NumberExprAST?>(NumberExprAST("1", 1)))

        Assertions.assertSame(ZType.INT32_PTR, index.type(null))
        Assertions.assertEquals(1, index.getIndexes().size())
        Assertions.assertEquals("placeholder", index.codeGen(null).text)
    }

    @org.junit.jupiter.api.Test
    fun testArrayIndexWithoutIndexesKeepsAnEmptyList() {
        val index: ArrayIdxExprAST = ArrayIdxExprAST(VariableNode(), null)

        Assertions.assertTrue(index.getIndexes().isEmpty())
    }

    @org.junit.jupiter.api.Test
    fun testValueDistinguishesRegisters() {
        Assertions.assertTrue(LLVMTextUnit("%1", ZType.INT32).isRegister())
        Assertions.assertFalse(LLVMTextUnit("1", ZType.INT32).isRegister())
    }

    @org.junit.jupiter.api.Test
    fun testParenDelegates() {
        val number: NumberExprAST = NumberExprAST("7", 7)
        val paren: ParenExprAST = ParenExprAST(number)
        val builder: IRBuilder = IRBuilder()

        Assertions.assertSame(number, paren.inner())
        Assertions.assertSame(ZType.INT32, paren.type(builder))
        Assertions.assertEquals("7", paren.codeGen(builder).text)
        Assertions.assertEquals("7", paren.constantValue(builder).text)
    }

    @org.junit.jupiter.api.Test
    fun testStringLiteralAccessors() {
        val string: StringExprAST = StringExprAST("hello")
        val builder: IRBuilder = IRBuilder()

        Assertions.assertEquals("hello", string.value())
        Assertions.assertSame(ZType.STRING, string.type(builder))
        Assertions.assertNotNull(string.codeGen(builder))
        Assertions.assertEquals("@.str.1", string.constantValue(builder).text)
        Assertions.assertEquals("@.str.0", string.constantValue(IRBuilder()).text)
        Assertions.assertEquals("hello", if (!builder.getGlobals().isEmpty()) string.value() else "")
    }

    @org.junit.jupiter.api.Test
    fun testNumberAccessors() {
        val integer: NumberExprAST = NumberExprAST("42", 42L)
        val floating: NumberExprAST = NumberExprAST("4.5", 4.5)

        Assertions.assertFalse(integer.isFloating())
        Assertions.assertTrue(floating.isFloating())
        Assertions.assertEquals("42", integer.text())
        Assertions.assertEquals("4.5", floating.text())
        Assertions.assertSame(ZType.INT32, integer.type(null))
        Assertions.assertSame(ZType.FLT64, floating.type(null))
    }

    @org.junit.jupiter.api.Test
    fun testNumberCoercions() {
        val builder: IRBuilder = IRBuilder()
        val integer: NumberExprAST = NumberExprAST("42", 42L)
        val floating: NumberExprAST = NumberExprAST("4.5", 4.5)

        Assertions.assertEquals(
            ZType.INT64.llvm(),
            integer.coerce(builder, LLVMTextUnit("42", ZType.INT32), ZType.INT64).type.llvm()
        )
        Assertions.assertEquals(
            ZType.FLT32.llvm(),
            integer.coerce(builder, LLVMTextUnit("42", ZType.INT32), ZType.FLT32).type.llvm()
        )
        Assertions.assertEquals(
            ZType.INT32.llvm(),
            floating.coerce(builder, LLVMTextUnit("4.5", ZType.FLT64), ZType.INT32).type.llvm()
        )
        Assertions.assertEquals(
            ZType.FLT32.llvm(),
            floating.coerce(builder, LLVMTextUnit("4.5", ZType.FLT64), ZType.FLT32).type.llvm()
        )
        Assertions.assertSame(ZType.INT32, integer.coerce(builder, LLVMTextUnit("42", ZType.INT32), ZType.INT32).type)
    }

    @org.junit.jupiter.api.Test
    fun testBoolLiteral() {
        val bool: BoolExprAST = BoolExprAST(true)
        val builder: IRBuilder = IRBuilder()

        Assertions.assertSame(ZType.BOOL, bool.type(builder))
        Assertions.assertEquals("true", bool.codeGen(builder).text)
        Assertions.assertEquals("true", bool.constantValue(builder).text)
    }

    @org.junit.jupiter.api.Test
    fun testUnaryAccessorsAndConstants() {
        val integer: UnaryExprAST = UnaryExprAST(NumberExprAST("5", 5L))
        val floating: UnaryExprAST = UnaryExprAST(NumberExprAST("1.5", 1.5))

        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, integer.operand())
        Assertions.assertSame(ZType.INT32, integer.type(IRBuilder()))
        Assertions.assertEquals("-5", Objects.requireNonNull<LLVMTextUnit?>(integer.constantValue(IRBuilder())).text)
        Assertions.assertEquals("-1.5", Objects.requireNonNull<LLVMTextUnit?>(floating.constantValue(IRBuilder())).text)
        Assertions.assertNull(UnaryExprAST(BoolExprAST(true)).constantValue(IRBuilder()))
        Assertions.assertNull(UnaryExprAST(Tests.TestAstNodes.Literal(ZType.VOID, "void")).constantValue(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testUnaryCodeGen() {
        val builder: IRBuilder = IRBuilder()

        Assertions.assertNotNull(UnaryExprAST(NumberExprAST("5", 5L)).codeGen(builder))
        Assertions.assertNull(UnaryExprAST(VarRefExprAST("ghost")).codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testAddressOfAccessors() {
        val variable: VarRefExprAST = VarRefExprAST("x")
        val address: AddressOfExprAST = AddressOfExprAST(variable)
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))

        Assertions.assertSame(variable, address.operand())
        Assertions.assertSame(ZType.INT32_PTR, address.type(builder))
        Assertions.assertEquals("%x", Objects.requireNonNull<LLVMTextUnit?>(address.codeGen(builder)).text)
    }

    @org.junit.jupiter.api.Test
    fun testAddressOfNeedsAVariable() {
        val builder: IRBuilder = IRBuilder()

        Assertions.assertNull(AddressOfExprAST(NumberExprAST("1", 1)).codeGen(builder))
        Assertions.assertNull(AddressOfExprAST(VarRefExprAST("ghost")).codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testDerefAccessors() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("p", SymbolInfo("p", ZType.INT32_PTR, "%p", false))
        val pointer: VarRefExprAST = VarRefExprAST("p")
        val deref: DerefExprAST = DerefExprAST(pointer)

        Assertions.assertSame(pointer, deref.pointer())
        Assertions.assertSame(ZType.INT32, deref.elementType(builder))
        Assertions.assertSame(ZType.INT32, deref.type(builder))
    }

    @org.junit.jupiter.api.Test
    fun testDerefOfAnUntypedPointer() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("p", SymbolInfo("p", ZType.PTR, "%p", false))

        Assertions.assertSame(ZType.PTR, DerefExprAST(VarRefExprAST("p")).type(builder))
    }

    @org.junit.jupiter.api.Test
    fun testDerefCodeGenFailures() {
        Assertions.assertNull(DerefExprAST(VarRefExprAST("ghost")).codeGen(IRBuilder()))

        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("p", SymbolInfo("p", ZType.PTR, "%p", false))
        Assertions.assertNull(DerefExprAST(VarRefExprAST("p")).codeGen(builder))
    }

    @org.junit.jupiter.api.Test
    fun testAssignThroughPointerAccessors() {
        val target: DerefExprAST = DerefExprAST(VarRefExprAST("p"))
        val value: NumberExprAST = NumberExprAST("1", 1)
        val assign: AssignThroughPointerExprAST = AssignThroughPointerExprAST(target, value)

        Assertions.assertSame(ZType.VOID, assign.type(IRBuilder()))
        val builder: IRBuilder = typedPointerBuilder()
        Assertions.assertNull(assign.codeGen(builder))
        Assertions.assertFalse(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testAssignThroughPointerFailures() {
        Assertions.assertNull(
            AssignThroughPointerExprAST(
                DerefExprAST(VarRefExprAST("ghost")),
                NumberExprAST("1", 1)
            ).codeGen(IRBuilder())
        )

        val untyped: IRBuilder = IRBuilder()
        untyped.symbols().define("p", SymbolInfo("p", ZType.PTR, "%p", false))
        Assertions.assertNull(
            AssignThroughPointerExprAST(
                DerefExprAST(VarRefExprAST("p")),
                NumberExprAST("1", 1)
            ).codeGen(untyped)
        )

        val typed: IRBuilder = typedPointerBuilder()
        Assertions.assertNull(
            AssignThroughPointerExprAST(
                DerefExprAST(VarRefExprAST("p")),
                VarRefExprAST("ghost")
            ).codeGen(typed)
        )

        val stringTarget: IRBuilder = typedPointerBuilder()
        Assertions.assertNull(
            AssignThroughPointerExprAST(
                DerefExprAST(VarRefExprAST("p")),
                StringExprAST("text")
            ).codeGen(stringTarget)
        )
    }

    @org.junit.jupiter.api.Test
    fun testBlockAccessors() {
        val block: BlockAST = BlockAST(java.util.List.of<ExprAST?>(NumberExprAST("1", 1)))

        Assertions.assertEquals(1, block.statements().size())
        Assertions.assertSame(ZType.VOID, block.type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testBlockRunsEveryStatement() {
        val builder: IRBuilder = IRBuilder()
        val block: BlockAST = BlockAST(java.util.List.of<ExprAST>(NumberExprAST("1", 1), NumberExprAST("2", 2)))

        Assertions.assertNull(block.codeGen(builder))
        Assertions.assertFalse(builder.hasFailed())
    }

    @org.junit.jupiter.api.Test
    fun testBlockRejectsUnreachableStatements() {
        val block: BlockAST = BlockAST(java.util.List.of<ExprAST>(RetAST(NumberExprAST("1", 1)), NumberExprAST("2", 2)))

        Assertions.assertNull(block.codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testBlockStopsAtAFailedStatement() {
        val block: BlockAST = BlockAST(java.util.List.of<ExprAST?>(VarRefExprAST("ghost")))

        Assertions.assertNull(block.codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testPrototypeConstructors() {
        val prototype: PrototypeAST = PrototypeAST("main", java.util.List.of<src.AST.PrototypeAST.Param?>())

        Assertions.assertNull(prototype.returnType)
        Assertions.assertTrue(prototype.isMain())
        Assertions.assertFalse(PrototypeAST("other", java.util.List.of<src.AST.PrototypeAST.Param?>()).isMain())
        Assertions.assertTrue(prototype.params.isEmpty())
    }

    @org.junit.jupiter.api.Test
    fun testRetWithoutAValue() {
        val builder: IRBuilder = IRBuilder()

        Assertions.assertSame(ZType.VOID, RetAST(ZType.VOID).type(builder))
        Assertions.assertNull(RetAST(null as ExprAST?).codeGen(builder))
        Assertions.assertFalse(builder.isTerminated())
    }

    @org.junit.jupiter.api.Test
    fun testRetOfAFailedExpression() {
        Assertions.assertNull(RetAST(VarRefExprAST("ghost")).codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testRetEmitsItsValue() {
        val builder: IRBuilder = IRBuilder()
        builder.setReturnType(ZType.INT32)

        Assertions.assertNull(RetAST(NumberExprAST("1", 1)).codeGen(builder))
        Assertions.assertTrue(builder.isTerminated())
    }

    @org.junit.jupiter.api.Test
    fun testStructLiteralTypeAndAccessors() {
        val point: StructType = StructType(
            "Point",
            java.util.List.of<src.Parser.StructType.Field?>(src.Parser.StructType.Field("x", ZType.INT32))
        )
        StructRegistry.register(point)
        val literal: StructLiteralAST = StructLiteralAST("Point", java.util.List.of<ExprAST?>(NumberExprAST("1", 1)))

        Assertions.assertSame(point, literal.type(IRBuilder()))
        Assertions.assertSame(ZType.PTR, StructLiteralAST("Missing", java.util.List.of<ExprAST?>()).type(IRBuilder()))
        Assertions.assertNotNull(literal.codeGen(IRBuilder()))
        Assertions.assertNull(StructLiteralAST("Missing", java.util.List.of<ExprAST?>()).codeGen(IRBuilder()))
        Assertions.assertNull(StructLiteralAST("Point", java.util.List.of<ExprAST?>()).codeGen(IRBuilder()))
        Assertions.assertNull(
            StructLiteralAST("Point", java.util.List.of<ExprAST?>(VarRefExprAST("ghost"))).codeGen(
                IRBuilder()
            )
        )
        Assertions.assertNotNull(literal.constantValue(IRBuilder()))
        Assertions.assertNull(StructLiteralAST("Missing", java.util.List.of<ExprAST?>()).constantValue(IRBuilder()))
        Assertions.assertNull(
            StructLiteralAST(
                "Point",
                java.util.List.of<ExprAST?>(VarRefExprAST("ghost"))
            ).constantValue(IRBuilder())
        )
    }

    @org.junit.jupiter.api.Test
    fun testAssignAccessorsAndType() {
        val assign: AssignExprAST = AssignExprAST("x", NumberExprAST("1", 1))
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))

        Assertions.assertEquals("x", assign.varName())
        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, assign.expr())
        Assertions.assertSame(ZType.INT32, assign.type(builder))
        Assertions.assertSame(ZType.VOID, assign.type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testAssignTypeOfAStructIsVoid() {
        val point: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("p", SymbolInfo("p", point, "%p", false))

        Assertions.assertSame(ZType.VOID, AssignExprAST("p", NumberExprAST("1", 1)).type(builder))
    }

    @org.junit.jupiter.api.Test
    fun testAssignCodeGen() {
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", false))

        Assertions.assertNotNull(AssignExprAST("x", NumberExprAST("1", 1)).codeGen(builder))
        Assertions.assertNull(AssignExprAST("ghost", NumberExprAST("1", 1)).codeGen(IRBuilder()))

        val constant: IRBuilder = IRBuilder()
        constant.symbols().define("x", SymbolInfo("x", ZType.INT32, "%x", true))
        Assertions.assertNull(AssignExprAST("x", NumberExprAST("1", 1)).codeGen(constant))

        Assertions.assertNull(AssignExprAST("x", VarRefExprAST("ghost")).codeGen(IRBuilder()))

        val point: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        val struct: IRBuilder = IRBuilder()
        struct.symbols().define("p", SymbolInfo("p", point, "%p", false))
        Assertions.assertNull(AssignExprAST("p", NumberExprAST("1", 1)).codeGen(struct))

        val string: IRBuilder = IRBuilder()
        string.symbols().define("s", SymbolInfo("s", ZType.STRING, "%s", false))
        Assertions.assertNull(AssignExprAST("s", NumberExprAST("1", 1)).codeGen(string))
    }

    @org.junit.jupiter.api.Test
    fun testBinaryAccessorsAndType() {
        val left: NumberExprAST = NumberExprAST("1", 1)
        val right: NumberExprAST = NumberExprAST("2", 2)
        val binary: BinaryExprAST = BinaryExprAST("+", left, right)

        Assertions.assertEquals('+', binary.operator())
        Assertions.assertSame(left, binary.left())
        Assertions.assertSame(right, binary.right())
        Assertions.assertSame(ZType.INT32, binary.type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testBinaryTypeWithStructsIsInt32() {
        val point: StructType = StructType("Point", java.util.List.of<src.Parser.StructType.Field?>())
        val builder: IRBuilder = IRBuilder()
        builder.symbols().define("p", SymbolInfo("p", point, "%p", false))
        val binary: BinaryExprAST = BinaryExprAST("+", VarRefExprAST("p"), NumberExprAST("1", 1))

        Assertions.assertSame(ZType.INT32, binary.type(builder))
    }

    @org.junit.jupiter.api.Test
    fun testBinaryTypeCatchesUnificationErrors() {
        val binary: BinaryExprAST = BinaryExprAST("+", BoolExprAST(true), NumberExprAST("1", 1))

        Assertions.assertSame(ZType.BOOL, binary.type(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testBinaryCodeGenFailures() {
        Assertions.assertNull(BinaryExprAST("+", VarRefExprAST("ghost"), NumberExprAST("1", 1)).codeGen(IRBuilder()))
        Assertions.assertNull(BinaryExprAST("+", NumberExprAST("1", 1), VarRefExprAST("ghost")).codeGen(IRBuilder()))

        val pointer: IRBuilder = IRBuilder()
        pointer.symbols().define("p", SymbolInfo("p", ZType.PTR, "%p", false))
        Assertions.assertNull(BinaryExprAST("+", VarRefExprAST("p"), NumberExprAST("1", 1)).codeGen(pointer))

        Assertions.assertNull(BinaryExprAST("&", NumberExprAST("1", 1), NumberExprAST("2", 2)).codeGen(IRBuilder()))
        Assertions.assertNull(
            BinaryExprAST(
                "&",
                NumberExprAST("1.5", 1.5),
                NumberExprAST("2.5", 2.5)
            ).codeGen(IRBuilder())
        )
    }

    @org.junit.jupiter.api.Test
    fun testBinaryComparisons() {
        Assertions.assertNotNull(
            BinaryExprAST(
                "<",
                Tests.TestAstNodes.Literal(ZType.FLT64, "1.0"),
                Tests.TestAstNodes.Literal(ZType.FLT64, "2.0")
            ).codeGen(IRBuilder())
        )
        Assertions.assertNotNull(
            BinaryExprAST(
                "<=",
                Tests.TestAstNodes.Literal(ZType.UINT32, "1"),
                Tests.TestAstNodes.Literal(ZType.UINT32, "2")
            ).codeGen(IRBuilder())
        )
        Assertions.assertNotNull(BinaryExprAST("==", NumberExprAST("1", 1), NumberExprAST("1", 1)).codeGen(IRBuilder()))
    }

    @org.junit.jupiter.api.Test
    fun testLiteralNodesKeepTheirRequestedType() {
        val node: Literal = Tests.TestAstNodes.Literal(ZType.INT64, "9")

        Assertions.assertSame(ZType.INT64, node.type(null))
        Assertions.assertEquals("9", node.codeGen(null).text)
        Assertions.assertEquals("9", node.constantValue(null).text)
    }

    private class VariableNode : ExprAST() {
        override fun type(builder: IRBuilder?): TypeRef {
            return ZType.INT32_PTR
        }

        override fun codeGen(builder: IRBuilder?): LLVMTextUnit {
            return LLVMTextUnit("placeholder", ZType.VOID)
        }
    }

    companion object {
        private fun typedPointerBuilder(): IRBuilder {
            val builder: IRBuilder = IRBuilder()
            builder.symbols().define("p", SymbolInfo("p", ZType.INT32_PTR, "%p", false))
            return builder
        }
    }
}
