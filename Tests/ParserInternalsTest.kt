package src.Parser

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

class ParserInternalsTest {
    @AfterEach
    fun clean() {
        StructRegistry.clear()
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSubParsersAreExposed() {
        val parser: src.Parser.Parser = parser("proc main() { ret 0 }")

        Assertions.assertNotNull(parser.getDefinitionParser())
        Assertions.assertNotNull(parser.getArrayParser())
        Assertions.assertNotNull(parser.getParserType())
        Assertions.assertNotNull(parser.getHeaderParser())
        Assertions.assertNotNull(parser.getExpressionParser())
        Assertions.assertNotNull(parser.getBranchesParser())
        Assertions.assertNotNull(parser.getLoopParser())
        Assertions.assertNotNull(parser.getRetParser())
        Assertions.assertNotNull(parser.getStructParser())
        Assertions.assertNotNull(parser.getVariableParser())
        Assertions.assertSame(parser.getContext().getLex(), parser.getContext().getLex())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDirectExpressionEntryPoints() {
        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, parser("42").parseNumberExpr(42.0))
        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, parser("42").parsePrimary())
        Assertions.assertInstanceOf<VarRefExprAST>(VarRefExprAST::class.java, parser("x").parseIdentifierExpr())
        Assertions.assertInstanceOf<VarRefExprAST>(VarRefExprAST::class.java, parser("x").parsePrimary())
        Assertions.assertNotNull(parser("(1 + 2)").parseParenExpr())
        Assertions.assertNotNull(parser("(1 + 2)").parseExpression())
        Assertions.assertNotNull(parser("+ 1").parseBinOpRHS(0, NumberExprAST("1", 1)))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfBranchEntryPoint() {
        val branch: src.AST.IfAST.Branch? = parser("(1 < 2) { ret 1 }").parseIfBranch("if")

        Assertions.assertNotNull(branch)
        Assertions.assertNotNull(branch.cond)
        Assertions.assertNotNull(branch.body)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTypeDeclarationPredicate() {
        StructRegistry.register(StructType("Point", java.util.List.of<src.Parser.StructType.Field?>()))
        Assertions.assertTrue(parser("Point").atTypeDeclaration())
        Assertions.assertTrue(parser("int32").atTypeDeclaration())
        Assertions.assertFalse(parser("42").atTypeDeclaration())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarargType() {
        Assertions.assertSame(ZType.VARARG, parser("...").parseType(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnknownTypeIsNull() {
        Assertions.assertNull(parser("nope").parseType(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainLoopSkipsSemicolonsAndUnknownTokens() {
        parser("; ; @ ; int32 x ;").mainLoop()
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainLoopHandlesExternAndArrays() {
        parser("extern sqrt(flt64) -> flt64").mainLoop()
        parser("[int32|3|1] values").mainLoop()
    }

    @org.junit.jupiter.api.Test
    fun testParserErrorReturnsNull() {
        Assertions.assertNull(src.Parser.Parser.Companion.error<Any?>("boom"))
    }

    @org.junit.jupiter.api.Test
    fun testTypeHelpers() {
        Assertions.assertFalse(TypeParser.Companion.isTypeToken(null))
        Assertions.assertTrue(TypeParser.Companion.isTypeToken(src.Lexer.Lexer.Tokens.STRING))
        Assertions.assertTrue(TypeParser.Companion.isTypeToken(src.Lexer.Lexer.Tokens.INT32))
        Assertions.assertFalse(TypeParser.Companion.isTypeToken(src.Lexer.Lexer.Tokens.IDENTIFIER))
        Assertions.assertSame(ZType.INT32, TypeParser.Companion.resolveType("int32"))
        Assertions.assertNull(TypeParser.Companion.resolveType("int7"))
    }

    @org.junit.jupiter.api.Test
    fun testTokenDescriptions() {
        Assertions.assertEquals("int32", TypeParser.Companion.describeToken(src.Lexer.Lexer.Tokens.INT32.value))
        Assertions.assertEquals("'$'", TypeParser.Companion.describeToken('$'.code))
        Assertions.assertEquals("70000", TypeParser.Companion.describeToken(70000))
        Assertions.assertEquals("-999", TypeParser.Companion.describeToken(-999))
    }

    @org.junit.jupiter.api.Test
    fun testLiteralKeepsIntegerText() {
        val number: NumberExprAST = src.Parser.ExpressionParser.Companion.literal(1.0, "1", false)

        Assertions.assertEquals("1", number.text())
        Assertions.assertFalse(number.isFloating())
    }

    @org.junit.jupiter.api.Test
    fun testLiteralFallsBackWhenTextIsNotALong() {
        val number: NumberExprAST = src.Parser.ExpressionParser.Companion.literal(1.0, "1e", false)

        Assertions.assertEquals("1", number.text())
        Assertions.assertFalse(number.isFloating())
    }

    @org.junit.jupiter.api.Test
    fun testLiteralKeepsFractionsAndHugeValues() {
        Assertions.assertEquals("1.5", src.Parser.ExpressionParser.Companion.literal(1.5, "1.5", false).text())
        Assertions.assertEquals("1.0E20", src.Parser.ExpressionParser.Companion.literal(1e20, null, false).text())
        Assertions.assertEquals("1.0", src.Parser.ExpressionParser.Companion.literal(1.0, "1", true).text())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayWithoutValues() {
        val array: ArrayAST? = array("[int32|5|2] values")

        Assertions.assertNotNull(array)
        Assertions.assertEquals("values", array.getName())
        Assertions.assertEquals(0, array.getValues().size())
        Assertions.assertEquals(ZType.INT32_PTR, array.type(null))
        Assertions.assertEquals("placeholder", array.codeGen(null).text)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayWithValues() {
        val array: ArrayAST? = array("[int32|3|1] values { -> 1|2|3 }")

        Assertions.assertNotNull(array)
        Assertions.assertEquals(3, array.getValues().size())
        Assertions.assertEquals("1", array.getValues().getFirst().text())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayAcceptsEqualsAfterTheBrace() {
        val array: ArrayAST? = array("[int32|2|1] values { = 7|8 }")

        Assertions.assertNotNull(array)
        Assertions.assertEquals(2, array.getValues().size())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayStopsAtTheClosingBrace() {
        val array: ArrayAST? = array("[int32|3|1] values { -> 1 }")

        Assertions.assertNotNull(array)
        Assertions.assertEquals(1, array.getValues().size())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayIgnoresExtraValues() {
        val array: ArrayAST? = array("[int32|2|1] values { -> 1|2|3 }")

        Assertions.assertNotNull(array)
        Assertions.assertEquals(2, array.getValues().size())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresAType() {
        Assertions.assertNull(array("[nope|3|1] values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRejectsATypeWithoutAPointerForm() {
        Assertions.assertNull(array("[ptr|3|1] values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresThePipeAfterTheType() {
        Assertions.assertNull(array("[int32 3|1] values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresASize() {
        Assertions.assertNull(array("[int32|] values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresADepth() {
        Assertions.assertNull(array("[int32|3|] values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayAcceptsAZeroSize() {
        val array: ArrayAST? = array("[int32|0|1] values")

        Assertions.assertNotNull(array)
        Assertions.assertEquals("0", array.getSize().text())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresTheClosingBracket() {
        Assertions.assertNull(array("[int32|3|1 values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresAName() {
        Assertions.assertNull(array("[int32|3|1]"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresAnArrowBeforeItsValues() {
        Assertions.assertNull(array("[int32|3|1] values { 1 }"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayRequiresASeparatorBetweenValues() {
        Assertions.assertNull(array("[int32|3|1] values { -> 1 2 }"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParenthesisedAssignmentTarget() {
        val expression: ExprAST? = parser("(x) -> 1").parseExpression()

        Assertions.assertInstanceOf<AssignExprAST>(AssignExprAST::class.java, expression)
        Assertions.assertEquals("x", (expression as AssignExprAST).varName())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarargIndexReference() {
        val expression: ExprAST? = parser("args.1").parseExpression()

        Assertions.assertEquals(
            "args.1",
            Assertions.assertInstanceOf<VarRefExprAST>(VarRefExprAST::class.java, expression).name()
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBracesCanWrapAPrimaryExpression() {
        val parser: src.Parser.Parser = parser("{ 1 }")

        Assertions.assertInstanceOf<NumberExprAST>(NumberExprAST::class.java, parser.parsePrimary())
    }


    @org.junit.jupiter.api.Test
    fun testEveryBuiltInTokenIsAType() {
        for (token in arrayOf<src.Lexer.Lexer.Tokens>(
            src.Lexer.Lexer.Tokens.STRING, src.Lexer.Lexer.Tokens.UINT32, src.Lexer.Lexer.Tokens.UINT64,
            src.Lexer.Lexer.Tokens.BOOL, src.Lexer.Lexer.Tokens.VOID, src.Lexer.Lexer.Tokens.INT64,
            src.Lexer.Lexer.Tokens.FLOAT32, src.Lexer.Lexer.Tokens.FLOAT64, src.Lexer.Lexer.Tokens.PTR
        )) {
            Assertions.assertTrue(TypeParser.Companion.isTypeToken(token))
        }
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayIndexingThroughTheBinaryEntryPoint() {
        val parser: src.Parser.Parser = parser("x[[1]]")
        val result: ExprAST? = parser.parseBinOpRHS(-1, parser.parsePrimary())

        Assertions.assertInstanceOf<ArrayIdxExprAST>(ArrayIdxExprAST::class.java, result)
        Assertions.assertEquals(1, (result as ArrayIdxExprAST).getIndexes().size())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayIndexingNeedsANumberAndAClosingBracket() {
        val missingNumber: src.Parser.Parser = parser("x[[value]]")
        Assertions.assertNotNull(missingNumber.parseBinOpRHS(-1, missingNumber.parsePrimary()))

        val missingBracket: src.Parser.Parser = parser("x[[1] 2")
        Assertions.assertNull(missingBracket.parseBinOpRHS(-1, missingBracket.parsePrimary()))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAssignmentToADottedVarRefFails() {
        Assertions.assertNull(parser("args.1 -> 2").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testNestedPrecedenceFailureReturnsNull() {
        Assertions.assertNull(parser("1 + 2 *").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParenWithAMissingExpressionFails() {
        Assertions.assertNull(parser("()").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayStatementEntryPoint() {
        Assertions.assertNull(parser("[int32|3|1] values").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayDepthBranch() {
        val array: ArrayAST? = array("[int32|5|2|3] 9 values")

        Assertions.assertNotNull(array)
        Assertions.assertEquals("values", array.getName())
        Assertions.assertEquals("3", array.getDepth().text())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testArrayDepthBranchErrors() {
        Assertions.assertNotNull(array("[int32|5|2|x] 9 values"))
        Assertions.assertNull(array("[int32|0|1|2] 9 values"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testPrototypeNeedsAnOpeningParenthesis() {
        Assertions.assertNull(parser("f").parsePrototype(false))
    }

    companion object {
        @Throws(IOException::class)
        private fun parser(source: String): src.Parser.Parser {
            return src.Parser.Parser(src.Lexer.Lexer(ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
        }

        @Throws(IOException::class)
        private fun array(source: String): ArrayAST? {
            val parser: src.Parser.Parser = parser(source)
            parser.getContext().next()
            return parser.getArrayParser().parseArray()
        }
    }
}
