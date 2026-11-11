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

class FunctionAST(proto: PrototypeAST, body: ExprAST?) : ExprAST() {
    private val proto: PrototypeAST
    private val body: ExprAST?

    init {
        this.proto = proto
        this.body = body
    }

    /** @return the signature
     */
    fun prototype(): PrototypeAST? {
        return proto
    }

    /** @return the declared return type, or the historical default
     */
    fun returnType(): TypeRef {
        if (proto.returnType != null) {
            return proto.returnType
        }
        return if (proto.isMain()) ZType.INT32 else ZType.FLT64
    }

    override fun type(builder: IRBuilder?): ZType {
        return ZType.VOID
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        builder.reset()
        builder.symbols().enterScope()
        builder.declare(proto)
        builder.setReturnType(returnType())

        val isMain: Boolean = proto.isMain()
        val params: MutableList<src.AST.PrototypeAST.Param> = proto.params

        val parameters: java.lang.StringBuilder = java.lang.StringBuilder()
        for (i in params.indices) {
            if (i > 0) parameters.append(", ")
            val param: src.AST.PrototypeAST.Param = params.get(i)
            if (param.varArg) parameters.append("...")
            else {
                val type: String = resolveParamType(param.type).llvm()
                var sb: String? = type
                if (type.contains("ptr") && param.mut) {
                    sb += " noalias"
                }
                sb += " %" + param.name
                parameters.append(sb)
            }
        }

        var parametersAreDistinct = true
        var hasVarArg = false
        for (param in params) {
            val type: TypeRef = resolveParamType(param.type)
            var slot = ""
            if (!param.varArg) {
                slot = builder.emitAlloca(param.name + ".addr", type.llvm())
                builder.emitStore("%" + param.name, type.llvm(), slot)
            }
            parametersAreDistinct = parametersAreDistinct and builder.symbols()
                .define(param.name, SymbolInfo(param.name, type, slot, false, param.varArg))
            hasVarArg = hasVarArg or param.varArg
        }
        if (!parametersAreDistinct) {
            logger.error("Error: '{}' declares the same parameter twice", proto.name)
            builder.markFailed()
            builder.symbols().exitScope()
            return null
        }

        if (hasVarArg) builder.beginVarArgFunction()

        if (body != null) body.codeGen(builder)

        if (builder.hasFailed()) {
            builder.symbols().exitScope()
            return null
        }

        if (!builder.isTerminated()) {
            if (!isMain && returnType() !== ZType.VOID) {
                logger.error("Error: '{}' can reach the end without returning", proto.name)
                builder.markFailed()
                builder.symbols().exitScope()
                return null
            }
            val ret: TypeRef = returnType()
            val zero: String
            if (ret.isBoolean()) {
                zero = "i1 false"
            } else if (ret.isString()) {
                zero = "{ i8*, i64 } zeroinitializer"
            } else if (ret.isPointer()) {
                zero = "ptr null"
            } else if (ret.isFloat()) {
                zero = ret.llvm() + " 0.0"
            } else if (ret.isIntegral()) {
                zero = ret.llvm() + " 0"
            } else if (ret.isVoid()) {
                zero = "void"
            } else {
                logger.error("Error: cannot produce a default return for '{}'", ret.zName())
                builder.markFailed()
                builder.symbols().exitScope()
                return null
            }
            builder.emitBareReturn(zero)
        }

        val attrs: java.lang.StringBuilder = java.lang.StringBuilder()

        val small = builder.getIR().lineSequence().asStream().count() < LINE_COUNT_TO_INLINE
        if (small) attrs.append(" alwaysinline")
        else attrs.append(" inlinehint")


        attrs.append(" nounwind nosync")
        if (isMain) attrs.append(" hot willreturn")
        val text =
            "define " + (if (isMain) "dso_local" else "internal dso_local") + " " + returnType().llvm() + " @" + builder.irNameOf(
                proto.name
            ) + "(" + parameters + ")" + attrs + " {\nentry:\n" + builder.getEntryAllocas() + builder.getIR() + "}\n"

        builder.symbols().exitScope()
        return LLVMTextUnit(text, ZType.VOID)
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(FunctionAST::class.java)
        private const val LINE_COUNT_TO_INLINE: Short = 40

        private fun resolveParamType(name: String?): TypeRef {
            try {
                return ZType.Companion.fromZName(name)
            } catch (e: java.lang.IllegalArgumentException) {
                val st: StructType = StructRegistry.lookup(name)
                if (st == null) throw e
                return st
            }
        }
    }
}
