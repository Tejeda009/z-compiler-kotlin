package src.Codegen

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

internal object TypeLayout {
    private const val POINTER_ALIGNMENT = 8

    fun alignmentOf(type: TypeRef?): Int {
        return TypeLayout.alignmentOf(type, HashSet<String?>())
    }

    private fun alignmentOf(type: TypeRef?, visiting: MutableSet<String?>): Int {
        when (type) {
            null -> {
                return 0
            }

            -> {
                return when (builtin) {
                    ZType.BOOL -> 1
                    ZType.INT32, ZType.UINT32, ZType.FLT32 -> 4
                    ZType.INT64, ZType.UINT64, ZType.FLT64 -> 8
                    ZType.PTR, ZType.INT32_PTR, ZType.INT64_PTR, ZType.UINT32_PTR, ZType.UINT64_PTR, ZType.FLT32_PTR, ZType.FLT64_PTR, ZType.BOOL_PTR, ZType.STR_PTR, ZType.STRING -> POINTER_ALIGNMENT
                    ZType.VOID, ZType.VARARG -> 0
                }
            }

            -> {
                if (!visiting.add(struct.zName())) {
                    return 0
                }
                try {
                    var alignment = 1
                    for (field in struct.fields()) {
                        alignment = java.lang.Math.max(alignment, TypeLayout.alignmentOf(field.type, visiting))
                    }
                    return alignment
                } finally {
                    visiting.remove(struct.zName())
                }
            }

            else -> {}
        }
        return 0
    }

    fun alignmentOf(llvmType: String?): Int {
        return TypeLayout.alignmentOf(llvmType, HashSet<String?>())
    }

    private fun alignmentOf(llvmType: String?, visiting: MutableSet<String?>): Int {
        if (llvmType == null) {
            return 0
        }
        val type: String = llvmType.trim()
        if (type.isEmpty()) {
            return 0
        }
        if (type.endsWith("*")) {
            return POINTER_ALIGNMENT
        }
        if (type.startsWith("%")) {
            val struct: StructType? = StructRegistry.lookup(type.substring(1))
            return if (struct == null) 0 else alignmentOf(struct, visiting)
        }
        if (type.startsWith("<{")) {
            return 1
        }
        if (type.startsWith("{")) {
            return aggregateAlignment(type, visiting)
        }
        if (type.startsWith("[")) {
            val separator = topLevelX(type)
            if (separator < 0) {
                return 0
            }
            return TypeLayout.alignmentOf(type.substring(separator + 1, type.length() - 1), visiting)
        }
        if (type.startsWith("<")) {
            return 0
        }
        if (type.startsWith("i") && type.length() > 1 && java.lang.Character.isDigit(type.charAt(1))) {
            return integerAlignment(type)
        }
        return when (type) {
            "half" -> 2
            "float" -> 4
            "double" -> 8
            "fp128", "x86_fp80", "ppc_fp128" -> 16
            "ptr" -> POINTER_ALIGNMENT
            else -> 0
        }
    }

    private fun aggregateAlignment(type: String, visiting: MutableSet<String?>): Int {
        val body: String = type.substring(1, type.length() - 1)
        var alignment = 1
        var start = 0
        var depth = 0
        for (i in 0..body.length()) {
            val end = i == body.length()
            if (!end) {
                val c: Char = body.charAt(i)
                if (c == '[' || c == '{' || c == '<' || c == '(') {
                    depth++
                } else if (c == ']' || c == '}' || c == '>' || c == ')') {
                    depth--
                }
            }
            if (end || (body.charAt(i) == ',' && depth == 0)) {
                val field: String = body.substring(start, i).trim()
                if (!field.isEmpty()) {
                    alignment = java.lang.Math.max(alignment, TypeLayout.alignmentOf(field, visiting))
                }
                start = i + 1
            }
        }
        return alignment
    }

    private fun topLevelX(type: String): Int {
        var depth = 0
        for (i in 1..<type.length() - 1) {
            val c: Char = type.charAt(i)
            if (c == '[' || c == '{' || c == '<' || c == '(') {
                depth++
            } else if (c == ']' || c == '}' || c == '>' || c == ')') {
                depth--
            } else if (c == 'x' && depth == 0 && java.lang.Character.isWhitespace(type.charAt(i - 1))
                && java.lang.Character.isWhitespace(type.charAt(i + 1))
            ) {
                return i
            }
        }
        return -1
    }

    private fun integerAlignment(type: String): Int {
        try {
            val bits: Int = java.lang.Integer.parseInt(type.substring(1))
            val bytes = (bits + 7) / 8
            var alignment = 1
            while (alignment < bytes) {
                alignment = alignment shl 1
            }
            return alignment
        } catch (e: java.lang.NumberFormatException) {
            return 0
        }
    }
}
