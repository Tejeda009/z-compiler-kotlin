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

class TestMoreLexer {
    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTwoDotsStayTwoDots() {
        val lex: src.Lexer.Lexer = lexer("..x")

        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLoneDotIsADot() {
        val lex: src.Lexer.Lexer = lexer(".")

        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testThreeDotsAfterAnIdentifierStayDots() {
        val lex: src.Lexer.Lexer = lexer("x...")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLeadDotNumberAfterIdentifier() {
        val lex: src.Lexer.Lexer = lexer("x.5")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals('.'.code, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals(5.0, lex.getNumVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMalformedNumberBecomesZero() {
        val lex: src.Lexer.Lexer = lexer("1e")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NUMBER.value, lex.getTok())
        Assertions.assertEquals("1e", lex.getNumberText())
        Assertions.assertEquals(0.0, lex.getNumVal())
        Assertions.assertTrue(lex.getNumIsFloat())
        Assertions.assertFalse(lex.getNumIsInteger())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testAllStringEscapes() {
        val lex: src.Lexer.Lexer = lexer("\"\\r\\0\\q\"")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.STRING_LITERAL.value, lex.getTok())
        Assertions.assertEquals("\r\u0000q", lex.getStringVal())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testBareNotIsItsAsciiCode() {
        val lex: src.Lexer.Lexer = lexer("!")

        Assertions.assertEquals('!'.code, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testNotEqual() {
        val lex: src.Lexer.Lexer = lexer("!=")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.NE.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testEqualAndOrderingOperators() {
        val lex: src.Lexer.Lexer = lexer("== <= >= < > =")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EQ.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.LE.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.GE.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.LESS.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.MORE.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.SAME.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMinusAtEndOfInputIsSubtraction() {
        val lex: src.Lexer.Lexer = lexer("-")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.SUB.value, lex.getTok())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.EOF.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testCarriageReturnEndsALineComment() {
        val lex: src.Lexer.Lexer = lexer("// comment\rret")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.RET.value, lex.getTok())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testIdentifierLookalikesAreNotKeywords() {
        val lex: src.Lexer.Lexer = lexer("procx int32x eofx numbers")

        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("procx", lex.getIdentifierStr())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("int32x", lex.getIdentifierStr())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("eofx", lex.getIdentifierStr())
        Assertions.assertEquals(src.Lexer.Lexer.Tokens.IDENTIFIER.value, lex.getTok())
        Assertions.assertEquals("numbers", lex.getIdentifierStr())
    }

    companion object {
        private fun lexer(source: String): src.Lexer.Lexer {
            return src.Lexer.Lexer(ByteArrayInputStream(source.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
        }
    }
}
