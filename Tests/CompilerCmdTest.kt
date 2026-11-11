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

class CompilerCmdTest {
    @TempDir
    var directory: Path? = null

    @Throws(java.lang.Exception::class)
    private fun optAvailable(): Boolean {
        try {
            val process: java.lang.Process = java.lang.ProcessBuilder("opt", "--version")
                .redirectErrorStream(true)
                .redirectOutput(java.lang.ProcessBuilder.Redirect.DISCARD)
                .start()
            return process.waitFor() == 0
        } catch (e: IOException) {
            return false
        }
    }

    @Throws(java.lang.Exception::class)
    private fun source(): Path {
        val file: Path = directory.resolve("main.z")
        Files.writeString(file, "proc main() { ret 0 }")
        return file
    }

    private fun binary(name: String): Path {
        val windows: Boolean = java.lang.System.getProperty("os.name").toLowerCase().contains("win")
        return directory.resolve(if (windows) name + ".exe" else name)
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testCompilesToADefaultNamedBinary() {
        val out: Path = directory.resolve("program")

        Assertions.assertEquals(0, run("-o", out.toString(), source().toString()))
        Assertions.assertTrue(Files.isRegularFile(binary("program")))
        Assertions.assertTrue(Files.isRegularFile(directory.resolve("program.ll")))
        Assertions.assertEquals(optAvailable(), Files.isRegularFile(directory.resolve("program_opt.ll")))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testCompilesWithAnLlOutputNameAndLibraries() {
        val out: Path = directory.resolve("program.ll")

        val exitCode = if (java.lang.System.getProperty("os.name").toLowerCase().contains("win"))
            run("-o", out.toString(), "-L", directory.toString(), source().toString())
        else
            run("-o", out.toString(), "-L", directory.toString(), "-l", "m", "-l", "z", source().toString())
        Assertions.assertEquals(0, exitCode)
        Assertions.assertTrue(Files.isRegularFile(out))
        Assertions.assertEquals(optAvailable(), Files.isRegularFile(directory.resolve("program_opt.ll")))
        Assertions.assertTrue(Files.isRegularFile(binary("program")))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testDefaultOutputNameStopsBeforeWritingWhenParsingFails() {
        val bad: Path = directory.resolve("bad.z")
        Files.writeString(bad, "")

        Assertions.assertEquals(1, run(bad.toString()))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testWriteFailureReturnsOne() {
        val blocker: Path = directory.resolve("blocker")
        Files.writeString(blocker, "not a directory")

        Assertions.assertEquals(1, run("-o", blocker.resolve("program").toString(), source().toString()))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testWindowsAddsTheExeSuffix() {
        val previous: String = java.lang.System.getProperty("os.name")
        val out: Path = directory.resolve("windows")
        try {
            java.lang.System.setProperty("os.name", "Windows 10")
            Assertions.assertEquals(0, run("-o", out.toString(), source().toString()))
            Assertions.assertTrue(Files.isRegularFile(directory.resolve("windows.exe")))
        } finally {
            java.lang.System.setProperty("os.name", previous)
        }
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testAddTargetTriplePreservesAnExistingTriple() {
        val method: java.lang.reflect.Method =
            CompilerCmd::class.java.getDeclaredMethod("addTargetTriple", String::class.java)
        method.setAccessible(true)
        val existing = "target triple = \"x86_64-pc-linux-gnu\"\ndefine i32 @main() { ret i32 0 }\n"

        val result = method.invoke(null, existing) as String
        Assertions.assertTrue(result.startsWith("target triple = \"x86_64-pc-linux-gnu\""))
        Assertions.assertTrue(result.contains("target datalayout = \""), result)
        Assertions.assertTrue(result.contains("define i32 @main() { ret i32 0 }"), result)
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testAddTargetTripleUsesClang() {
        val method: java.lang.reflect.Method =
            CompilerCmd::class.java.getDeclaredMethod("addTargetTriple", String::class.java)
        method.setAccessible(true)
        val generated = method.invoke(null, "define i32 @main() { ret i32 0 }\n") as String

        Assertions.assertTrue(generated.startsWith("target triple = \""), generated)
        Assertions.assertTrue(generated.contains("define i32 @main()"))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testOptFailureIsReported() {
        val method: java.lang.reflect.Method =
            CompilerCmd::class.java.getDeclaredMethod("runOptPasses", String::class.java, String::class.java)
        method.setAccessible(true)

        Assertions.assertEquals(
            false, method.invoke(
                null, directory.resolve("missing.ll").toString(),
                directory.resolve("out.ll").toString()
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testClangFailureIsReported() {
        val method: java.lang.reflect.Method = CompilerCmd::class.java.getDeclaredMethod(
            "compileToBinary",
            String::class.java,
            String::class.java,
            MutableList::class.java,
            MutableList::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java
        )
        method.setAccessible(true)

        Assertions.assertEquals(
            1,
            method.invoke(
                null,
                directory.resolve("missing.ll").toString(),
                directory.resolve("binary").toString(),
                java.util.List.of<Any?>(),
                java.util.List.of<Any?>(),
                false,
                null
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testCreateProcessBuilderBuildsTheFullCommand() {
        val method: java.lang.reflect.Method = CompilerCmd::class.java.getDeclaredMethod(
            "createProcessBuilder",
            String::class.java,
            String::class.java,
            MutableList::class.java,
            MutableList::class.java,
            Boolean::class.javaPrimitiveType,
            String::class.java
        )
        method.setAccessible(true)
        val builder: java.lang.ProcessBuilder = method.invoke(
            null,
            "in.ll",
            "binary",
            java.util.List.of<String?>("/opt/lib"),
            java.util.List.of<String>("m", "-lz"),
            false,
            null
        ) as java.lang.ProcessBuilder
        val command: MutableList<String?> = builder.command()

        Assertions.assertEquals("clang", command.getFirst())
        Assertions.assertTrue(command.contains("-O3"))
        Assertions.assertTrue(command.contains("in.ll"))
        Assertions.assertTrue(command.contains("-o"))
        Assertions.assertTrue(command.contains("binary"))
        Assertions.assertTrue(command.contains("-L/opt/lib"))
        Assertions.assertTrue(command.contains("-lm"))
        Assertions.assertTrue(command.contains("-lz"))
        val windows: Boolean = java.lang.System.getProperty("os.name").toLowerCase().contains("win")
        Assertions.assertEquals(
            (if (windows) 1 else 2).toLong(),
            command.stream().filter(java.util.function.Predicate { anObject: String? -> "-lm".equals(anObject) })
                .count()
        )
        Assertions.assertEquals(windows, command.contains("-fuse-ld=lld"))
        Assertions.assertEquals(!windows, command.contains("-fno-plt"))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testCompilesAnExternalModule() {
        Files.writeString(directory.resolve("math.z"), "proc square(int32 x) { ret x * x }\n")
        val main: Path = directory.resolve("uses.z")
        Files.writeString(main, "import math\nproc main() { ret math.square(3) }\n")

        Assertions.assertEquals(0, run("-o", directory.resolve("uses").toString(), main.toString()))
        Assertions.assertTrue(Files.isRegularFile(binary("uses")))
    }

    @org.junit.jupiter.api.Test
    @Throws(java.lang.Exception::class)
    fun testCompilesWithFastMath() {
        val out: Path = directory.resolve("program_fast")
        val file: Path = directory.resolve("fast_main.z")
        Files.writeString(file, "proc main() { ret 1.5 + 2.5 }\n")
        Assertions.assertEquals(0, run("-fast-math", "-o", out.toString(), file.toString()))
        Assertions.assertTrue(Files.isRegularFile(binary("program_fast")))
        val rawLl: String = Files.readString(directory.resolve("program_fast.ll"))
        Assertions.assertTrue(rawLl.contains("fadd fast double 1.5, 2.5"), rawLl)
    }

    companion object {
        private fun run(vararg args: String?): Int {
            return picocli.CommandLine(CompilerCmd()).execute(*args)
        }
    }
}
