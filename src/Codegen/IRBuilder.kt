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
 * Class with helper methods to generate LLVM IR code using textual strings
 */
class IRBuilder {
    /**
     * registerCount stores the next available register (es. %1)
     * irCode is the StringBuilder for the code
     * registerTypes stores the LLVM type of each register
     */
    private var registerCount = 1
    private val irCode: java.lang.StringBuilder = java.lang.StringBuilder()

    /** Stack slots are collected here and emitted immediately after a function's entry label.  */
    private val entryAllocas: java.lang.StringBuilder = java.lang.StringBuilder()
    private val allocaNameCounts: MutableMap<String?, Int?> = HashMap<String?, Int?>()
    private val registerTypes: MutableMap<String?, String?> = HashMap<String?, String?>()
    private var tbaaCounter = 5000
    private var tbaaRoot = -1
    private var tbaaChar = -1
    private val tbaaNodes: MutableMap<String?, Int?> = HashMap<String?, Int?>()
    private var loopMetadataCounter = 1000

    /**
     * The names visible to the function being generated.
     *
     */
    private val symbols: src.Codegen.SymbolTable = src.Codegen.SymbolTable()

    private val signatures: MutableMap<String?, PrototypeAST?> = LinkedHashMap<String?, PrototypeAST?>()

    private var currentModule: String? = null
    private var varArgList: String? = null
    private var fastMathFlags: String? = "contract"

    fun setFastMathFlags(flags: String?) {
        this.fastMathFlags = flags
    }

    fun fastMathFlags(): String? {
        return fastMathFlags
    }

    fun setCurrentModule(moduleName: String?) {
        this.currentModule = moduleName
    }

    fun currentModule(): String? {
        return currentModule
    }

    fun qualify(name: String): String {
        return if (currentModule == null || name.contains(".")) name else currentModule + "." + name
    }

    fun irName(key: String): String {
        return key.replace('.', '_')
    }


    fun irNameOf(name: String): String {
        return irName(qualify(name))
    }

    class ResolvedSignature(prototype: PrototypeAST?, irName: String?, externReturnType: ZType?) {
        constructor(prototype: PrototypeAST?, irName: String?) : this(prototype, irName, null)

        val prototype: PrototypeAST?
        val irName: String?
        val externReturnType: ZType?

        init {
            this.prototype = prototype
            this.irName = irName
            this.externReturnType = externReturnType
        }
    }


    fun resolve(callee: String): ResolvedSignature? {
        if (callee.contains(".")) {
            val prototype: PrototypeAST? = signatures.get(callee)
            if (prototype != null) return ResolvedSignature(prototype, irName(callee))
            // an extern of the standard library keeps its plain C name
            if (!callee.startsWith("std.")) return null
            return resolveExtern(callee.substring("std.".length))
        }
        if (currentModule != null) {
            // a wrapper calls the C function it shares its name with
            if (moduleExterns.contains(currentModule + "." + callee)) {
                val external = resolveExtern(callee)
                if (external != null) return external
            }
            val own: PrototypeAST? = signatures.get(currentModule + "." + callee)
            if (own != null) return ResolvedSignature(own, irName(currentModule + "." + callee))
        }
        val root: PrototypeAST? = signatures.get(callee)
        if (root != null) return ResolvedSignature(root, irName(callee), externReturnTypes.get(callee))
        // the standard library can be called without its prefix once imported
        val std: PrototypeAST? = signatures.get("std." + callee)
        if (std == null) return null
        return ResolvedSignature(std, irName("std." + callee), externReturnTypes.get("std." + callee))
    }

    private fun resolveExtern(name: String): ResolvedSignature? {
        val prototype: PrototypeAST? = signatures.get(name)
        val returnType: ZType? = externReturnTypes.get(name)
        if (prototype == null || returnType == null) return null
        return ResolvedSignature(prototype, irName(name), returnType)
    }

    /** @return whether the current block already has a terminator
     */
    var isTerminated: Boolean = false
        private set
    private var returnType: TypeRef = ZType.FLT64

    /**.
     */
    private var failed = false

    /** Records that generation hit an error the caller cannot see.  */
    fun markFailed() {
        this.failed = true
    }

    /** @return whether anything reported an error since the last reset
     */
    fun hasFailed(): Boolean {
        return failed
    }

    /** Global definitions: string constants and library declarations.  */
    private val globals: java.lang.StringBuilder = java.lang.StringBuilder()

    /** How many string constants have been emitted so far.  */
    private var stringCount = 0

    /** True once the libc declarations have been emitted.  */
    private var declaresEmitted = false

    /** True once the vararg declarations have been emitted  */
    private var varArgsEmitted = false

    /** True when the current function has an active va_list  */
    private var varArgStarted = false

    /**
     *
     * @param prototype the signature to declare
     * @return false when the name is already taken, in which case nothing changes
     */
    fun declare(prototype: PrototypeAST): Boolean {
        val key: String? = qualify(prototype.name)
        if (signatures.containsKey(key)) return false
        signatures.put(key, prototype)
        return true
    }


    /**
     * The return types of the extern (C) functions declared so far.
     */
    private val externReturnTypes: MutableMap<String?, ZType?> = HashMap<String?, ZType?>()

    /** Externs declared by each module, used to resolve its wrappers.  */
    private val moduleExterns: MutableSet<String?> = HashSet<String?>()

    /** What happened when an extern was declared.  */
    enum class ExternDeclaration {
        DECLARED,
        ALREADY_DECLARED,
        CONFLICT
    }

    /**
     * Declares an extern function together with the type it returns.
     *
     * @param prototype the signature to declare
     * @param returnType what the C function returns
     * @return whether the declaration is new, already present, or conflicts
     */
    fun declareExtern(prototype: PrototypeAST, returnType: ZType?): ExternDeclaration {
        moduleExterns.add(qualify(prototype.name))
        val existing: PrototypeAST? = signatures.get(prototype.name)
        if (existing == null) {
            signatures.put(prototype.name, prototype)
            externReturnTypes.put(prototype.name, returnType)
            return ExternDeclaration.DECLARED
        }

        val existingReturn: ZType? = externReturnTypes.get(prototype.name)
        if (existingReturn == null) {
            return ExternDeclaration.CONFLICT
        }
        if (existing == prototype && existingReturn == returnType) {
            return ExternDeclaration.ALREADY_DECLARED
        }
        return ExternDeclaration.CONFLICT
    }

    private fun emitVarArgEnd() {
        if (!varArgStarted) return
        appendLine("call void @llvm.va_end(ptr " + varArgList + ")")
    }

    fun emitAssumeAfterAlloc(p: String?, size: String?) {
        appendLine("call void @llvm.assume(i1 true) [ \"nonnull\"(ptr " + p + "), \"dereferenceable\"(ptr " + p + ", i64 " + size + ") ]")
    }

    fun emitBranchWeights(cond: String?, t: String?, f: String?) {
        appendLine("br i1 " + cond + ", label %" + t + ", label %" + f + ", !prof !{!\"branch_weights\", i32 1000, i32 1}")
    }

    fun emitReturn(llvmTextUnit: LLVMTextUnit?) {
        emitVarArgEnd()

        val targetType: TypeRef = returnType
        if (returnType === ZType.VOID) {
            appendLine("ret void")
            this.isTerminated = true
            return
        }

        if (llvmTextUnit == null) { // it must be main
            appendLine("ret i32 281")
            this.isTerminated = true
            return
        }


        val target: String = targetType.llvm()
        val from: String = llvmTextUnit.type.llvm()

        if (from == target) {
            appendLine("ret " + target + " " + llvmTextUnit.text)
            this.isTerminated = true
            return
        }

        if (from == "ptr" || target == "ptr") {
            logger.error("Error: cannot convert between {} and {}", from, target)
            markFailed()
            return
        }

        if (llvmTextUnit.type.isString() || targetType.isString()) {
            logger.error("Error: cannot convert between str and {}", if (llvmTextUnit.type.isString()) target else from)
            markFailed()
            return
        }
        val opcode = conversionOpcode(llvmTextUnit.type, targetType)
        if (opcode == null) {
            logger.error("Error: cannot convert between {} and {}", from, target)
            markFailed()
            return
        }
        val converted = nextRegister()
        appendLine(converted + " = " + opcode + from + " " + llvmTextUnit.text + " to " + target)
        appendLine("ret " + target + " " + converted)
        this.isTerminated = true
    }

    /** Emits a terminator that has no operand, such as a `main` that fell off the end.  */
    fun emitBareReturn(literal: String?) {
        emitVarArgEnd()
        appendLine("ret " + literal)
        this.isTerminated = true
    }

    /** Declares what the function being generated returns.  */
    fun setReturnType(type: TypeRef) {
        this.returnType = type
    }

    /** @return the symbol table for the function being generated
     */
    fun symbols(): src.Codegen.SymbolTable {
        return symbols
    }

    /**
     * @return a string with the next register (es. %2)
     */
    fun nextRegister(): String {
        return "%" + (registerCount++)
    }

    /**
     * appends a line to the string builder and adds \n
     * @param line the String to append
     */
    fun appendLine(line: String?) {
        irCode.append(" ").append(line).append("\n")
    }

    val iR: String
        /**
         * @return the IR code in a string form
         */
        get() = irCode.toString()

    /**
     * return the String form of the IR
     */
    override fun toString(): String {
        return this.iR
    }

    /**
     * reset the count of the registers (es. when entering a new function)
     */
    fun reset() {
        registerCount = 1
        labelCount = 0
        irCode.setLength(0)
        entryAllocas.setLength(0)
        allocaNameCounts.clear()
        registerTypes.clear()
        loops.clear()
        this.isTerminated = false
        failed = false
        varArgList = null
        varArgStarted = false
    }

    /** the loops being generated, the innermost first  */
    private val loops: Deque<src.Codegen.Loop?> = java.util.ArrayDeque<src.Codegen.Loop?>()

    /** Starts a loop whose body can `break` or `continue`.  */
    fun enterLoop(continueLabel: String?, breakLabel: String?) {
        loops.push(src.Codegen.Loop(continueLabel, breakLabel))
    }

    /** Ends the innermost loop.  */
    fun exitLoop(): src.Codegen.Loop {
        return loops.pop()
    }

    /** @return the innermost loop, or null outside any loop
     */
    fun currentLoop(): src.Codegen.Loop? {
        return loops.peek()
    }

    /**
     * emitAlloca emits an allocation instruction for a variable
     * @param varName name of the ptr to use
     * @param llvmType LLVM type of the variable (es. int32 becomes i32)
     * @return a String with the IR code
     */
    fun emitAlloca(varName: String?, llvmType: String?): String {
        val occurrence: Int = allocaNameCounts.merge(varName, 1) { a: Int?, b: Int? -> java.lang.Integer.sum(a, b) }
        val uniqueName = if (occurrence == 1) varName else varName + "." + (occurrence - 1)
        val ptr = "%" + uniqueName
        entryAllocas.append(" ").append(ptr).append(" = alloca ")
            .append(llvmType).append(alignment(llvmType)).append("\n")
        return ptr
    }

    /** @return stack allocations that must be placed at the start of the entry block
     */
    fun getEntryAllocas(): String {
        return entryAllocas.toString()
    }

    /**
     * emitStore emits the IR code to store a value in a variable
     * @param val the value to store
     * @param llvmType the llvmtype
     * @param ptr the name of the ptr
     */
    fun emitStore(`val`: String?, llvmType: String, ptr: String?) {
        appendLine(
            "store " + llvmType + " " + `val` + ", ptr " + ptr + alignment(llvmType) + ", !tbaa !" + tbaaNodeFor(
                llvmType
            )
        )
    }

    private fun tbaaRootNode(): Int {
        if (tbaaRoot == -1) {
            tbaaRoot = tbaaCounter++
            globals.append("!" + tbaaRoot + " = !{!\"Z TBAA\"}\n")
        }
        return tbaaRoot
    }

    private fun tbaaCharNode(): Int {
        if (tbaaChar == -1) {
            val root = tbaaRootNode()
            tbaaChar = tbaaCounter++
            globals.append("!" + tbaaChar + " = !{!\"omnipotent char\", !" + root + ", i64 0}\n")
        }
        return tbaaChar
    }

    private fun tbaaNodeFor(llvmType: String): Int {
        val existing = tbaaNodes.get(llvmType)
        if (existing != null) return existing
        val parent = tbaaCharNode()
        val id = tbaaCounter++
        globals.append("!" + id + " = !{!\"" + llvmType.replace("\"", "") + "\", !" + parent + ", i64 0}\n")
        tbaaNodes.put(llvmType, id)
        return id
    }

    fun emitAllocaGlobal(varName: String?, llvmType: String?): String {
        val ptr = "%" + varName
        appendLine(ptr + " = global " + llvmType + alignment(llvmType))
        return ptr
    }

    /**
     * emitLoad emits the Ir code to Load the value from a ptr
     * @param llvmType llvmtype
     * @param ptr the name of the ptr
     * @return the register in which the value was loaded
     */
    fun emitLoad(llvmType: String, ptr: String?): String {
        val reg = nextRegister()
        appendLine(
            reg + " = load " + llvmType + ", ptr " + ptr + alignment(llvmType) + ", !tbaa !" + tbaaNodeFor(
                llvmType
            )
        )
        registerTypes.put(reg, llvmType)
        return reg
    }

    /**
     * Gets the LLVM type of register
     * @param reg the register name (e.g., "%1")
     * @return the LLVM type or null if not tracked
     */
    fun getRegisterType(reg: String?): String? {
        return registerTypes.get(reg)
    }

    private var labelCount = 0

    /**
     * nextLabel emits the next available label
     * @param prefix the prefix of the label
     * @return the label
     */
    fun nextLabel(prefix: String?): String {
        return prefix + "." + (labelCount++)
    }


    /**
     * setter method for registerTypes
     * @param reg name of the register
     * @param type type to set
     */
    fun setRegisterType(reg: String?, type: String?) {
        registerTypes.put(reg, type)
    }

    /**
     * emitLabel starts a new basic block, e.g. "for.cond:"
     * @param label the label name (without '%')
     */
    fun emitLabel(label: String?) {
        appendLine(label + ":")
        this.isTerminated = false
    }

    /**
     * emitBr outputs an unconditional branch, e.g. "br label %for.cond"
     * @param label the target label (without '%')
     */
    fun emitBr(label: String?) {
        appendLine("br label %" + label)
        this.isTerminated = true
    }

    /**
     * emitCondBr outputs a conditional branch, e.g.
     * "br i1 %1, label %for.body, label %for.end"
     * @param condReg the i1 condition register
     * @param trueLabel label to jump to if true
     * @param falseLabel label to jump to if false
     */
    fun emitCondBr(condReg: String?, trueLabel: String?, falseLabel: String?) {
        appendLine("br i1 " + condReg + ", label %" + trueLabel + ", label %" + falseLabel)
        this.isTerminated = true
    }

    /**
     * @return the global definitions (string constants and libc declares),
     * which must go in front of the functions that use them
     */
    fun getGlobals(): String {
        return globals.toString()
    }

    /**
     * Adds a string literal to the module as a fat string constant.
     *
     *
     * Two globals are emitted: `@.str.N.bytes`, the null-terminated
     * bytes (so C functions can still read them), and `@.str.N`, the
     * fat `{ i8*, i64 }` pair. The length excludes the null byte.
     *
     * @param bytes the UTF-8 bytes of the literal
     * @return the fat constant's name
     */
    fun emitStringConstant(bytes: ByteArray): String {
        val name = ".str." + (stringCount++)
        val length = bytes.size + 1

        globals.append("@").append(name).append(".bytes = private unnamed_addr constant [").append(length)
            .append(" x i8] c\"")
        for (b in bytes) {
            val u = b.toInt() and 0xFF
            if (u == '"'.code) globals.append("\\22")
            else if (u == '\\'.code) globals.append("\\5C")
            else if (u >= 32 && u < 127) globals.append(u.toChar())
            else globals.append(String.format("\\%02X", u))
        }
        globals.append("\\00\"\n")

        globals.append("@").append(name)
            .append(" = private unnamed_addr constant { i8*, i64 } { ")
            .append("i8* getelementptr inbounds ([").append(length)
            .append(" x i8], ptr @").append(name).append(".bytes, i64 0, i64 0), ")
            .append("i64 ").append(bytes.size)
            .append(" }\n")

        return name
    }

    fun appendToGlobals(append: String?) {
        globals.append(append)
    }

    fun beginVarArgFunction() {
        ensureVarArgs()
        varArgList = nextRegister()
        appendLine(varArgList + " = alloca " + VA_LIST + ", align 16")
        appendLine("call void @llvm.va_start(ptr " + varArgList + ")")
        varArgStarted = true
    }

    fun loadVarArg(name: String?, index: Int): String? {
        val info: SymbolInfo? = symbols.lookup(name)
        if (info == null || !info.isVarArg() || varArgList == null || index < 1) return null

        val copy = nextRegister()
        appendLine(copy + " = alloca " + VA_LIST + ", align 16")
        appendLine("call void @llvm.va_copy(ptr " + copy + ", ptr " + varArgList + ")")

        var value: String? = null
        for (i in 0..<index) {
            value = nextRegister()
            appendLine(value + " = va_arg ptr " + copy + ", " + info.getLlvmType())
        }

        appendLine("call void @llvm.va_end(ptr " + copy + ")")
        return value
    }

    /**
     * emits the declarations for the vararg intrinsics once
     */
    private fun ensureVarArgs() {
        if (varArgsEmitted) return
        varArgsEmitted = true
        globals.append("declare void @llvm.va_start(ptr)\n")
        globals.append("declare void @llvm.va_end(ptr)\n")
        globals.append("declare void @llvm.va_copy(ptr, ptr)\n")
    }

    /** True once the allocator runtime has been emitted.  */
    private var allocatorEmitted = false

    /**
     * Declares the allocator functions provided by the compiled C runtime.
     */
    private fun ensureAllocator() {
        if (allocatorEmitted) return
        allocatorEmitted = true

        globals.append("declare noalias ptr @zalloc(i64) allocsize(0) nounwind nosync willreturn\n")
        globals.append("declare void @zfree(ptr) nounwind nosync willreturn\n")
    }

    /**
     * Bump-allocates `size` bytes and returns the pointer.
     *
     * @param size a register holding the byte count
     * @return a register holding the pointer
     */
    fun emitBumpAlloc(size: String?): String {
        ensureAllocator()
        val ptr = nextRegister()
        appendLine(ptr + " = call ptr @zalloc(i64 " + size + ")")
        return ptr
    }

    /**
     * Frees a block allocated by `alloc`.
     *
     * @param ptr a register holding the pointer
     */
    fun emitFree(ptr: String?) {
        ensureAllocator()
        appendLine("call void @zfree(ptr " + ptr + ")")
    }

    /**
     * Public entry point for the string runtime: `strcpy` and
     * `strcat` declarations.
     */
    fun ensureStringRuntime() {
        ensureStringDeclares()
    }

    /**
     * Emits the libc declares `strcpy` and `strcat`, the only
     * C functions the string runtime still needs. Length is in the fat
     * struct, so `strlen` is gone.
     */
    private fun ensureStringDeclares() {
        if (declaresEmitted) return
        declaresEmitted = true
        globals.append("declare ptr @strcpy(ptr, ptr)\n")
        globals.append("declare ptr @strcat(ptr, ptr)\n")
    }

    private fun hasExtern(name: String?, returnType: ZType?): Boolean {
        val prototype: PrototypeAST? = signatures.get(name)
        if (prototype == null || externReturnTypes.get(name) != returnType) return false
        return prototype.params.size == 1 && prototype.params.getFirst().type == ZType.STRING.zName()
    }

    /** The structs this module has already declared.  */
    private val declaredStructs: MutableSet<String?> = HashSet<String?>()

    /** Emits `%Point = type { i32, i32 }` once.  */
    fun declareStruct(type: StructType) {
        if (!declaredStructs.add(type.zName())) return
        val sb: java.lang.StringBuilder = java.lang.StringBuilder("%").append(type.zName()).append(" = type { ")
        for (i in type.fields().indices) {
            if (i > 0) sb.append(", ")
            sb.append(type.fields().get(i).type.llvm())
        }
        sb.append(" }\n")
        appendToGlobals(sb.toString())
    }

    /** Emits `%r = getelementptr inbounds %T, ptr %base, i32 0, i32 idx`.  */
    fun emitGEP(basePtr: String?, structType: String?, idx: Int): String {
        val reg = nextRegister()
        appendLine(reg + " = getelementptr inbounds " + structType + ", ptr " + basePtr + ", i32 0, i32 " + idx)
        return reg
    }

    /**
     * emits an llvm builtin for memcpy instead on relying to a declare (can be inlinde)
     */
    fun emitMemcpy(dest: String?, src: String?, len: String?) {
        appendLine("call void @llvm.memcpy.p0.p0.i64(ptr align 1 " + dest + ", ptr align 1 " + src + ", i64 " + len + ", i1 false)")
    }

    /**
     * same thing here
     */
    fun emitMemset(dest: String?, `val`: String?, len: String?) {
        appendLine("call void @llvm.memset.p0.i64(ptr align 1 " + dest + ", i8 " + `val` + ", i64 " + len + ", i1 false)")
    }

    /**
     * emits metadata to inline loops
     */
    fun emitBrWithLoopMetadata(label: String?, vectorWidth: Int, unrollCount: Int) {
        loopMetadataCounter += 1
        val id = loopMetadataCounter

        globals.append("!").append(id).append(" = !{!").append(id).append(", !").append(id + 1).append(", !")
            .append(id + 2).append("}\n")
        globals.append("!").append(id + 1).append(" = !{!\"llvm.loop.vectorize.enable\", i1 true}\n")
        globals.append("!").append(id + 2).append(" = !{!\"llvm.loop.unroll.count\", i32 ").append(unrollCount)
            .append("}\n")
        appendLine("br label %" + label + ", !llvm.loop !" + id)

        loopMetadataCounter += 2
    }

    companion object {
        /** logger  */
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(IRBuilder::class.java)

        private const val DOUBLE = "double"
        private const val FLT = "float"
        private const val VA_LIST = "[32 x i8]"
        private fun conversionOpcode(fromType: TypeRef, targetType: TypeRef): String? {
            val from: String = fromType.llvm()
            val to: String = targetType.llvm()
            val fromFloat: Boolean = fromType.isFloat()
            val toFloat: Boolean = targetType.isFloat()
            if (fromFloat && !toFloat) {
                return if (targetType.isUnsigned() || targetType.isBoolean()) "fptoui " else "fptosi "
            }
            if (!fromFloat && toFloat) {
                return if (fromType.isUnsigned() || fromType.isBoolean()) "uitofp " else "sitofp "
            }
            if (fromFloat) return if (to == FLT) "fptrunc " else "fpext "
            if (!from.startsWith("i") || !to.startsWith("i")) return null
            val fromBits: Int = fromType.bits()
            val toBits = integerBits(to)
            if (fromBits > toBits) return "trunc "
            if (fromBits < toBits) {
                return if (fromType.isUnsigned() || fromType.isBoolean()) "zext " else "sext "
            }
            return null
        }

        private fun integerBits(type: String): Int {
            return if (type == "i1") 1 else type.substring(1).toInt()
        }

        private fun alignment(llvmType: String?): String {
            val alignment: Int = TypeLayout.alignmentOf(llvmType)
            return if (alignment > 0) ", align " + alignment else ""
        }
    }
}
