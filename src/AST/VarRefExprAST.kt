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

class VarRefExprAST(private val name: String) : ExprAST() {
    /** @return the name being read
     */
    fun name(): String? {
        return name
    }

    override fun type(builder: IRBuilder): TypeRef? {
        var info: SymbolInfo? = builder.symbols().lookup(name)
        if (info == null && !name.contains(".")) info = builder.symbols().lookup(builder.qualify(name))
        if (info != null) return info.getType()

        val dot: Int = name.lastIndexOf('.')
        if (dot > 0) {
            val varArg: SymbolInfo? = builder.symbols().lookup(name.substring(0, dot))
            if (varArg != null && varArg.isVarArg()) return varArg.getType()
        }
        return ZType.INT32
    }

    override fun codeGen(builder: IRBuilder): LLVMTextUnit? {
        var info: SymbolInfo? = builder.symbols().lookup(name)
        if (info == null && !name.contains(".")) {
            info = builder.symbols().lookup(builder.qualify(name))
        }
        if (info != null) {
            if (info.isVarArg()) {
                logger.error("Error: variadic parameter '{}' must be read with an index", name)
                builder.markFailed()
                return null
            }
            val type: TypeRef = info.getType()
            return LLVMTextUnit(builder.emitLoad(type.llvm(), info.getPointerReg()), type)
        }

        val dot: Int = name.lastIndexOf('.')
        if (dot > 0) {
            val base: String = name.substring(0, dot)
            val indexText: String = name.substring(dot + 1)
            val varArg: SymbolInfo? = builder.symbols().lookup(base)
            if (varArg != null && varArg.isVarArg()) {
                val index: Int
                try {
                    index = indexText.toInt()
                } catch (e: java.lang.NumberFormatException) {
                    logger.error("Error: invalid variadic index in '{}'", name)
                    builder.markFailed()
                    return null
                }
                val register: String? = builder.loadVarArg(base, index)
                if (register == null) {
                    logger.error("Error: cannot read variadic argument '{}'", name)
                    builder.markFailed()
                    return null
                }
                return LLVMTextUnit(register, varArg.getType())
            }
        }

        logger.error("Error: undeclared variable: {}", name)
        builder.markFailed()
        return null
    }

    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(VarRefExprAST::class.java)
    }
}