package src.AST

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
 *
 *
 */
class CallExprAST : ExprAST {
    private val callee: String
    private val arguments: MutableList<ExprAST?>
    private val inTailPosition: Boolean

    constructor(callee: String, arguments: MutableList<ExprAST?>) {
        this.callee = callee
        this.arguments = java.util.List.copyOf<ExprAST?>(arguments)
        this.inTailPosition = false
    }

    constructor(callee: String, arguments: MutableList<ExprAST?>, inTailPosition: Boolean) {
        this.callee = callee
        this.arguments = java.util.List.copyOf<ExprAST?>(arguments)
        this.inTailPosition = inTailPosition
    }

    /** @return the name being called
     */
    fun callee(): String? {
        return callee
    }

    /** @return the arguments, in order
     */
    fun arguments(): MutableList<ExprAST?> {
        return arguments
    }

    override fun type(builder: IRBuilder): TypeRef {
        val resolved: ResolvedSignature? = builder.resolve(callee)
        if (resolved == null) return ZType.FLT64
        if (resolved.externReturnType != null) return resolved.externReturnType
        val signature: PrototypeAST = resolved.prototype
        if (signature.returnType != null) return signature.returnType
        if (signature.isMain()) return ZType.INT32
        return ZType.FLT64
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        val resolved: ResolvedSignature? = builder.resolve(callee)
        if (resolved == null) {
            logger.error("Error: unknown function: {}", callee)
            builder.markFailed()
            return null
        }
        val signature: PrototypeAST = resolved.prototype

        val params: MutableList<src.AST.PrototypeAST.Param?> = signature.params

        val variadic = !params.isEmpty() && (params.getLast().varArg || params.getLast().type == ZType.VARARG.zName())
        val fixedParams = if (variadic) params.size - 1 else params.size

        if (if (variadic) arguments.size < fixedParams else arguments.size != fixedParams) {
            logger.error("Error: ''{}'' takes {} argument(s) but {} were given", callee, fixedParams, arguments.size)
            builder.markFailed()
            return null
        }

        for (i in 0..<fixedParams) {
            if (params.get(i).mut) {
                val targetI = getPointerTarget(arguments.get(i), builder)
                if (targetI != null) {
                    for (j in 0..<fixedParams) {
                        if (i != j) {
                            val targetJ = getPointerTarget(arguments.get(j), builder)
                            if (targetI == targetJ) {
                                logger.error(
                                    "Error: alias detected: '{}' cannot be passed multiple times when an argument is marked 'mut'",
                                    targetI
                                )
                                builder.markFailed()
                                return null
                            }
                        }
                    }
                }
            }
        }

        val printed: MutableList<String?> = java.util.ArrayList<String?>()
        val externReturn: ZType? = resolved.externReturnType
        for (i in arguments.indices) {
            val argument: LLVMTextUnit? = arguments.get(i).codeGen(builder)
            if (argument == null) {
                builder.markFailed()
                return null
            }
            val expected: TypeRef
            if (i < fixedParams) {
                expected = resolveParamType(params.get(i).type)
            } else if (params.getLast().varArg) {
                expected = resolveParamType(params.getLast().type)
            } else {
                val argType: TypeRef? = argument.type
                if (argType !is ZType) {
                    logger.error("Error: struct argument to a variadic function")
                    builder.markFailed()
                    return null
                }
                expected = when (argType) {
                    ZType.FLT32 -> ZType.FLT64
                    ZType.BOOL -> ZType.INT32
                    else -> argType
                }
            }
            val fitted: LLVMTextUnit? = coerce(builder, argument, expected)
            if (fitted == null) return null

            // Extern (C) functions take the raw bytes, not the fat struct.
            // `printf("%s" | s)` passes s.ptr, `printf`.
            if (externReturn != null && fitted.type.isString()) {
                val ptr: String = builder.nextRegister()
                builder.appendLine(ptr + " = extractvalue { i8*, i64 } " + fitted.text + ", 0")
                printed.add("i8* " + ptr)
            } else {
                printed.add(expected.llvm() + " " + fitted.text)
            }
        }

        val result: TypeRef
        if (externReturn != null) {
            result = externReturn
        } else if (signature.returnType != null) {
            result = signature.returnType
        } else if (signature.isMain()) {
            result = ZType.INT32
        } else {
            result = ZType.FLT64
        }

        val call: java.lang.StringBuilder =
            java.lang.StringBuilder(if (inTailPosition) "tail call " else "call ").append(result.llvm())
        if (variadic) {
            call.append(" (")
            for (i in 0..<fixedParams) {
                if (i > 0) call.append(", ")
                // The type printed for a fixed argument is the one the
                // argument was actually coerced to (e.g. i8* for a fat
                // str that printf unwraps), not the prototype's type.
                call.append(printed.get(i).split(" ".toRegex(), limit = 2).toTypedArray()[0])
            }
            call.append(if (fixedParams > 0) ", ...)" else "...)")
        }
        call.append(" @").append(resolved.irName).append("(").append(java.lang.String.join(", ", printed)).append(")")
        if (result.isVoid()) {
            builder.appendLine(call.toString())
            return null
        }
        val register: String = builder.nextRegister()
        builder.appendLine(register + " = " + call)
        builder.setRegisterType(register, result.llvm())
        return LLVMTextUnit(register, result)
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(CallExprAST::class.java)

        /**
         * Resolves a parameter type from the prototype.
         *
         * @param name a type name as written in the prototype
         * @return the built-in type or the struct it names
         */
        private fun resolveParamType(name: String?): TypeRef {
            try {
                return ZType.Companion.fromZName(name)
            } catch (e: java.lang.IllegalArgumentException) {
                val st: StructType = StructRegistry.lookup(name)
                if (st == null) throw e
                return st
            }
        }

        private fun getPointerTarget(expr: ExprAST, builder: IRBuilder): String? {
            if (expr is AddressOfExprAST && expr.operand() is VarRefExprAST) {
                return `var`.name()
            }
            if (expr is VarRefExprAST && expr.type(builder).isPointer()) {
                return expr.name()
            }
            return null
        }
    }
}
