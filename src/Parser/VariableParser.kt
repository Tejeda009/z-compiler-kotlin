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

class VariableParser(parser: src.Parser.Parser) {
    private val context: ParserContext
    private val typeParser: TypeParser
    private val parseExpression: src.Parser.ExpressionParser

    init {
        this.context = parser.getContext()
        this.typeParser = parser.getParserType()
        this.parseExpression = parser.getExpressionParser()
    }

    /**
     * Parses a definition and assignment of a constant
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseConstant(): DeclaratorExprAST? {
        context.next()

        val zType: TypeRef? = typeParser.parseType()
        if (zType == null) {
            return src.Parser.Parser.Companion.error<DeclaratorExprAST?>("Expected type after 'cn'")
        }

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
            return src.Parser.Parser.Companion.error<DeclaratorExprAST?>("Expected constant name")
        }
        val name: String? = context.getLex().getIdentifierStr()
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.ASSIGN.value && context.getCurTok() != src.Lexer.Lexer.Tokens.SAME.value) {
            return src.Parser.Parser.Companion.error<DeclaratorExprAST?>("Expected '->' or '=' in constant declaration")
        }
        context.next()

        val value: ExprAST? = parseExpression.parseExpression(false)
        if (value == null) return null

        return DeclaratorExprAST(zType, name, value, true, false)
    }

    /**
     * Parses a variable
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseVariable(globals: Boolean): DeclaratorExprAST? {
        val zType: TypeRef? = typeParser.parseType()
        if (zType == null) return null

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
            return src.Parser.Parser.Companion.error<DeclaratorExprAST?>("Expected name after type")
        }
        val name: String? = context.getLex().getIdentifierStr()
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.ASSIGN.value && context.getCurTok() != src.Lexer.Lexer.Tokens.SAME.value) {
            return DeclaratorExprAST(zType, name, null, false, globals)
        }
        context.next()
        val `val`: ExprAST? = parseExpression.parseExpression(false)
        if (`val` == null) return null
        return DeclaratorExprAST(zType, name, `val`, false, globals)
    }
}
