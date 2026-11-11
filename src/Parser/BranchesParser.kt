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

class BranchesParser(parser: src.Parser.Parser) {
    private val context: ParserContext
    private val expressionParser: src.Parser.ExpressionParser
    private val parser: src.Parser.Parser

    init {
        this.parser = parser
        this.context = parser.getContext()
        this.expressionParser = parser.getExpressionParser()
    }

    /**
     * Parses a conditional: `if (cond) { } eif (cond) { } else { }`.
     *
     * @return an IfAST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseIf(): IfAST? {
        context.next() // skip 'if'

        val branches: MutableList<src.AST.IfAST.Branch?> = java.util.ArrayList<src.AST.IfAST.Branch?>()
        var elseBody: ExprAST? = null

        val first: src.AST.IfAST.Branch? = parseIfBranch("if")
        if (first == null) return null
        branches.add(first)

        while (src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok()) == src.Lexer.Lexer.Tokens.EIF) {
            context.next() // skip 'eif'
            val branch: src.AST.IfAST.Branch? = parseIfBranch("eif")
            if (branch == null) return null
            branches.add(branch)
        }

        if (src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok()) == src.Lexer.Lexer.Tokens.ELSE) {
            context.next() // skip 'else'
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_FUNC.value) {
                return src.Parser.Parser.Companion.error<IfAST?>("Expected '{' after 'else'")
            }
            context.next()
            val stmts: MutableList<ExprAST?>? = parseBlockBody()
            if (stmts == null) return null
            elseBody = BlockAST(stmts)
        }

        return IfAST(branches, elseBody)
    }

    /**
     * Parses one `(cond) { body }` pair, shared by `if` and `eif`.
     */
    @Throws(IOException::class)
    fun parseIfBranch(keyword: String?): src.AST.IfAST.Branch? {
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_PAR.value) {
            return src.Parser.Parser.Companion.error<src.AST.IfAST.Branch?>("Expected '(' after '" + keyword + "'")
        }
        context.next()
        val cond: ExprAST? = expressionParser.parseExpression(false)
        if (cond == null) return null
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
            return src.Parser.Parser.Companion.error<src.AST.IfAST.Branch?>("Expected ')' after condition in " + keyword)
        }
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_FUNC.value) {
            return src.Parser.Parser.Companion.error<src.AST.IfAST.Branch?>("Expected '{' after condition in " + keyword)
        }
        context.next()
        val stmts: MutableList<ExprAST?>? = parseBlockBody()
        if (stmts == null) return null
        val body: ExprAST = BlockAST(stmts)
        return src.AST.IfAST.Branch(cond, body)
    }

    /**
     * Parses `stmt*` and consumes the closing `'`'}'.
     *
     * @return the statements, or null on error
     */
    @Throws(IOException::class)
    fun parseBlockBody(): MutableList<ExprAST?>? {
        val stmts: MutableList<ExprAST?> = java.util.ArrayList<ExprAST?>()
        while (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_FUNC.value && context.getCurTok() != src.Lexer.Lexer.Tokens.EOF.value) {
            if (context.getCurTok() == ';'.code) {
                context.next()
                continue
            }
            val s: ExprAST? = parser.parseStatement()
            if (s == null) return kotlin.collections.mutableListOf<ExprAST?>()
            stmts.add(s)
        }
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_FUNC.value) {
            return src.Parser.Parser.Companion.error<MutableList<ExprAST?>?>("Expected '}' at the end of the block")
        }
        context.next()
        return stmts
    }
}
