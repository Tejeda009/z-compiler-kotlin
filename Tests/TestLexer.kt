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

class TestLexer {
    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testProcDeclarationTokens() {
        // for now (int64 fails because it gets treated as one string
        val code = "proc name ( int64 name | int64 name) { \n ret 281 \n }"

        val lex: src.Lexer.Lexer = src.Lexer.Lexer(ByteArrayInputStream(code.getBytes()))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT64.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.PARAMS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT64.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_FUNC.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConditionalTokens() {
        // for now (int64 fails because it gets treated as one string
        val code = "if name > 64 { \n\n } eif name < 32 { \n \n } else { \n \n }"

        val lex: src.Lexer.Lexer = src.Lexer.Lexer(ByteArrayInputStream(code.getBytes()))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IF.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.MORE.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EIF.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.LESS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ELSE.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_FUNC.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_FUNC.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAssignmentOperator() {
        val code = "->"

        val lex: src.Lexer.Lexer = src.Lexer.Lexer(ByteArrayInputStream(code.getBytes()))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ASSIGN.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSlashComments() {
        val code = "// this is a comment\nret"

        val lex: src.Lexer.Lexer = src.Lexer.Lexer(ByteArrayInputStream(code.getBytes()))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testConstantToken() {
        val code = "cn int64 PI -> 314"
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(ByteArrayInputStream(code.getBytes()))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT64.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("PI", lex.getIdentifierStr())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ASSIGN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(314.0, lex.getNumVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testImport() {
        val code = "import libc"
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IMPORT.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("libc", lex.getIdentifierStr())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVariableAssignment() {
        val code = "int32 var -> 30"
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT32.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ASSIGN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExtern() {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream("extern test(int32)".getBytes(java.nio.charset.StandardCharsets.UTF_8)))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EXTERN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT32.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_PAR.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIntegerLiteralKeepsItsText() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "9007199254740993".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals("9007199254740993", lex.getNumberText())
        Assertions.assertTrue(lex.getNumIsInteger())
        Assertions.assertFalse(lex.getNumIsFloat())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testFloatLiteralIsMarkedAsFloating() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "3.14".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals("3.14", lex.getNumberText())
        Assertions.assertTrue(lex.getNumIsFloat())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testExponentIsAFloatLiteral() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "1e3".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals("1e3", lex.getNumberText())
        Assertions.assertTrue(lex.getNumIsFloat())
        Assertions.assertEquals(1000.0, lex.getNumVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSubtractionIsNotAnAssignment() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "- 5".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.SUB.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEndOfInput() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "ret".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTypeKeywords() {
        val code = "int32 int64 flt32 flt64"

        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT32.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT64.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.FLOAT32.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.FLOAT64.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testForKeywordAndItsSymbols() {
        val code = "for (int32 i -> 0 | i < 10 | i + 1) {"

        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(code.getBytes(java.nio.charset.StandardCharsets.UTF_8)))

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.FOR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT32.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("i", lex.getIdentifierStr())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ASSIGN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.PARAMS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.LESS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.PARAMS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ADD.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_FUNC.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testDivSymbol() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "7 / 2".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.DIV.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIdentifierKeepsItsName() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "myVar42".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("myVar42", lex.getIdentifierStr())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCommentAtEndOfInput() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "ret // trailing".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMultiLineComment() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "/* first line\n * second line\n */ ret".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMultiLineCommentBetweenTokens() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "ret /* a * b / c **/ 281".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(281.0, lex.getNumVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnterminatedMultiLineComment() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "ret /* never closed *".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testSlashIsStillDivision() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "a / b".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.DIV.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBreakAndContinueTokens() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "break continue".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.BREAK.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CONTINUE.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnknownCharacterIsItsAsciiCode() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "@".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals('@'.code, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringLiteral() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "\"hello\"".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.STRING_LITERAL.value, lex.getTok())
        Assertions.assertEquals("hello", lex.getStringVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testStringLiteralEscapes() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "\"a\\nb\\t\\\\\\\"c\"".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.STRING_LITERAL.value, lex.getTok())
        Assertions.assertEquals("a\nb\t\\\"c", lex.getStringVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnterminatedStringLiteral() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "\"hello".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAmpersandToken() {
        val lex: src.Lexer.Lexer = src.Lexer.Lexer(
            ByteArrayInputStream(
                "&x".getBytes(java.nio.charset.StandardCharsets.UTF_8)
            )
        )
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.AND.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testVarargs() {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream("extern printf(ptr | ...) -> int32".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EXTERN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.OPEN_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.PTR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.PARAMS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.VARARG.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.CLOSE_PAR.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.ASSIGN.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.INT32.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testModuleAccessDotsAreNotSwallowed() {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream("library.math".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("library", lex.getIdentifierStr())
        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("math", lex.getIdentifierStr())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLeadingDotNumberStillWorks() {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream(".5".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(0.5, lex.getNumVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMutToken() {
        val lex: src.Lexer.Lexer =
            src.Lexer.Lexer(ByteArrayInputStream("mut".getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.MUT.value, lex.getTok())
    }
}