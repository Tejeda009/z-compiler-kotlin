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

class HeaderParser(parser: src.Parser.Parser) {
    private val context: ParserContext
    private val typeParser: TypeParser

    init {
        this.context = parser.getContext()
        this.typeParser = parser.getParserType()
    }

    @Throws(IOException::class)
    fun parseImport(): ImportAST? {
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.IMPORT.value) {
            return null
        }

        context.next() // consume 'import'

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
            return src.Parser.Parser.Companion.error<ImportAST?>("Expected module name after 'import'")
        }

        val moduleName: String? = context.getLex().getIdentifierStr()
        context.next()

        return ImportAST(moduleName)
    }

    @Throws(IOException::class)
    fun parseExtern(): ExternExprAST? {
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.EXTERN.value) {
            return null
        }
        context.next() // consume 'extern'

        val proto: PrototypeAST? = parsePrototype(true)
        if (proto == null) return null

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.ASSIGN.value) {
            return src.Parser.Parser.Companion.error<ExternExprAST?>("Missing return value for extern function: " + proto.name)
        }

        context.next()
        val token: src.Lexer.Lexer.Tokens? = src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok())
        if (!TypeParser.Companion.isTypeToken(token)) {
            return src.Parser.Parser.Companion.error<ExternExprAST?>("Invalid return type for extern function: " + proto.name)
        }
        val returnType: ZType? = TypeParser.Companion.resolveType(token.description)
        if (returnType == null) return null
        context.next()

        return ExternExprAST(proto, token.description)
    }

    /**
     * Parses a function prototype
     * @return an error or a PrototypeAST node
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parsePrototype(extern: Boolean): PrototypeAST? {
        var isVarArg = false

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
            return src.Parser.Parser.Companion.error<PrototypeAST?>(
                "Expected function name in prototype, got " + TypeParser.Companion.describeToken(
                    context.getCurTok()
                ) + " instead"
            )
        }
        val fnName: String? = context.getLex().getIdentifierStr()
        context.next()

        // Expect '('
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_PAR.value) {
            return src.Parser.Parser.Companion.error<PrototypeAST?>(
                "Expected '(' in prototype, got " + TypeParser.Companion.describeToken(
                    context.getCurTok()
                ) + " instead"
            )
        }
        context.next()

        val params: MutableList<src.AST.PrototypeAST.Param?> = java.util.ArrayList<src.AST.PrototypeAST.Param?>()
        if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
            while (true) {
                var isMut = false
                if (context.getCurTok() == src.Lexer.Lexer.Tokens.MUT.value) {
                    isMut = true
                    context.next()
                }
                var paramType: TypeRef? = typeParser.parseType()
                if (paramType == null) {
                    return src.Parser.Parser.Companion.error<PrototypeAST?>("Expected parameter type for function: " + fnName)
                }
                if (extern && paramType.isVararg() && context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
                    return src.Parser.Parser.Companion.error<PrototypeAST?>("'...' must be the last parameter of extern function: " + fnName)
                }

                if (extern) {
                    params.add(src.AST.PrototypeAST.Param(paramType.zName(), "", false, isMut))
                } else {
                    if (paramType.isVararg()) {
                        paramType = typeParser.parseType()
                        if (paramType == null) {
                            return src.Parser.Parser.Companion.error<PrototypeAST?>("Expected a type after '...' in function: " + fnName)
                        }
                        isVarArg = true
                    }
                    if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
                        return src.Parser.Parser.Companion.error<PrototypeAST?>("Expected parameter name")
                    }
                    val paramName: String? = context.getLex().getIdentifierStr()
                    context.next()
                    params.add(src.AST.PrototypeAST.Param(paramType.zName(), paramName, isVarArg, isMut))
                }

                if (isVarArg && context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
                    return src.Parser.Parser.Companion.error<PrototypeAST?>("'...' must be the last parameter of function: " + fnName)
                }

                if (context.getCurTok() == src.Lexer.Lexer.Tokens.PARAMS.value) {
                    context.next()
                    continue
                }
                if (context.getCurTok() == src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
                    break
                }
                return src.Parser.Parser.Companion.error<PrototypeAST?>("Expected '|' or ')' in parameter list of function: " + fnName)
            }
        }

        context.next()

        var returnType: TypeRef? = null
        if (!extern && context.getCurTok() == src.Lexer.Lexer.Tokens.ASSIGN.value) {
            context.next()
            val retTok: src.Lexer.Lexer.Tokens? = src.Lexer.Lexer.Tokens.Companion.fromValues(context.getCurTok())
            val structName = retTok == src.Lexer.Lexer.Tokens.IDENTIFIER
                    && StructRegistry.isStruct(context.getLex().getIdentifierStr())
            if (!TypeParser.Companion.isTypeToken(retTok) && !structName) {
                return src.Parser.Parser.Companion.error<PrototypeAST?>(
                    "Expected return type after '->', got " + TypeParser.Companion.describeToken(
                        context.getCurTok()
                    ) + " instead"
                )
            }
            returnType = typeParser.parseType()
            if (returnType == null) {
                return src.Parser.Parser.Companion.error<PrototypeAST?>(
                    "Expected return type after '->', got " + TypeParser.Companion.describeToken(
                        context.getCurTok()
                    ) + " instead"
                )
            }
            context.next()
        }

        return PrototypeAST(fnName, params, returnType)
    }
}
