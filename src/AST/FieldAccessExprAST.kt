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
 * `p.x`: read a field of a struct variable.
 *
 *
 * At parse time this may also be a module variable `math.sqrt`,
 * because the parser cannot tell them apart. If the base name turns out
 * to be a module, codegen falls back to reading `base.field` from
 * the symbol table.
 */
class FieldAccessExprAST(base: ExprAST?, field: String?) : ExprAST() {
    private val base: ExprAST?
    private val field: String?

    init {
        this.base = base
        this.field = field
    }

    /**
     * Emits the field's address, without loading it.
     *
     *
     * This is what nested field access uses: `o.inner.v` needs
     * the address of `inner` to GEP into it. A module variable
     * `math.sqrt` is not a struct field, so it is not handled here;
     * [.codeGen] deals with it before calling this.
     *
     * @return the field pointer, or null on error
     */
    fun emitFieldPointer(builder: IRBuilder): String? {
        val basePtr: String?
        if (base is VarRefExprAST) {
            val info: SymbolInfo? = builder.symbols().lookup(base.name())
            if (info == null) {
                logger.error("Error: undeclared variable: {}", base.name())
                builder.markFailed()
                return null
            }
            if (info.getType() !is StructType) {
                logger.error(
                    "Error: '{}' is not a struct, cannot access '.{}'",
                    base.name(), field
                )
                builder.markFailed()
                return null
            }
            basePtr = info.getPointerReg()
        } else if (base is FieldAccessExprAST) {
            basePtr = base.emitFieldPointer(builder)
            if (basePtr == null) return null
        } else {
            logger.error("Error: field access needs a variable or field base")
            builder.markFailed()
            return null
        }

        val baseType: TypeRef? = base.type(builder)
        if (baseType !is StructType) {
            logger.error("Error: base of '.{}' is not a struct", field)
            builder.markFailed()
            return null
        }
        val idx: Int = baseType.indexOf(field)
        if (idx < 0) {
            logger.error("Error: struct '{}' has no field '{}'", baseType.zName(), field)
            builder.markFailed()
            return null
        }
        return builder.emitGEP(basePtr, baseType.llvm(), idx)
    }

    override fun type(builder: IRBuilder): TypeRef {
        if (base is VarRefExprAST) {
            val moduleVar: SymbolInfo? =
                builder.symbols().lookup(base.name() + "." + field)
            if (moduleVar != null) return moduleVar.getType()
            val info: SymbolInfo? = builder.symbols().lookup(base.name())
            if (info != null && info.getType() is StructType) {
                val ft: TypeRef? = st.fieldType(field)
                return if (ft == null) ZType.INT32 else ft
            }
        } else if (base is FieldAccessExprAST) {
            val baseType: TypeRef? = base.type(builder)
            if (baseType is StructType) {
                val ft: TypeRef? = baseType.fieldType(field)
                return if (ft == null) ZType.INT32 else ft
            }
        }
        return ZType.INT32
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        // Module variable, e.g. math.sqrt: the base is a module, the
        // field is a global. The symbol table holds the global directly.
        if (base is VarRefExprAST) {
            val moduleVar: SymbolInfo? =
                builder.symbols().lookup(base.name() + "." + field)
            if (moduleVar != null) {
                return LLVMTextUnit(
                    builder.emitLoad(moduleVar.getLlvmType(), moduleVar.getPointerReg()),
                    moduleVar.getType()
                )
            }
        }
        val fieldPtr = emitFieldPointer(builder)
        if (fieldPtr == null) return null
        val ft: TypeRef = type(builder)
        return LLVMTextUnit(builder.emitLoad(ft.llvm(), fieldPtr), ft)
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(FieldAccessExprAST::class.java)
    }
}