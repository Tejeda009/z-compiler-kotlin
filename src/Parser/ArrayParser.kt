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

class ArrayParser(parser: src.Parser.Parser) {
    private val context: ParserContext
    private val expressionParser: src.Parser.ExpressionParser

    init {
        this.context = parser.getContext()
        this.expressionParser = parser.getExpressionParser()
    }

    @Throws(IOException::class)
    fun parseArray(): ArrayAST? {
        //context.next(); (I believe this isn't necessary here)
        //Array declaration does NOT support expressions inside of it yet. you can only declare with a number
        val typeTok: src.Lexer.Lexer.Tokens? = src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok())
        if (!TypeParser.Companion.isTypeToken(typeTok)) {
            return src.Parser.Parser.Companion.error<ArrayAST?>("Expected type after array definition")
        }
        val zType: ZType? = TypeParser.Companion.resolveType(typeTok.description + "*") //it will resolve it as a ptr
        if (zType == null) return null
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.PARAMS.value) {
            return src.Parser.Parser.Companion.error<ArrayAST?>("Expected '|' after type definition")
        }
        context.next()
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.NUMBER.value) {
            src.Parser.Parser.Companion.error<Any?>("Expected number")
        }
        val size: Double = context.getLex().getNumVal()
        val arraySize: NumberExprAST? = expressionParser.parseNumberExpr(size) as NumberExprAST?
        var arrayDepth: NumberExprAST? = expressionParser.parseNumberExpr(1.0) as NumberExprAST?
        context.next()

        if (context.getCurTok() == src.Lexer.Lexer.Tokens.PARAMS.value) {
            context.next()
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.NUMBER.value) {
                src.Parser.Parser.Companion.error<Any?>("Expected number")
            }
            val depth: Double = context.getLex().getNumVal()
            if (size < 1) {
                return src.Parser.Parser.Companion.error<ArrayAST?>("Invalid depth")
            }
            arrayDepth =
                expressionParser.parseNumberExpr(depth) as NumberExprAST? //for matrixes, depth 2 means [][], 1 is default
            context.next()
        } else if (context.getCurTok() != src.Lexer.Lexer.Tokens.ARRAY_CLOSE.value) {
            return src.Parser.Parser.Companion.error<ArrayAST?>("Expected closing bracket")
        }
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
            return src.Parser.Parser.Companion.error<ArrayAST?>("Expected identifier name")
        }
        val name: String? = context.getLex().getIdentifierStr()
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_FUNC.value) {
            return ArrayAST(zType, arraySize, arrayDepth, name, java.util.ArrayList<NumberExprAST?>())
        }
        context.next()

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.ASSIGN.value && context.getCurTok() != src.Lexer.Lexer.Tokens.SAME.value) {
            return src.Parser.Parser.Companion.error<ArrayAST?>("Expected '->' or '=' in constant declaration")
        }
        context.next()

        val members: MutableList<NumberExprAST?> = java.util.ArrayList<NumberExprAST?>(size.toInt())

        for (i in 0..<size.toInt()) {
            val valAST: NumberExprAST? = expressionParser.parseNumberExpr(
                context.getLex().getNumVal()
            ) as NumberExprAST? //qua dentro fa già un next
            members.add(valAST)
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.PARAMS.value && context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_FUNC.value) {
                return src.Parser.Parser.Companion.error<ArrayAST?>("Expected '|' after type definition")
            } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.CLOSE_FUNC.value) {
                break
            }
            context.next()
        }

        return ArrayAST(zType, arraySize, arrayDepth, name, members)
    }
}
