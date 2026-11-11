package Tests

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

class TestModuleLoader {
    @TempDir
    var directory: Path? = null

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testLoadsTheStandardLibraryWithoutADirectory() {
        val modules: MutableList<ModuleAST?>? =
            ModuleLoader("root").loadAll(java.util.List.of<ImportAST?>(ImportAST("std")), null)

        Assertions.assertNotNull(modules)
        Assertions.assertEquals(1, modules.size())
        Assertions.assertEquals("std", modules.getFirst().name())
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMissingModulesAreRejected() {
        Assertions.assertNull(
            ModuleLoader("root").loadAll(
                java.util.List.of<ImportAST?>(ImportAST("absent")),
                directory
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMalformedModulesAreRejected() {
        Files.writeString(directory.resolve("broken.z"), "42")

        Assertions.assertNull(
            ModuleLoader("root").loadAll(
                java.util.List.of<ImportAST?>(ImportAST("broken")),
                directory
            )
        )
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testMalformedImportsExternsAndDefinitionsAreRejected() {
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("import 42"), "m"))
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("extern"), "m"))
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("proc"), "m"))
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("struct"), "m"))
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("int32"), "m"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testUnexpectedTopLevelTokensAreRejected() {
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("42"), "m"))
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser("+"), "m"))
        Assertions.assertNull(ModuleLoader.Companion.parseUnit(parser(";"), "m"))
    }

    @org.junit.jupiter.api.Test
    @Throws(IOException::class)
    fun testTopLevelDeclarationsAreCollected() {
        val module: ModuleAST? = ModuleLoader.Companion.parseUnit(
            parser("extern sqrt(flt64) -> flt64\nstruct Point { int32 x }\nint32 g -> 1\nproc f() { ret 1 }"),
            "m"
        )

        Assertions.assertNotNull(module)
        Assertions.assertEquals(1, module.externals().size())
        Assertions.assertEquals(1, module.vars().size())
        Assertions.assertEquals(1, module.functions().size())
    }

    companion object {
        @Throws(IOException::class)
        private fun parser(source: String): src.Parser.Parser {
            return ZFixtures.parserFor(source)
        }
    }
}
