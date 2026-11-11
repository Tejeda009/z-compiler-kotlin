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

class TestParser {
    @Throws(IOException::class)
    private fun createParser(input: String): src.Parser.Parser {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(input.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        return src.Parser.Parser(lex)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSimpleNumber() {
        val parser: src.Parser.Parser = createParser("42")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBinaryPrecedence() {
        val parser: src.Parser.Parser = createParser("5 + 10 * 2")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
        Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParenthesis() {
        val parser: src.Parser.Parser = createParser("(5 + 10) * 2")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
        Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCall() {
        val parser: src.Parser.Parser = createParser("foo(x | y)")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
        Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLeftParsing() {
        val parser: src.Parser.Parser = createParser("5 - 3 - 1")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallWithoutArgs() {
        val parser: src.Parser.Parser = createParser("foo()")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
        Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallWithExpression() {
        val parser: src.Parser.Parser = createParser("foo(x + 5 | 42)")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNotNull(ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testErrorUnclosedParenthesis() {
        val parser: src.Parser.Parser = createParser("(5 + x")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNull(ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testOrphanOperation() {
        val parser: src.Parser.Parser = createParser("5 +")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNull(ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnexpectedToken() {
        val parser: src.Parser.Parser = createParser("+ 5")
        val ast: ExprAST? = parser.parseExpression()
        Assertions.assertNull(ast)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrecedenceGroupsMultiplicationFirst() {
        val parser: src.Parser.Parser = createParser("5 + 10 * 2")
        val ast: ExprAST? = parser.parseExpression()
        val root: BinaryExprAST = Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, ast)

        Assertions.assertEquals('+', root.operator())
        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, root.left())
        Assertions.assertEquals(
            '*',
            Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, root.right()).operator()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSubtractionIsLeftAssociative() {
        val parser: src.Parser.Parser = createParser("5 - 3 - 1")
        val root: BinaryExprAST =
            Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, parser.parseExpression())

        // (5 - 3) - 1: the first subtraction has to end up on the left, or
        // 5 - 3 - 1 would be read as 5 - (3 - 1).
        Assertions.assertEquals('-', Objects.requireNonNull<BinaryExprAST?>(root).operator())
        val left: BinaryExprAST = Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, root.left())
        Assertions.assertEquals('-', left.operator())
        Assertions.assertEquals(
            "5",
            Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, left.left()).text()
        )
        Assertions.assertEquals(
            "3",
            Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, left.right()).text()
        )
        Assertions.assertEquals(
            "1",
            Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, root.right()).text()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParenthesisOverridesPrecedence() {
        val parser: src.Parser.Parser = createParser("(5 + 10) * 2")
        val root: BinaryExprAST =
            Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, parser.parseExpression())

        Assertions.assertEquals('*', Objects.requireNonNull<BinaryExprAST?>(root).operator())
        val left: ParenExprAST = Assertions.assertInstanceOf<ParenExprAST>(ParenExprAST::class.java, root.left())
        Assertions.assertEquals(
            '+',
            Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, left.inner()).operator()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnaryMinusWrapsTheOperand() {
        val parser: src.Parser.Parser = createParser("-5")
        val unary: UnaryExprAST =
            Assertions.assertInstanceOf<UnaryExprAST>(UnaryExprAST::class.java, parser.parseExpression())

        Assertions.assertInstanceOf<NumberExprAST>(
            NumberExprAST::class.java,
            Objects.requireNonNull<UnaryExprAST?>(unary).operand()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallKeepsItsArguments() {
        val parser: src.Parser.Parser = createParser("foo(x | y)")
        val call: CallExprAST =
            Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, parser.parseExpression())

        Assertions.assertEquals("foo", Objects.requireNonNull<CallExprAST?>(call).callee())
        Assertions.assertEquals(2, call.arguments().size())
        Assertions.assertEquals(
            "x",
            Assertions.assertInstanceOf<VarRefExprAST>(VarRefExprAST::class.java, call.arguments().getFirst()).name()
        )
        Assertions.assertEquals(
            "y",
            Assertions.assertInstanceOf<VarRefExprAST>(VarRefExprAST::class.java, call.arguments().get(1)).name()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallWithAnExpressionArgument() {
        val parser: src.Parser.Parser = createParser("foo(x + 5 | 42)")
        val call: CallExprAST =
            Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, parser.parseExpression())

        Assertions.assertEquals(2, Objects.requireNonNull<CallExprAST?>(call).arguments().size())
        Assertions.assertEquals(
            '+',
            Assertions.assertInstanceOf<BinaryExprAST>(BinaryExprAST::class.java, call.arguments().getFirst())
                .operator()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallWithNoArgumentsHasAnEmptyList() {
        val parser: src.Parser.Parser = createParser("foo()")
        val call: CallExprAST =
            Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, parser.parseExpression())

        Assertions.assertTrue(Objects.requireNonNull<CallExprAST?>(call).arguments().isEmpty())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionCallWithoutArguments() {
        val parser: src.Parser.Parser = createParser("foo()")
        val call: CallExprAST =
            Assertions.assertInstanceOf<CallExprAST>(CallExprAST::class.java, parser.parseExpression())

        Assertions.assertEquals("foo", Objects.requireNonNull<CallExprAST?>(call).callee())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVariableReferenceIsNotACall() {
        val parser: src.Parser.Parser = createParser("x")
        val reference: VarRefExprAST =
            Assertions.assertInstanceOf<VarRefExprAST>(VarRefExprAST::class.java, parser.parseExpression())

        Assertions.assertEquals("x", Objects.requireNonNull<VarRefExprAST?>(reference).name())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testReturnStatement() {
        val parser: src.Parser.Parser = createParser("ret 1 + 2")
        Assertions.assertInstanceOf<RetAST>(RetAST::class.java, parser.parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForLoopIsParsed() {
        val parser: src.Parser.Parser = createParser("for (int32 i -> 0 | i < 10 | i + 1) { }")
        val loop: ForAST = Assertions.assertInstanceOf<ForAST>(ForAST::class.java, parser.parseStatement())

        Assertions.assertEquals("i", Objects.requireNonNull<ForAST?>(loop).varName())
        Assertions.assertEquals(ZType.INT32, loop.varType())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBreakAndContinueAreParsed() {
        val jump: LoopJumpAST =
            Assertions.assertInstanceOf<LoopJumpAST>(LoopJumpAST::class.java, createParser("break").parseStatement())
        Assertions.assertTrue(Objects.requireNonNull<LoopJumpAST?>(jump).isBreak())
        Assertions.assertEquals(ZType.VOID, jump.type(IRBuilder()))

        Assertions.assertFalse(
            Objects.requireNonNull<LoopJumpAST?>(
                Assertions.assertInstanceOf<LoopJumpAST>(
                    LoopJumpAST::class.java,
                    createParser("continue").parseStatement()
                )
            ).isBreak()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForLoopMissingSeparatorFails() {
        val parser: src.Parser.Parser = createParser("for (int32 i -> 0, i < 10 | i + 1) { }")
        Assertions.assertNull(parser.parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeIsParsed() {
        val parser: src.Parser.Parser = createParser("foo(int32 x | flt64 y)")
        val prototype: PrototypeAST? = parser.parsePrototype(false)

        Assertions.assertEquals("foo", Objects.requireNonNull<PrototypeAST?>(prototype).name)
        Assertions.assertEquals(2, prototype.params.size())
        Assertions.assertEquals("x", prototype.params.getFirst().name)
        Assertions.assertEquals("int32", prototype.params.getFirst().type)
        Assertions.assertEquals("flt64", prototype.params.get(1).type)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionDefinitionCarriesItsBody() {
        val parser: src.Parser.Parser = createParser("proc foo(int32 x) { ret x }")
        val function: FunctionAST? = parser.parseDefinition()

        Assertions.assertNotNull(function)
        Assertions.assertEquals("foo", function.prototype().name)
        Assertions.assertEquals(1, function.prototype().params.size())
        Assertions.assertEquals(ZType.FLT64, function.returnType())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainIsTheOnlyFunctionReturningAnInteger() {
        val parser: src.Parser.Parser = createParser("proc main() { ret 1 }")
        val function: FunctionAST? = parser.parseDefinition()

        Assertions.assertEquals(ZType.INT32, Objects.requireNonNull<FunctionAST?>(function).returnType())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSeveralDefinitionsInOneFile() {
        val definitions: MutableList<FunctionAST?>? =
            ZFixtures.parseAll("proc a() { ret 1 }\nproc b() { ret a() }")

        Assertions.assertNotNull(definitions)
        Assertions.assertEquals(2, definitions.size())
        Assertions.assertEquals("a", definitions.getFirst().prototype().name)
        Assertions.assertEquals("b", definitions!!.get(1).prototype().name)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnclosedBraceFailsToParse() {
        Assertions.assertNull(ZFixtures.parseAll("proc a() { ret 1"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnclosedBraceAfterAGoodDefinitionFailsToParse() {
        Assertions.assertNull(ZFixtures.parseAll("proc main() { ret 1 }\nproc broken() { ret"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnclosedPrototypeAfterAGoodDefinitionFailsToParse() {
        Assertions.assertNull(ZFixtures.parseAll("proc main() { ret 1 }\nproc broken() { ret 2"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTruncatedValueAfterAGoodDefinitionFailsToParse() {
        Assertions.assertNull(ZFixtures.parseAll("proc main() { ret 1 }\nint32 x -> "))
    }

    @org.junit.jupiter.api.Test
    fun testGoodDefinitionIsNotLostToALaterSyntaxError() {
        val diagnostics: String? =
            ZFixtures.failureOf("proc main() { ret 1 }\nproc broken() { ret 2")

        Assertions.assertNotNull(diagnostics)
        Assertions.assertTrue(diagnostics.contains("Expected '}'"), diagnostics)
    }

    @org.junit.jupiter.api.Test
    fun testIncompleteTopLevelVariableFails() {
        val diagnostics: String? =
            ZFixtures.failureOf("proc main() { ret 1 }\nint32 x -> ")

        Assertions.assertNotNull(diagnostics)
        // Top-level variables are recognized now, so the failure is the
        // missing initializer expression, not a missing `proc`.
        Assertions.assertTrue(
            diagnostics.contains("unknown token when expecting an expression"),
            diagnostics
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLastDefinitionWithoutNewlineIsStillParsed() {
        Assertions.assertNotNull(ZFixtures.parseAll("proc a() { ret 1 }"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDeclarationWithNoBodyFailsToParse() {
        Assertions.assertNull(ZFixtures.parseAll("proc a() "))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeWithMissingParameterNameFails() {
        val parser: src.Parser.Parser = createParser("foo(int32)")
        Assertions.assertNull(parser.parsePrototype(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeWithUnknownTypeFails() {
        val parser: src.Parser.Parser = createParser("foo(int7 x)")
        Assertions.assertNull(parser.parsePrototype(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testWholeFileGeneratesItsModule() {
        val module: String? = ZFixtures.module("proc a() { ret 1 }\nproc main() { ret a() }")

        Assertions.assertNotNull(module)
        Assertions.assertTrue(module.contains("define dso_local i32 @main()"), module)
        Assertions.assertTrue(module.contains("call double @a()"), module)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSimpleConstant() {
        // cn int64 PI -> 314
        val parser: src.Parser.Parser = createParser("cn int64 PI -> 314")
        val c: DeclaratorExprAST? = parser.parseConstant()

        Assertions.assertNotNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantWithEquals() {
        // 也支持 = 赋值
        val parser: src.Parser.Parser = createParser("cn int32 X = 42")
        val c: DeclaratorExprAST? = parser.parseConstant()

        Assertions.assertNotNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantWithExpressionValue() {
        // 值可以是表达式
        val parser: src.Parser.Parser = createParser("cn int64 X -> 2 + 3")
        val c: DeclaratorExprAST? = parser.parseConstant()

        Assertions.assertNotNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantWithVariableValue() {
        // 值可以是变量引用
        val parser: src.Parser.Parser = createParser("cn int64 Y -> X")
        val c: DeclaratorExprAST? = parser.parseConstant()

        Assertions.assertNotNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantFloatType() {
        val parser: src.Parser.Parser = createParser("cn flt64 PI -> 3.14")
        val c: DeclaratorExprAST? = parser.parseConstant()

        Assertions.assertNotNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantStringType() {
        val parser: src.Parser.Parser = createParser("cn int32 NAME -> 42")
        val c: DeclaratorExprAST? = parser.parseConstant()

        Assertions.assertNotNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantMissingType() {
        val parser: src.Parser.Parser = createParser("cn PI -> 314")
        val c: DeclaratorExprAST? = parser.parseConstant()
        Assertions.assertNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantMissingName() {
        val parser: src.Parser.Parser = createParser("cn int64 -> 314")
        val c: DeclaratorExprAST? = parser.parseConstant()
        Assertions.assertNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantMissingAssign() {
        val parser: src.Parser.Parser = createParser("cn int64 PI 314")
        val c: DeclaratorExprAST? = parser.parseConstant()
        Assertions.assertNull(c)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainLoopWithConstants() {
        // mainLoop 能连续处理多个常量
        val code: String = """
                cn int64 A -> 1
                cn int64 B -> 2
                cn flt64 C -> 3.14
                
                """.trimIndent()

        val parser: src.Parser.Parser = createParser(code)
        Assertions.assertDoesNotThrow(org.junit.jupiter.api.function.Executable { parser.mainLoop() })
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainLoopConstantAndFunction() {
        // 常量和函数混在一起
        val code = "cn int64 PI -> 314\nproc foo(int64 x | int64 y) { ret x }\n"

        val parser: src.Parser.Parser = createParser(code)
        Assertions.assertDoesNotThrow(org.junit.jupiter.api.function.Executable { parser.mainLoop() })
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVariableDeclaration() {
        val code = "int32 var"
        val parser: src.Parser.Parser = createParser(code)
        val v: DeclaratorExprAST? = parser.parseVariable(false)
        Assertions.assertInstanceOf<DeclaratorExprAST>(DeclaratorExprAST::class.java, v)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVariableWithAssignment() {
        val code = "flt32 var = 2"
        val parser: src.Parser.Parser = createParser(code)
        val v: DeclaratorExprAST? = parser.parseVariable(false)
        Assertions.assertInstanceOf<DeclaratorExprAST>(DeclaratorExprAST::class.java, v)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfParsed() {
        val parser: src.Parser.Parser = createParser("if (1 < 2) { ret 1 }")
        val ifAst: IfAST = Assertions.assertInstanceOf<IfAST>(IfAST::class.java, parser.parseStatement())

        Assertions.assertEquals(1, Objects.requireNonNull<IfAST?>(ifAst).branches().size())
        Assertions.assertNull(ifAst.elseBody())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfEifElseParsed() {
        val parser: src.Parser.Parser = createParser(
            "if (1 > 2) { ret 1 } eif (2 > 3) { ret 2 } else { ret 3 }"
        )
        val ifAst: IfAST = Assertions.assertInstanceOf<IfAST>(IfAST::class.java, parser.parseStatement())

        Assertions.assertEquals(2, Objects.requireNonNull<IfAST?>(ifAst).branches().size())
        Assertions.assertNotNull(ifAst.elseBody())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfMissingConditionFails() {
        val parser: src.Parser.Parser = createParser("if { ret 1 }")
        Assertions.assertNull(parser.parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfMissingBodyFails() {
        val parser: src.Parser.Parser = createParser("if (1 < 2) ret 1")
        Assertions.assertNull(parser.parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeWithStrReturnType() {
        val parser: src.Parser.Parser = createParser("greet(str s) -> str")
        val proto: PrototypeAST? = parser.parsePrototype(false)

        Assertions.assertNotNull(proto)
        Assertions.assertEquals(ZType.STRING, proto.returnType)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeWithInt32ReturnType() {
        val parser: src.Parser.Parser = createParser("answer() -> int32")
        val proto: PrototypeAST? = parser.parsePrototype(false)

        Assertions.assertNotNull(proto)
        Assertions.assertEquals(ZType.INT32, proto.returnType)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeWithoutReturnTypeIsNull() {
        val parser: src.Parser.Parser = createParser("answer()")
        val proto: PrototypeAST? = parser.parsePrototype(false)

        Assertions.assertNotNull(proto)
        Assertions.assertNull(proto.returnType)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionDeclaresStrReturnType() {
        val parser: src.Parser.Parser = createParser("proc greet(str s) -> str { ret s }")
        val fn: FunctionAST? = parser.parseDefinition()

        Assertions.assertEquals(ZType.STRING, Objects.requireNonNull<FunctionAST?>(fn).returnType())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFunctionWithoutReturnTypeFallsBackToDouble() {
        val parser: src.Parser.Parser = createParser("proc foo() { ret 1 }")
        val fn: FunctionAST? = parser.parseDefinition()

        Assertions.assertEquals(ZType.FLT64, Objects.requireNonNull<FunctionAST?>(fn).returnType())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainWithoutReturnTypeIsInt32() {
        val parser: src.Parser.Parser = createParser("proc main() { ret 0 }")
        val fn: FunctionAST? = parser.parseDefinition()

        Assertions.assertEquals(ZType.INT32, Objects.requireNonNull<FunctionAST?>(fn).returnType())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPointerType() {
        val parser: src.Parser.Parser = createParser("int32*")
        val type: TypeRef? = parser.parseType(false)
        Assertions.assertEquals(ZType.INT32_PTR, type)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDoublePointerType() {
        val parser: src.Parser.Parser = createParser("int32**")
        val type: TypeRef? = parser.parseType(false)
        Assertions.assertEquals(ZType.PTR, type) // double pointer maps to void*
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAddressOf() {
        val parser: src.Parser.Parser = createParser("&x")
        Assertions.assertInstanceOf<AddressOfExprAST>(AddressOfExprAST::class.java, parser.parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDeref() {
        val parser: src.Parser.Parser = createParser("*p")
        Assertions.assertInstanceOf<DerefExprAST>(DerefExprAST::class.java, parser.parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPointerParameterInPrototype() {
        val parser: src.Parser.Parser = createParser("inc(int32* p)")
        val proto: PrototypeAST? = parser.parsePrototype(false)
        Assertions.assertEquals("int32*", Objects.requireNonNull<PrototypeAST?>(proto).params.getFirst().type)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternPrototypeWithMultipleParametersAndVarargs() {
        val parser: src.Parser.Parser = createParser("printf(str | int32 | ...)")
        val proto: PrototypeAST? = parser.parsePrototype(true)

        Assertions.assertNotNull(proto)
        Assertions.assertEquals(3, proto.params.size())
        Assertions.assertEquals("str", proto.params.get(0).type)
        Assertions.assertEquals("int32", proto.params.get(1).type)
        Assertions.assertEquals(ZType.VARARG.zName(), proto.params.get(2).type)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationIsParsed() {
        StructRegistry.clear()
        val parser: src.Parser.Parser = createParser("struct Point { int32 x | int32 y }")
        Assertions.assertInstanceOf<StructDeclAST>(StructDeclAST::class.java, parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationMissingNameFails() {
        val parser: src.Parser.Parser = createParser("struct { int32 x }")
        Assertions.assertNull(parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationMissingBraceFails() {
        val parser: src.Parser.Parser = createParser("struct Point int32 x }")
        Assertions.assertNull(parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationMissingFieldTypeFails() {
        val parser: src.Parser.Parser = createParser("struct Point { x }")
        Assertions.assertNull(parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationMissingFieldNameFails() {
        val parser: src.Parser.Parser = createParser("struct Point { int32 }")
        Assertions.assertNull(parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructDeclarationMissingCloseFails() {
        val parser: src.Parser.Parser = createParser("struct Point { int32 x")
        Assertions.assertNull(parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDuplicateStructDeclarationFails() {
        StructRegistry.clear()
        val parser: src.Parser.Parser = createParser(
            "struct Point { int32 x }\nstruct Point { int32 y }"
        )
        Assertions.assertNotNull(parser.parseStructDecl())
        Assertions.assertNull(parser.parseStructDecl())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructTypeInParseType() {
        StructRegistry.clear()
        val parser: src.Parser.Parser = createParser("Point")
        // Register the struct first so parseType can resolve the name.
        StructRegistry.register(StructType("Point", java.util.List.of<src.Parser.StructType.Field?>()))
        val t: TypeRef? = parser.parseType(false)
        Assertions.assertInstanceOf<StructType>(StructType::class.java, t)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructPointerTypeInParseType() {
        StructRegistry.clear()
        StructRegistry.register(StructType("Point", java.util.List.of<src.Parser.StructType.Field?>()))
        val parser: src.Parser.Parser = createParser("Point*")
        val t: TypeRef? = parser.parseType(false)
        Assertions.assertSame(ZType.PTR, t)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnknownTokenInError() {
        // `@` is not a known token; describeToken falls back to its raw value.
        val parser: src.Parser.Parser = createParser("proc main() { @ }")
        Assertions.assertNull(parser.parseDefinition())
    }
}