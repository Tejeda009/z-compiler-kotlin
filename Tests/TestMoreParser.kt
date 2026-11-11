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

class TestMoreParser {
    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAssignmentNeedsAValue() {
        Assertions.assertNull(parser("x ->").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructLiteralNeedsSeparators() {
        StructRegistry.clear()
        Assertions.assertNotNull(parser("struct Point { int32 x | int32 y }").parseStructDecl())
        Assertions.assertNull(parser("Point{1 2}").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructLiteralNeedsValuesThatParse() {
        StructRegistry.clear()
        Assertions.assertNotNull(parser("struct Point { int32 x | int32 y }").parseStructDecl())
        Assertions.assertNull(parser("Point{1 + | 2}").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallNeedsSeparators() {
        Assertions.assertNull(parser("f(1 2)").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCallNeedsValuesThatParse() {
        Assertions.assertNull(parser("f(1 +)").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAddressOfNeedsAnOperand() {
        Assertions.assertNull(parser("&").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDerefNeedsAnOperand() {
        Assertions.assertNull(parser("*").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnaryMinusNeedsAnOperand() {
        Assertions.assertNull(parser("-").parseExpression())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDefinitionAfterImportIsRejected() {
        Assertions.assertNull(parser("import math").parseDefinition())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDefinitionNeedsProc() {
        Assertions.assertNull(parser("int32 x").parseDefinition())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDefinitionNeedsAPrototype() {
        Assertions.assertNull(parser("proc").parseDefinition())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDefinitionSkipsSemicolons() {
        Assertions.assertNotNull(parser("proc f() { ; ret 1 ; }").parseDefinition())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVariableNeedsAType() {
        Assertions.assertNull(parser("42").parseVariable(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantNeedsAValue() {
        Assertions.assertNull(parser("cn int32 X ->").parseConstant())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternRequiresTheKeyword() {
        Assertions.assertNull(parser("int32").parseExtern())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternRequiresAPrototype() {
        Assertions.assertNull(parser("extern").parseExtern())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternRequiresAReturnType() {
        Assertions.assertNull(parser("extern sqrt(flt64)").parseExtern())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternRejectsANonTypeReturn() {
        Assertions.assertNull(parser("extern sqrt(flt64) -> 1").parseExtern())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExternVarargMustBeLast() {
        Assertions.assertNull(parser("f(... | int32)").parsePrototype(true))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarargNeedsAType() {
        Assertions.assertNull(parser("f(...)").parsePrototype(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarargMustBeTheLastParameter() {
        Assertions.assertNull(parser("f(... int32 x | int32 y)").parsePrototype(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarargCanBeTheLastParameter() {
        val prototype: PrototypeAST? = parser("f(... int32 x)").parsePrototype(false)

        Assertions.assertNotNull(prototype)
        Assertions.assertEquals(ZType.INT32.zName(), prototype.params.getFirst().type)
        Assertions.assertEquals("x", prototype.params.getFirst().name)
        Assertions.assertTrue(prototype.params.getFirst().varArg)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testParameterListNeedsSeparators() {
        Assertions.assertNull(parser("f(int32 x int32 y)").parsePrototype(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testReturnTypeMustBeAType() {
        Assertions.assertNull(parser("f() -> 1").parsePrototype(false))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStructReturnTypeIsAccepted() {
        StructRegistry.clear()
        StructRegistry.register(StructType("Point", java.util.List.of<src.Parser.StructType.Field?>()))
        val prototype: PrototypeAST? = parser("f() -> Point").parsePrototype(false)

        Assertions.assertNotNull(prototype)
        Assertions.assertInstanceOf<StructType>(StructType::class.java, prototype.returnType)
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutParenthesisFails() {
        Assertions.assertNull(parser("for").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutTypeFails() {
        Assertions.assertNull(parser("for (nope i -> 0 | 1 | 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutNameFails() {
        Assertions.assertNull(parser("for (int32 -> 0 | 1 | 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutInitialValueFails() {
        Assertions.assertNull(parser("for (int32 i 0 | 1 | 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithABrokenInitialValueFails() {
        Assertions.assertNull(parser("for (int32 i -> + | 1 | 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutThePipeAfterTheInitialValueFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 1 | 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithABrokenConditionFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | + | 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutThePipeAfterTheConditionFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | i < 3 i + 1) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithABrokenUpdateFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | i < 3 | +) { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutAClosingParenthesisFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1 { }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutABodyFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1) 1").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithABrokenStatementFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1) { 1 + }").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForWithoutAClosingBraceFails() {
        Assertions.assertNull(parser("for (int32 i -> 0 | i < 3 | i + 1) { ret 1").parseStatement())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForSkipsSemicolons() {
        val loop: ForAST = Assertions.assertInstanceOf<ForAST>(
            ForAST::class.java,
            parser("for (int32 i -> 0 | i < 3 | i + 1) { ; ret 1 ; }").parseStatement()
        )

        Assertions.assertEquals("i", Objects.requireNonNull<ForAST?>(loop).varName())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEifNeedsAValidBranch() {
        Assertions.assertNull(parser("if (1) { } eif 1 { }").parseIf())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testElseNeedsABrace() {
        Assertions.assertNull(parser("if (1) { } else 1").parseIf())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfNeedsACondition() {
        Assertions.assertNull(parser("if () { }").parseIf())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfNeedsAClosingParenthesis() {
        Assertions.assertNull(parser("if (1 { }").parseIf())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfWithABrokenStatementStillBuildsTheBranch() {
        val conditional: IfAST =
            Assertions.assertInstanceOf<IfAST>(IfAST::class.java, parser("if (1) { 1 + }").parseIf())

        Assertions.assertNotNull(conditional)
        Assertions.assertEquals(1, conditional.branches().size())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfNeedsAClosingBrace() {
        Assertions.assertNull(parser("if (1) { ret 1").parseIf())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIfSkipsSemicolons() {
        Assertions.assertNotNull(parser("if (1) { ; ret 1 ; }").parseIf())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testReturnNeedsAValueThatParses() {
        Assertions.assertNull(parser("ret +").parseRet())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainLoopReachesEndOfInput() {
        Assertions.assertDoesNotThrow(org.junit.jupiter.api.function.Executable { parser(";").mainLoop() })
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMainLoopStopsAtAnArray() {
        Assertions.assertDoesNotThrow(org.junit.jupiter.api.function.Executable { parser("[int32|1|1] values").mainLoop() })
    }

    @org.junit.jupiter.api.Test
    fun testParseAllStopsOnAnUnknownTopLevelToken() {
        Assertions.assertNotNull(ZFixtures.failureOf("@"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExpressionAtTopLevelIsNotAFunction() {
        Assertions.assertNull(ZFixtures.parseAll("1 + 2"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExpressionStatementIsParsed() {
        val parser: src.Parser.Parser = parser("1 + 2")
        val statement: ExprAST? = parser.parseStatement()

        Assertions.assertNotNull(statement)
    }

    companion object {
        @Throws(IOException::class)
        private fun parser(source: String): src.Parser.Parser {
            return ZFixtures.parserFor(source)
        }
    }
}
