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

/**
 */
class Parser(lex: src.Lexer.Lexer) {
    private val context: ParserContext
    private val expressionParser: src.Parser.ExpressionParser
    private val typeParser: TypeParser
    private val headerParser: src.Parser.HeaderParser
    private val arrayParser: src.Parser.ArrayParser
    private val branchesParser: BranchesParser
    private val loopParser: LoopParser
    private val retParser: RetParser
    private val structParser: StructParser
    private val variableParser: VariableParser
    private val definitionParser: DefinitionParser

    fun getDefinitionParser(): DefinitionParser {
        return definitionParser
    }

    fun getArrayParser(): src.Parser.ArrayParser {
        return arrayParser
    }

    fun getContext(): ParserContext {
        return context
    }

    val parserType: TypeParser
        get() = typeParser

    fun getHeaderParser(): src.Parser.HeaderParser {
        return headerParser
    }

    fun getExpressionParser(): src.Parser.ExpressionParser {
        return expressionParser
    }

    fun getBranchesParser(): BranchesParser {
        return branchesParser
    }

    fun getLoopParser(): LoopParser {
        return loopParser
    }

    fun getRetParser(): RetParser {
        return retParser
    }

    fun getStructParser(): StructParser {
        return structParser
    }

    fun getVariableParser(): VariableParser {
        return variableParser
    }

    /**
     * Constructor for Parser
     * @param lex the lexer object
     * @throws IOException if an exception is found
     */
    init {
        this.context = ParserContext(lex)
        this.expressionParser = src.Parser.ExpressionParser(this)
        this.typeParser = TypeParser(this)
        this.headerParser = src.Parser.HeaderParser(this)
        this.arrayParser = src.Parser.ArrayParser(this)
        this.branchesParser = BranchesParser(this)
        this.loopParser = LoopParser(this)
        this.retParser = RetParser(this)
        this.structParser = StructParser(this)
        this.variableParser = VariableParser(this)
        this.definitionParser = DefinitionParser(this)
    }

    /**
     * @return whether the parser is positioned on the end of the input
     */
    fun atEOF(): Boolean {
        return context.getCurTok() == src.Lexer.Lexer.Tokens.EOF.value
    }


    /**
     * mainLoop method to parse the input
     * @throws IOException if an exception is found
     */
    @Deprecated("")
    @Throws(IOException::class)
    fun mainLoop() {
        while (true) {
            val token: src.Lexer.Lexer.Tokens? = src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok())

            if (token == src.Lexer.Lexer.Tokens.EOF) {
                return
            }
            if (context.getCurTok() == ';'.code) {
                context.next()
                continue
            }

            var parsed: ExprAST? = null
            if (token == src.Lexer.Lexer.Tokens.FUNC) {
                parsed = parseDefinition()
            } else if (token == src.Lexer.Lexer.Tokens.CN) {
                parsed = parseConstant()
            } else if (token == src.Lexer.Lexer.Tokens.ARRAY_OPEN) {
                parsed = arrayParser.parseArray()
            } else if (TypeParser.Companion.isTypeToken(token)) {
                parsed = parseVariable(false)
            } else if (token == src.Lexer.Lexer.Tokens.EXTERN) {
                parsed = headerParser.parseExtern()
            } else {
                src.Parser.Parser.Companion.error<Any?>("Unknown token in main loop: " + context.getCurTok())
                context.next()
                continue
            }

            if (parsed == null) {
                return
            }
        }
    }

    /**
     * Parses a function definition
     *
     * @return null or a FunctionAST node
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseDefinition(): FunctionAST? {
        return definitionParser.parseDefinition()
    }


    /**
     * Parses a variable
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseVariable(globals: Boolean): DeclaratorExprAST? {
        return variableParser.parseVariable(globals)
    }

    /**
     * Parses a definition and assignment of a constant
     *
     * @return an AST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseConstant(): DeclaratorExprAST? {
        return variableParser.parseConstant()
    }

    /**
     * Parses a struct declaration:
     * `struct Point { int32 x | int32 y }`.
     *
     * @return a StructDeclAST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseStructDecl(): StructDeclAST? {
        return structParser.parseStructDecl()
    }


    /**
     * Parses a number expression
     *
     * @param numVal double value
     * @return a number Expression
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseNumberExpr(numVal: Double): ExprAST? {
        return expressionParser.parseNumberExpr(numVal)
    }


    /**
     * Parses a parenthesis expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseParenExpr(): ExprAST? {
        return expressionParser.parseParenExpr(false)
    }

    /**
     * Parses an identifier expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseIdentifierExpr(): ExprAST? {
        return expressionParser.parseIdentifierExpr(false)
    }

    /**
     * Parses a primary expression (general one)
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parsePrimary(): ExprAST? {
        return expressionParser.parsePrimary(false)
    }


    /**
     * Parses a Ret instruction
     *
     * @return an RetAST node
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseRet(): ExprAST? {
        return retParser.parseRet()
    }

    /**
     * Parses `stmt*` and consumes the closing `'`'}'.
     *
     * @return the statements, or null on error
     */
    @Throws(IOException::class)
    private fun parseBlockBody(): MutableList<ExprAST?>? {
        return branchesParser.parseBlockBody()
    }

    /**
     * Parses one `(cond) { body }` pair, shared by `if` and `eif`.
     */
    @Throws(IOException::class)
    fun parseIfBranch(keyword: String?): src.AST.IfAST.Branch? {
        return branchesParser.parseIfBranch(keyword)
    }

    /**
     * Parses a conditional: `if (cond) { } eif (cond) { } else { }`.
     *
     * @return an IfAST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseIf(): IfAST? {
        return branchesParser.parseIf()
    }

    /**
     * Parses a for loop: `for (type name -> init | cond | op) { body }`
     *
     * @return a ForAST node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseFor(): ForAST? {
        return loopParser.parseFor()
    }


    /**
     * Parses a statement
     * @return a node, in reality it just calls other functions
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseStatement(): ExprAST? {
        val token: src.Lexer.Lexer.Tokens? = src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok())
        if (token == src.Lexer.Lexer.Tokens.RET) {
            return parseRet()
        } else if (token == src.Lexer.Lexer.Tokens.IF) {
            return parseIf()
        } else if (token == src.Lexer.Lexer.Tokens.FOR) {
            return parseFor()
        } else if (token == src.Lexer.Lexer.Tokens.BREAK || token == src.Lexer.Lexer.Tokens.CONTINUE) {
            context.next()
            return LoopJumpAST(token == src.Lexer.Lexer.Tokens.BREAK)
        } else if (TypeParser.Companion.isTypeToken(token) || (token == src.Lexer.Lexer.Tokens.IDENTIFIER && StructRegistry.isStruct(
                context.getLex().getIdentifierStr()
            ))
        ) {
            return parseVariable(false)
        } else if (token == src.Lexer.Lexer.Tokens.CN) {
            return parseConstant()
        } else if (token == src.Lexer.Lexer.Tokens.ARRAY_OPEN) {
            return arrayParser.parseArray()
        } else {
            return parseExpression()
        }
    }

    /**
     * Parses an expression (general use)
     * @return null or calls other functions
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseExpression(): ExprAST? {
        return expressionParser.parseExpression(false)
    }

    /**
     * Parses a binary operation
     * @param ExprPrev precedence of the operator
     * @param leftOperandSide left side
     * @return null or a BinaryExprAST node
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseBinOpRHS(ExprPrev: Int, leftOperandSide: ExprAST?): ExprAST? {
        return expressionParser.parseBinOpRHS(ExprPrev, leftOperandSide)
    }


    @Throws(IOException::class)
    fun parseImport(): ImportAST? {
        return headerParser.parseImport()
    }

    @Throws(IOException::class)
    fun parseExtern(): ExternExprAST? {
        return headerParser.parseExtern()
    }

    /**
     * Parses a function prototype
     * @return an error or a PrototypeAST node
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parsePrototype(extern: Boolean): PrototypeAST? {
        return headerParser.parsePrototype(extern)
    }

    /**
     * Parses a type: a type keyword, optionally followed by `*` for a pointer.
     *
     * @return the type, or null when the current token is not a type
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseType(varargs: Boolean): TypeRef? {
        return typeParser.parseType()
    }

    /**
     * @return whether the parser is positioned on a type keyword
     */
    fun atTypeDeclaration(): Boolean {
        return typeParser.atTypeDeclaration()
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(src.Parser.Parser::class.java)

        /**
         * @param message what was expected
         * @return null
         */
        fun <T> error(message: String): T? {
            if (src.Parser.Parser.Companion.logger.isErrorEnabled()) {
                src.Parser.Parser.Companion.logger.error("Error: {}", message)
            }
            return null
        }

        /**
         *
         * @param numVal the value the context.getLex()er produced
         * @param text the literal as written, or null if there is none
         * @param floating whether the source had a decimal point or an exponent
         * @return the number node
         */
        private fun literal(numVal: Double, text: String?, floating: Boolean): NumberExprAST {
            return src.Parser.ExpressionParser.Companion.literal(numVal, text, floating)
        }
    }
}
