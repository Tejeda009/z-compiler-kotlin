package src.Main

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
 * Command line interface for the compiler
 */
@picocli.CommandLine.Command(
    name = "compiler",
    mixinStandardHelpOptions = true,
    version = ["1"],
    description = ["Compiler z-code"]
)
internal class CompilerCmd : Callable<Int?> {
    /**
     * name of the source file
     */
    @picocli.CommandLine.Parameters(index = "0", description = ["Source file to be compiled"])
    private val sourceFile: File? = null

    /**
     * output path for the binary
     */
    @picocli.CommandLine.Option(names = ["-o", "--out"], description = ["Output path"])
    private val outPath: String? = null

    /**
     * libraries to be linked
     */
    @picocli.CommandLine.Option(
        names = ["-l", "--link"],
        description = ["Libraries to link against (e.g., -lcurl, -lm)"]
    )
    private val linkLibraries: MutableList<String> = java.util.ArrayList<String>()

    /**
     * directories in which to search for libraries
     */
    @picocli.CommandLine.Option(names = ["-L", "--library-path"], description = ["Directories to search for libraries"])
    private val libraryPaths: MutableList<String?> = java.util.ArrayList<String?>()

    @picocli.CommandLine.Option(
        names = ["--pgo-generate"],
        description = ["Build an instrumented binary for profile collection"]
    )
    private val pgoGenerate = false

    @picocli.CommandLine.Option(
        names = ["--pgo-use"],
        description = ["Use a profile (profdata) when generating the final binary"]
    )
    private val pgoUse: String? = null

    /**
     * enables LLVM fast-math optimizations
     */
    @picocli.CommandLine.Option(
        names = ["-fast-math", "--fast-math"],
        description = ["Enable aggressive fast-math optimizations"]
    )
    private val fastMath = false

    /**
     * Main method for the compiler
     * @return 0 if the compilation is successful, 1 otherwise
     * @throws Exception if an I/O error occurs
     */
    @Throws(java.lang.Exception::class)
    override fun call(): Int {
        // reads the file
        val zCode: String = Files.readString(sourceFile.toPath())

        logger.info("Starting compilation...")

        // parses the string in bytes
        try {
            val input: java.io.InputStream =
                ByteArrayInputStream(zCode.toByteArray(java.nio.charset.StandardCharsets.UTF_8))

            val llFilePath: String
            val binaryName: String
            val optFilePath: String

            // handles default parameters and
            // adds the output_opt.ll file which is the file where the optimized IR is put
            if (outPath == null) {
                llFilePath = "output.ll"
                optFilePath = "output_opt.ll"
                binaryName = "output"
            } else if (outPath.endsWith(".ll")) {
                llFilePath = outPath
                optFilePath = outPath.substring(0, outPath.length - 3) + "_opt.ll"
                binaryName = outPath.substring(0, outPath.length - 3)
            } else {
                llFilePath = outPath + ".ll"
                optFilePath = outPath + "_opt.ll"
                binaryName = outPath
            }

            // prepares the lexer with the byte input
            val lexer: src.Lexer.Lexer = src.Lexer.Lexer(input)

            // prepares the parser with the lexer and the name of the file (for linking reason)
            val parser: src.Parser.Parser = src.Parser.Parser(lexer)

            // FIX: Attach target metadata before writing the IR files and invoking
            // LLVM optimization and Clang compilation.
            val generatedIR: String? = src.Main.Main.generateModule(parser, sourceFile.toPath(), fastMath)

            // returns 1 (error) if the generation failed
            if (generatedIR == null) {
                return 1
            }

            // Writes the updated LLVM IR, including its target triple, to disk.
            val llvmIR = addTargetTriple(generatedIR)

            // creates a new file and writes the unoptimized IR
            val irFile: File = File(llFilePath)
            Files.writeString(irFile.toPath(), llvmIR)

            logger.info("Raw IR built successfully: {}", llFilePath)

            if (pgoGenerate && pgoUse != null && !pgoUse.isBlank()) {
                logger.error("Cannot use --pgo-generate and --pgo-use together")
                return 1
            }

            val optSuccess = runOptPasses(llFilePath, optFilePath)
            val toCompile = if (optSuccess) optFilePath else llFilePath
            if (!optSuccess) {
                logger.warn("opt not available, compiling the unoptimized IR")
            }

            val windows: Boolean =
                java.lang.System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")
            val exeName = if (windows && !binaryName.endsWith(".exe")) binaryName + ".exe" else binaryName

            return compileToBinary(
                File(toCompile).getAbsolutePath(),
                exeName,
                libraryPaths,
                linkLibraries,
                pgoGenerate,
                pgoUse
            )
        } catch (e: IOException) {
            logger.error("IO error during compilation", e)
            return 1
        }
    }


    companion object {
        private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(CompilerCmd::class.java)

        /**
         * I have added this method to
         * Prevents Clang's "-Woverride-module" warning by adding the
         * Existing target triples are preserved.
         */
        private fun addTargetTriple(llvmIR: String): String {
            var result = llvmIR
            if (result.lineSequence().asStream().noneMatch { line: String? -> line.startsWith("target triple =") }) {
                try {
                    val process: java.lang.Process = java.lang.ProcessBuilder("clang", "-dumpmachine")
                        .redirectErrorStream(true)
                        .start()

                    val targetTriple: String = kotlin.text.String(
                        process.getInputStream().readAllBytes(),
                        java.nio.charset.StandardCharsets.UTF_8
                    ).trim { it <= ' ' }

                    val exitCode: Int = process.waitFor()
                    if (exitCode != 0 || !targetTriple.matches("[A-Za-z0-9_.-]+".toRegex())) {
                        logger.warn("Could not determine target triple from Clang; leaving target triple unchanged")
                    } else {
                        result = "target triple = \"" + targetTriple + "\"\n\n" + result
                    }
                } catch (e: IOException) {
                    logger.warn("Failed to determine target triple from Clang; leaving target triple unchanged", e)
                } catch (e: java.lang.InterruptedException) {
                    java.lang.Thread.currentThread().interrupt()
                    logger.warn("Interrupted while determining target triple; leaving target triple unchanged", e)
                }
            }

            return addTargetDataLayout(result)
        }

        /**
         * Adds Clang's target data layout when the module does not already define one.
         * A missing or failing Clang executable is non-fatal, matching target-triple handling.
         */
        private fun addTargetDataLayout(llvmIR: String): String {
            if (llvmIR.lineSequence().asStream().anyMatch { line: String? -> line.startsWith("target datalayout =") }) {
                return llvmIR
            }

            val windows: Boolean =
                java.lang.System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")
            val nullDevice = if (windows) "NUL" else "/dev/null"
            try {
                val process: java.lang.Process = java.lang.ProcessBuilder(
                    "clang", "-S", "-emit-llvm", "-x", "c", nullDevice, "-o", "-"
                ).redirectErrorStream(true).start()

                val clangOutput: String = kotlin.text.String(
                    process.getInputStream().readAllBytes(),
                    java.nio.charset.StandardCharsets.UTF_8
                )
                val exitCode: Int = process.waitFor()
                if (exitCode != 0) {
                    logger.warn(
                        "Could not determine target data layout from Clang (exit code {}); leaving IR unchanged",
                        exitCode
                    )
                    return llvmIR
                }

                val dataLayout: String? = clangOutput.lineSequence().asStream()
                    .map<String> { obj: String? -> obj.trim { it <= ' ' } }
                    .filter { line: String? -> line.startsWith("target datalayout =") }
                    .findFirst()
                    .orElse(null)
                if (dataLayout == null || !dataLayout.startsWith("target datalayout = \"") || !dataLayout.endsWith("\"")) {
                    logger.warn("Clang output did not contain a valid target data layout; leaving IR unchanged")
                    return llvmIR
                }

                val tripleEnd: Int = llvmIR.indexOf('\n', llvmIR.indexOf("target triple ="))
                if (tripleEnd >= 0) {
                    return llvmIR.substring(0, tripleEnd + 1) + dataLayout + "\n" + llvmIR.substring(tripleEnd + 1)
                }
                return dataLayout + "\n" + llvmIR
            } catch (e: IOException) {
                logger.warn("Failed to determine target data layout from Clang; leaving IR unchanged", e)
                return llvmIR
            } catch (e: java.lang.InterruptedException) {
                java.lang.Thread.currentThread().interrupt()
                logger.warn("Interrupted while determining target data layout; leaving IR unchanged", e)
                return llvmIR
            }
        }


        /**
         * Run LLVM optimization passes using checks
         * @param inputLl LLVM IR input
         * @param outputLl LLVM IR output
         * @return true if the optimization passes were successful, false otherwise
         */
        private fun runOptPasses(inputLl: String, outputLl: String): Boolean {
            logger.info("Running LLVM Optimization passes...")
            val processBuilder: java.lang.ProcessBuilder = java.lang.ProcessBuilder(
                "opt",
                "-O3",
                "-mcpu=native",
                "-S",  // maintains a readable output
                "-enable-implicit-null-checks",
                inputLl,
                "-o",
                outputLl
            )

            processBuilder.inheritIO()

            try {
                val process: java.lang.Process = processBuilder.start()
                val exitCode: Int = process.waitFor()

                if (exitCode == 0) {
                    logger.info("Optimized IR saved to: {}", outputLl)
                    return true
                } else {
                    logger.error("Opt tool failed with exit code {}.", exitCode)
                    return false
                }
            } catch (e: IOException) {
                logger.warn("LLVM opt is unavailable; continuing with unoptimized IR", e)
                return false
            } catch (e: java.lang.InterruptedException) {
                logger.warn("LLVM opt is unavailable; continuing with unoptimized IR", e)
                return false
            }
        }

        /**
         * Wrapper for calling cLang with a try/catch block
         * @param llFilePath file path for the LLVM file
         * @param outputBinaryName name for the final binary file
         * @return 0 if the executable was produced, 1 otherwise
         */
        private fun compileToBinary(
            llFilePath: String, outputBinaryName: String, libPaths: MutableList<String?>, libFlags: MutableList<String>,
            pgoGenerate: Boolean, pgoUse: String?
        ): Int {
            logger.info("Launching Clang...")

            val processBuilder: java.lang.ProcessBuilder =
                createProcessBuilder(llFilePath, outputBinaryName, libPaths, libFlags, pgoGenerate, pgoUse)

            try {
                val process: java.lang.Process = processBuilder.start()
                val exitCode: Int = process.waitFor()

                if (exitCode == 0) {
                    logger.info("Executable created: ./{}", outputBinaryName)
                    return 0
                } else {
                    logger.error("Error during compilation. Exit code: {}", exitCode)
                    return 1
                }
            } catch (e: IOException) {
                logger.error("Failed to launch Clang", e)
                return 1
            } catch (e: java.lang.InterruptedException) {
                logger.error("Failed to launch Clang", e)
                return 1
            }
        }

        /**
         * invokes Clang to create the final binary
         * @param llFilePath path for the LLVM file
         * @param outputBinaryName name for the final binary file
         * @return a ProcesssBuilder object
         */
        private var runtimeObjectPath: String? = null

        private fun runtimeObjectPath(): String? {
            if (runtimeObjectPath != null) {
                return runtimeObjectPath
            }
            try {
                CompilerCmd::class.java.getResourceAsStream("/z_runtime.o").use { input ->
                    if (input == null) {
                        return null
                    }
                    val temp: Path = Files.createTempFile("z_runtime", ".o")
                    Files.copy(input, temp, java.nio.file.StandardCopyOption.REPLACE_EXISTING)
                    temp.toFile().deleteOnExit()
                    runtimeObjectPath = temp.toAbsolutePath().toString()
                    return runtimeObjectPath
                }
            } catch (e: IOException) {
                logger.warn("Could not extract runtime object", e)
                return null
            }
        }

        private fun createProcessBuilder(
            llFilePath: String,
            outputBinaryName: String,
            libPaths: MutableList<String?>,
            libFlags: MutableList<String>,
            pgoGenerate: Boolean,
            pgoUse: String?
        ): java.lang.ProcessBuilder {
            val command: MutableList<String?> = java.util.ArrayList<String?>()
            val windows: Boolean =
                java.lang.System.getProperty("os.name").lowercase(Locale.getDefault()).contains("win")

            command.add("clang")
            command.add("-O3")
            command.add("-flto")
            if (windows) {
                // Windows Clang uses the MSVC-style linker driver; explicitly select LLD.
                command.add("-fuse-ld=lld")
            }
            command.add("-march=native")
            command.add("-funroll-loops")
            command.add("-fno-rtti")
            command.add("-fno-exceptions")
            command.add("-mtune=native")
            if (!windows) {
                // These options are specific to ELF/GNU-style toolchains.
                command.add("-fno-plt")
                command.add("-fno-semantic-interposition")
                command.add("-Wl,--gc-sections")
                command.add("-Wl,-O2")
            }
            command.add("-fvisibility=hidden")
            command.add("-fstrict-aliasing")
            if (pgoGenerate) {
                command.add("-fprofile-generate")
            }
            if (pgoUse != null && !pgoUse.isBlank()) {
                command.add("-fprofile-use=" + pgoUse)
            }
            command.add(llFilePath)
            val runtimeObject = runtimeObjectPath()
            if (runtimeObject != null) {
                command.add(runtimeObject)
            }
            command.add("-o")
            command.add(outputBinaryName)

            for (path in libPaths) {
                command.add("-L" + path)
            }

            for (lib in libFlags) {
                command.add(if (lib.startsWith("-l")) lib else "-l" + lib)
            }

            // The Windows C runtime provides the standard math functions; there is no m.lib.
            if (!windows) {
                command.add("-lm")
            }

            val processBuilder: java.lang.ProcessBuilder = java.lang.ProcessBuilder(command)

            processBuilder.inheritIO()
            return processBuilder
        }
    }
}

/**
 * Main class, nothing to say
 */
object Main {
    private val logger: org.slf4j.Logger = org.slf4j.LoggerFactory.getLogger(Main::class.java)

    @JvmStatic
    fun main(args: Array<String>) {
        val exitCode: Int = picocli.CommandLine(CompilerCmd()).execute(*args)
        java.lang.System.exit(exitCode)
    }


    /**
     * Generates the module
     * @param parser parser class
     * @param sourceFile in which the code module is contained
     * @param fastMath whether to enable aggressive fast-math optimizations
     * @return only the globals externed or also the body (main) or null if an error is present
     * @throws IOException if an exception is found
     */
    /**
     * Generates the module
     * @param parser parser class
     * @param sourceFile in which the code module is contained
     * @return only the globals externed or also the body (main) or null if an error is present
     * @throws IOException if an exception is found
     */
    @JvmOverloads
    @Throws(IOException::class)
    fun generateModule(parser: src.Parser.Parser, sourceFile: Path, fastMath: Boolean = false): String? {
        StructRegistry.clear()

        // prepares classes
        val builder: IRBuilder = IRBuilder()
        builder.setFastMathFlags(if (fastMath) "fast" else "contract")

        val module: java.lang.StringBuilder = java.lang.StringBuilder()

        // gets the name of the file removing the final .z
        val fileNamePath: Path? = sourceFile.getFileName()
        val rootName = if (fileNamePath != null) fileNamePath.toString().replaceFirst("\\.z$".toRegex(), "") else ""

        // parses an entire module
        val root: ModuleAST? = ModuleLoader.Companion.parseUnit(parser, rootName)
        if (root == null) return null // error


        val fromDir: Path? = sourceFile.toAbsolutePath().getParent()
        val dependencies: MutableList<ModuleAST>? = ModuleLoader(rootName).loadAll(root.imports(), fromDir)
        if (dependencies == null) return null

        for (st in StructRegistry.all()) {
            builder.declareStruct(st)
        }

        for (dependency in dependencies) {
            if (!src.Main.Main.emitModule(dependency, builder, module)) return null
        }
        builder.setCurrentModule(null)

        for (extern in root.externals()) {
            val externLLVMTextUnit: LLVMTextUnit? = extern.codeGen(builder)
            if (externLLVMTextUnit == null || builder.hasFailed()) {
                src.Main.Main.logger.error("Error building the IR of the externs")
                return null
            }
            module.append(externLLVMTextUnit.text)
        }

        for (`var` in root.vars()) {
            `var`.codeGen(builder)
            if (builder.hasFailed()) {
                src.Main.Main.logger.error("Error building the globals")
                return null
            }
        }

        if (src.Main.Main.declareAll(root, builder)) return null

        for (definition in root.functions()) {
            val function: LLVMTextUnit? = definition.codeGen(builder)
            if (function == null || builder.hasFailed()) {
                src.Main.Main.logger.error("Error building the IR")
                return null
            }
            module.append(function.text)
        }

        if (module.isEmpty()) {
            src.Main.Main.logger.error("Syntax error")
            return null
        }

        val body = module.toString()
        val globals: String = builder.getGlobals()
        return if (globals.isEmpty()) body else globals + "\n" + body
    }

    private fun emitModule(module: ModuleAST, builder: IRBuilder, unit: java.lang.StringBuilder): Boolean {
        builder.setCurrentModule(module.name())

        for (extern in module.externals()) {
            val externLLVMTextUnit: LLVMTextUnit? = extern.codeGen(builder)
            if (externLLVMTextUnit == null || builder.hasFailed()) {
                if (src.Main.Main.logger.isErrorEnabled()) {
                    src.Main.Main.logger.error("Error building the IR of the externs of module '{}'", module.name())
                }
                return false
            }
            unit.append(externLLVMTextUnit.text)
        }

        for (`var` in module.vars()) {
            `var`.codeGen(builder)
            if (builder.hasFailed()) {
                src.Main.Main.logger.error("Error building the globals of module '{}'", module.name())
                return false
            }
        }

        for (definition in module.functions()) {
            if (definition.prototype().isMain()) {
                if (src.Main.Main.logger.isErrorEnabled()) {
                    src.Main.Main.logger.error(
                        "Error: 'main' can only be defined in the root file, not in module '{}'",
                        module.name()
                    )
                }
                return false
            }
        }

        if (src.Main.Main.declareAll(module, builder)) return false

        for (definition in module.functions()) {
            val function: LLVMTextUnit? = definition.codeGen(builder)
            if (function == null || builder.hasFailed()) {
                if (src.Main.Main.logger.isErrorEnabled()) {
                    src.Main.Main.logger.error("Error building the IR of module '{}'", module.name())
                }
                return false
            }
            unit.append(function.text)
        }
        return true
    }

    private fun declareAll(module: ModuleAST, builder: IRBuilder): Boolean {
        for (definition in module.functions()) {
            if (!builder.declare(definition.prototype())) {
                if (src.Main.Main.logger.isErrorEnabled()) {
                    src.Main.Main.logger.error(
                        "Error: '{}' is defined more than once",
                        builder.qualify(definition.prototype().name)
                    )
                }
                return true
            }
        }
        return false
    }
}