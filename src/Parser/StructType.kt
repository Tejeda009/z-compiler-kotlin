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
 * A user-declared struct: `struct Point { int32 x | int32 y }`.
 *
 *
 * LLVM sees it as `%Point = type { i32, i32 }`. A value of this
 * type is the whole aggregate, passed by value; a variable of this type
 * holds the aggregate in a stack slot.
 */
class StructType(private val name: String?, fields: MutableList<Field?>) : TypeRef {
    class Field(val name: String?, type: TypeRef?) {
        val type: TypeRef?

        init {
            this.type = type
        }
    }

    private val fields: MutableList<Field?>

    init {
        this.fields = java.util.List.copyOf<Field?>(fields)
    }

    fun fields(): MutableList<Field?> {
        return fields
    }

    /** @return the index of `fieldName` in the aggregate, or -1
     */
    fun indexOf(fieldName: String?): Int {
        for (i in fields.indices) {
            if (fields.get(i)!!.name == fieldName) return i
        }
        return -1
    }

    fun fieldType(fieldName: String?): TypeRef? {
        val i = indexOf(fieldName)
        return if (i < 0) null else fields.get(i)!!.type
    }

    override fun zName(): String? {
        return name
    }

    override fun llvm(): String {
        return "%" + name
    }

    override fun initial(): String {
        return "zeroinitializer"
    }

    override fun bits(): Int {
        return 0
    }

    val isIntegral: Boolean
        get() = false
    val isFloat: Boolean
        get() = false
    val isSigned: Boolean
        get() = false
    val isUnsigned: Boolean
        get() = false
    val isBoolean: Boolean
        get() = false
    val isString: Boolean
        get() = false
    val isVoid: Boolean
        get() = false
    val isPointer: Boolean
        get() = false
    val isVararg: Boolean
        get() = false
    val isStruct: Boolean
        get() = true

    override fun pointerTo(): TypeRef {
        return ZType.PTR
    }

    override fun elementType(): TypeRef? {
        return null
    }

    override fun literal(value: Long): String? {
        throw java.lang.IllegalStateException("struct has no literal")
    }

    override fun literal(value: Double): String? {
        throw java.lang.IllegalStateException("struct has no literal")
    }
}