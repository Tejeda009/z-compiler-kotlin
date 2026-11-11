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

/**
 * SymbolTable is a class that represents the symbol table of the program
 */
class SymbolTable {
    /**
     * SymbolInfo is a class that represents the information of a symbol
     *
     *
     * The type is kept as a [ZType], not as an LLVM type string.
     * `int32` and `uint32` share the LLVM type `i32`;
     * storing only the LLVM name would make it impossible to tell them
     * apart when reading the symbol back, and signed vs unsigned division
     * and comparison would silently pick the wrong instruction.
     */
    class SymbolInfo @JvmOverloads constructor(
        /**
         * name is the name of the var
         * type is the Z type of the var
         * pointerReg is the register of the var
         * isConstant is true if the var is a constant
         * isUsed is true if the var is used
         * callCount is the number of times the var is called
         */
        /**
         * getter methods for a symbol info
         * @return name, type, ptrReg, isConstant, isUsed, callCount
         */
        val name: String?, type: TypeRef, pointerReg: String?, isConstant: Boolean, isVarArg: Boolean = false
    ) {
        private val type: TypeRef
        val pointerReg: String?
        val isConstant: Boolean
        val isVarArg: Boolean
        var isUsed: Boolean
        var callCount: Int
            private set

        /**
         * constructor for the SymbolInfo of a variadic parameter
         * @param name name of the var
         * @param type Z type of each variadic argument
         * @param pointerReg the register in which the pointer is stored
         * @param isConstant is it a constant?
         * @param isVarArg whether this symbol is the variadic parameter
         */
        /**
         * constructor for the SymbolInfo for a variable
         * @param name name of the var
         * @param type Z type of the var
         * @param pointerReg the register in which the pointer is stored
         * @param isConstant is it a constant?
         */
        init {
            this.type = type
            this.pointerReg = pointerReg
            this.isConstant = isConstant
            this.isVarArg = isVarArg
            this.isUsed = false
            this.callCount = 0
        }

        fun getType(): TypeRef? {
            return type
        }

        val llvmType: String?
            get() = type.llvm()

        fun incrementCallCount() {
            this.callCount++
        }
    }

    /**
     * map of globals
     */
    private val globals: MutableMap<String?, SymbolInfo?> = HashMap<String?, SymbolInfo?>()

    /**
     * Deque of maps of symbol info
     */
    private val scopes: Deque<MutableMap<String?, SymbolInfo?>> =
        java.util.ArrayDeque<MutableMap<String?, SymbolInfo?>>()

    /**
     * constructor for the symbol table
     * creates the initial global scope
     */
    init {
        enterScope() // initial global scope
    }

    /**
     * enters a new scope inside the parent
     */
    fun enterScope() {
        scopes.push(HashMap<String?, SymbolInfo?>())
    }

    /**
     * exit the current scope to go back at the parent
     */
    fun exitScope() {
        if (scopes.size() > 1) {
            scopes.pop()
        }
    }

    /**
     * defines a new global
     * @param name name
     * @param info info
     */
    fun defineGlobal(name: String?, info: SymbolInfo?) {
        globals.put(name, info)
    }

    /**
     * defines a symbol in the current scope
     * returns false if the symbol is already present
     */
    fun define(name: String?, info: SymbolInfo?): Boolean {
        val current: MutableMap<String?, SymbolInfo?>? = scopes.peek()
        if (current == null || current.containsKey(name)) {
            return false
        }
        current.put(name, info)
        return true
    }

    /**
     * searches a symbol starting from the current scope and going up
     */
    fun lookup(name: String?): SymbolInfo? {
        for (scope in scopes) {
            val info: SymbolInfo? = scope.get(name)
            if (info != null) {
                info.isUsed = true // for optimization uses /warning
                return info
            }
        }
        return globals.get(name)
    }

    /**
     * searches for a symbol only in the current scope
     */
    fun lookupCurrentScope(name: String?): SymbolInfo? {
        val current: MutableMap<String?, SymbolInfo?>? = scopes.peek()
        return if (current != null) current.get(name) else null
    }

    val depth: Int
        /**
         * getter method for a SymbolTable
         * @return the size (number of stacks, functions)
         */
        get() = scopes.size()
}