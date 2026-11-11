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

class ExpressionParser(parser: src.Parser.Parser) {
    private val context: ParserContext

    init {
        this.context = parser.getContext()
    }

    val tokPrecedence: Int
        get() {
            val TokPrev: Int? = src.Parser.ExpressionParser.Companion.BinOpPrecedence.get(context.getCurTok())
            if (TokPrev == null || TokPrev <= 0) return -1
            return TokPrev
        }

    /**
     * Parses an expression (general use)
     * @return null or calls other functions
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseExpression(inTailPosition: Boolean): ExprAST? {
        val leftOperandSide: ExprAST? = parsePrimary(inTailPosition)
        if (leftOperandSide == null) return null
        return parseBinOpRHS(0, leftOperandSide)
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
        var leftOperandSide: ExprAST? = leftOperandSide
        while (true) {
            val TokPrev = this.tokPrecedence

            if (TokPrev < ExprPrev) {
                return leftOperandSide
            }

            val BinOp: Int = context.getCurTok()
            context.next()

            if (BinOp == src.Lexer.Lexer.Tokens.ASSIGN.value || BinOp == src.Lexer.Lexer.Tokens.SAME.value) {
                // Right-associative: parse the whole RHS.
                val rhs: ExprAST? = parseExpression(false)
                if (rhs == null) return null

                var target: ExprAST? = leftOperandSide
                while (target is ParenExprAST) target = target.inner()

                if (target is DerefExprAST) {
                    return AssignThroughPointerExprAST(target, rhs)
                }
                if (target is VarRefExprAST) {
                    if (target.name().contains(".")) {
                        return src.Parser.Parser.Companion.error<ExprAST?>("Cannot assign to module member '" + target.name() + "'")
                    }
                    return AssignExprAST(target.name(), rhs)
                }
                return src.Parser.Parser.Companion.error<ExprAST?>("Cannot assign to this expression")
            }
            if (BinOp == src.Lexer.Lexer.Tokens.ARRAY_OPEN.value) {
                val indexes: MutableList<NumberExprAST?> =
                    java.util.ArrayList<NumberExprAST?>() //list of indexes for matrix
                while (context.getCurTok() == src.Lexer.Lexer.Tokens.ARRAY_OPEN.value) {
                    context.next()
                    if (context.getCurTok() != src.Lexer.Lexer.Tokens.NUMBER.value) {
                        src.Parser.Parser.Companion.error<Any?>("Expected index value")
                    }
                    val size: Double = context.getLex().getNumVal()
                    val idx: NumberExprAST =
                        parseNumberExpr(size) as NumberExprAST //no support for expressions when indexing
                    indexes.add(idx)
                    context.next()
                    if (context.getCurTok() != src.Lexer.Lexer.Tokens.ARRAY_CLOSE.value) {
                        return src.Parser.Parser.Companion.error<ExprAST?>("Expected ']' to close indexing")
                    }
                }
                return ArrayIdxExprAST(leftOperandSide, indexes)
            }
            var rightOperandSide: ExprAST? = parsePrimary(false)
            if (rightOperandSide == null) return null

            val NextPrev = this.tokPrecedence
            if (TokPrev < NextPrev) {
                rightOperandSide = parseBinOpRHS(TokPrev + 1, rightOperandSide)
                if (rightOperandSide == null) return null
            }

            leftOperandSide = BinaryExprAST(
                src.Lexer.Lexer.Tokens.Companion.fromValues(BinOp).description,
                leftOperandSide,
                rightOperandSide
            )
        }
    }

    /**
     * Parses a primary expression (general one)
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parsePrimary(inTailPosition: Boolean): ExprAST? {
        if (context.getCurTok() == src.Lexer.Lexer.Tokens.OPEN_FUNC.value) {
            context.next()
        }

        if (context.getCurTok() == src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
            return parseIdentifierExpr(inTailPosition)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.NUMBER.value) {
            return parseNumberExpr(context.getLex().getNumVal())
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.STRING_LITERAL.value) {
            val value: String? = context.getLex().getStringVal()
            context.next()
            return StringExprAST(value)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.ALLOC.value) {
            context.next()
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_PAR.value) {
                return src.Parser.Parser.Companion.error<ExprAST?>("Expected '(' after 'alloc'")
            }
            context.next()
            val sizeExpr: ExprAST? = parseExpression(inTailPosition)
            if (sizeExpr == null) return null
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
                return src.Parser.Parser.Companion.error<ExprAST?>("Expected ')' after alloc size")
            }
            context.next()
            return AllocExprAST(sizeExpr)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.FREE.value) {
            context.next()
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.OPEN_PAR.value) {
                return src.Parser.Parser.Companion.error<ExprAST?>("Expected '(' after 'free'")
            }
            context.next()
            val ptrExpr: ExprAST? = parseExpression(inTailPosition)
            if (ptrExpr == null) return null
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
                return src.Parser.Parser.Companion.error<ExprAST?>("Expected ')' after free argument")
            }
            context.next()
            return FreeExprAST(ptrExpr)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.TRUE.value || context.getCurTok() == src.Lexer.Lexer.Tokens.FALSE.value) {
            val value = context.getCurTok() == src.Lexer.Lexer.Tokens.TRUE.value
            context.next()
            return BoolExprAST(value)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.OPEN_PAR.value) {
            return parseParenExpr(inTailPosition)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.AND.value) {
            context.next()
            val operand: ExprAST? = parsePrimary(inTailPosition)
            if (operand == null) return null
            return AddressOfExprAST(operand)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.MUL.value) {
            context.next()
            val operand: ExprAST? = parsePrimary(inTailPosition)
            if (operand == null) return null
            return DerefExprAST(operand)
        } else if (context.getCurTok() == src.Lexer.Lexer.Tokens.SUB.value) {
            context.next()
            val operand: ExprAST? = parsePrimary(inTailPosition)
            if (operand == null) return null
            return UnaryExprAST(operand)
        } else {
            return src.Parser.Parser.Companion.error<ExprAST?>(
                "unknown token when expecting an expression: " + TypeParser.Companion.describeToken(
                    context.getCurTok()
                )
            )
        }
    }

    /**
     * Parses an identifier expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseIdentifierExpr(inTailPosition: Boolean): ExprAST? {
        val parts: MutableList<String?> = java.util.ArrayList<String?>()
        val fullName: java.lang.StringBuilder = java.lang.StringBuilder(context.getLex().getIdentifierStr())
        parts.add(context.getLex().getIdentifierStr())
        context.next()

        while (context.getCurTok() == '.'.code) {
            context.next()
            if (context.getCurTok() == src.Lexer.Lexer.Tokens.NUMBER.value) {
                fullName.append(".").append(context.getLex().getNumberText())
                context.next()
                return VarRefExprAST(fullName.toString())
            } else if (context.getCurTok() != src.Lexer.Lexer.Tokens.IDENTIFIER.value) {
                return src.Parser.Parser.Companion.error<ExprAST?>("Expected a name or number (varargs) after '.' in '" + fullName + "'")
            }
            fullName.append(".").append(context.getLex().getIdentifierStr())
            parts.add(context.getLex().getIdentifierStr())
            context.next()
        }

        // struct literal: Point{1 | 2}
        if (context.getCurTok() == src.Lexer.Lexer.Tokens.OPEN_FUNC.value) {
            if (parts.size == 1 && StructRegistry.isStruct(parts.getFirst())) {
                context.next()
                val values: MutableList<ExprAST?> = java.util.ArrayList<ExprAST?>()
                if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_FUNC.value) {
                    while (true) {
                        val `val`: ExprAST? = parseExpression(false)
                        if (`val` == null) return null
                        values.add(`val`)
                        if (context.getCurTok() == src.Lexer.Lexer.Tokens.CLOSE_FUNC.value) break
                        if (context.getCurTok() != src.Lexer.Lexer.Tokens.PARAMS.value) {
                            return src.Parser.Parser.Companion.error<ExprAST?>("Expected '|' or '}' in struct literal")
                        }
                        context.next()
                    }
                }
                context.next()
                return StructLiteralAST(parts.getFirst(), values)
            }
            // Not a struct: this `X{...}` is not valid
            return src.Parser.Parser.Companion.error<ExprAST?>("Unknown struct: " + parts.getFirst())
        }

        // function call: foo(...) or math.sqrt(...)
        if (context.getCurTok() == src.Lexer.Lexer.Tokens.OPEN_PAR.value) {
            context.next()
            val args: MutableList<ExprAST?> = java.util.ArrayList<ExprAST?>()
            if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
                while (true) {
                    val arg: ExprAST? = parseExpression(false)
                    if (arg == null) return null
                    args.add(arg)
                    if (context.getCurTok() == src.Lexer.Lexer.Tokens.CLOSE_PAR.value) break
                    if (context.getCurTok() != src.Lexer.Lexer.Tokens.PARAMS.value) {
                        return src.Parser.Parser.Companion.error<ExprAST?>("Expected ')' or '|' in argument list in: " + fullName)
                    }
                    context.next()
                }
            }
            context.next()
            return CallExprAST(fullName.toString(), args, inTailPosition)
        }

        // no dot: plain variable
        if (parts.size == 1) {
            return VarRefExprAST(parts.getFirst())
        }

        // dotted: field access chain, e.g. p.x or p.inner.x
        var current: ExprAST = VarRefExprAST(parts.getFirst())
        for (i in 1..<parts.size) {
            current = FieldAccessExprAST(current, parts.get(i))
        }
        return current
    }

    /**
     * Parses a number expression
     *
     * @param numVal double value
     * @return a number Expression
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseNumberExpr(numVal: Double): ExprAST {
        val result: ExprAST = src.Parser.ExpressionParser.Companion.literal(
            numVal,
            context.getLex().getNumberText(),
            context.getLex().getNumIsFloat()
        )
        context.next()
        return result
    }

    /**
     * Parses a parenthesis expression
     *
     * @return an Expression node or an error
     * @throws IOException if an exception is found
     */
    @Throws(IOException::class)
    fun parseParenExpr(inTailPosition: Boolean): ExprAST? {
        context.next()
        val v: ExprAST? = parseExpression(false)
        if (v == null) return null

        if (context.getCurTok() != src.Lexer.Lexer.Tokens.CLOSE_PAR.value) {
            return src.Parser.Parser.Companion.error<ExprAST?>("Expected ')'")
        }
        context.next()
        return ParenExprAST(v)
    }

    companion object {
        private val BinOpPrecedence: MutableMap<Int?, Int?>

        init {
            src.Parser.ExpressionParser.Companion.BinOpPrecedence = java.util.Map.ofEntries<Int?, Int?>(
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.LESS.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.MORE.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.LE.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.GE.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.EQ.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.NE.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.SAME.value, 10),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.ASSIGN.value, 5),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.ADD.value, 20),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.SUB.value, 20),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.MUL.value, 40),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.DIV.value, 40),
                java.util.Map.entry<Int?, Int?>(src.Lexer.Lexer.Tokens.MOD.value, 40)
            )
        }

        protected const val LONG_EXACT_LIMIT: Double = 9007199254740992.0

        /**
         *
         * @param numVal the value the context.getLex()er produced
         * @param text the literal as written, or null if there is none
         * @param floating whether the source had a decimal point or an exponent
         * @return the number node
         */
        fun literal(numVal: Double, text: String?, floating: Boolean): NumberExprAST {
            if (!floating && text != null) {
                try {
                    return NumberExprAST(text.toLong().toString(), text.toLong())
                } catch (ignored: java.lang.NumberFormatException) {
                    // nothing
                }
            }
            if (!floating && numVal == kotlin.math.round(numVal) && kotlin.math.abs(numVal) <= src.Parser.ExpressionParser.Companion.LONG_EXACT_LIMIT) {
                val asLong = numVal.toLong()
                return NumberExprAST(asLong.toString(), asLong)
            }
            return NumberExprAST(numVal.toString(), numVal)
        }
    }
}