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
 * A struct literal: `Point{1 | 2}`.
 *
 *
 * Builds the aggregate with a chain of `insertvalue`: start from
 * `undef`, insert each value at its field index, and hand the final
 * register back.
 */
class StructLiteralAST(private val typeName: String?, values: MutableList<ExprAST?>) : ExprAST() {
    private val values: MutableList<ExprAST?>

    init {
        this.values = java.util.List.copyOf<ExprAST?>(values)
    }

    override fun type(builder: IRBuilder?): TypeRef {
        val st: StructType? = StructRegistry.lookup(typeName)
        return if (st == null) ZType.PTR else st
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        val st: StructType? = StructRegistry.lookup(typeName)
        if (st == null) {
            logger.error("Error: unknown struct: {}", typeName)
            builder.markFailed()
            return null
        }
        if (values.size != st.fields().size) {
            logger.error(
                "Error: '{}' expects {} values but {} were given",
                typeName, st.fields().size, values.size
            )
            builder.markFailed()
            return null
        }

        var aggregate = "undef"
        for (i in values.indices) {
            val v: LLVMTextUnit? = values.get(i).codeGen(builder)
            if (v == null) {
                builder.markFailed()
                return null
            }
            val fieldType: TypeRef = st.fields().get(i).type
            val fitted: LLVMTextUnit? = values.get(i).coerce(builder, v, fieldType)
            if (fitted == null) return null
            val reg: String = builder.nextRegister()
            builder.appendLine(
                (reg + " = insertvalue " + st.llvm() + " " + aggregate
                        + ", " + fieldType.llvm() + " " + fitted.text + ", " + i)
            )
            aggregate = reg
        }
        return LLVMTextUnit(aggregate, st)
    }

    /**
     * Builds the aggregate at compile time, as an LLVM constant.
     *
     *
     * Every field has to be a constant too: `undef` would be
     * wrong here, because a global initializer must not need a register.
     *
     * @param builder the module being built
     * @return the constant aggregate, or null when a field is not constant
     */
    override fun constantValue(builder: IRBuilder?): LLVMTextUnit? {
        val st: StructType? = StructRegistry.lookup(typeName)
        if (st == null || values.size != st.fields().size) return null

        val sb: java.lang.StringBuilder = java.lang.StringBuilder("{ ")
        for (i in values.indices) {
            val v: LLVMTextUnit? = values.get(i).constantValue(builder)
            if (v == null) return null
            if (i > 0) sb.append(", ")
            sb.append(v.type.llvm()).append(" ").append(v.text)
        }
        sb.append(" }")
        return LLVMTextUnit(sb.toString(), st)
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(StructLiteralAST::class.java)
    }
}