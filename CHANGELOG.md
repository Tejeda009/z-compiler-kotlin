# Changelog

All notable changes to Z-Compiler are documented in this file.

## [0.0.17-rc.1] - 2026-11-09

### Tests

- Test(codegen): update expected entry-block alloca output
- Test(codegen): expect local allocas in entry block
- Test(codegen): assert allocas in entry buffer
- Test(codegen): cover entry allocas and unique slot names
- Test(stdlib): make math linker assertion platform-aware
- Test(compiler): account for optional LLVM opt tool
- Test: support platform-specific compiler output
- Test: fix target metadata string escaping
- Test(llvm): import equality assertion
- Test(llvm): cover target data layout metadata (#222)

### Bug Fixes

- Fix(codegen): emit entry allocas before function body
- Fix(codegen): collect allocas in function entry block (#223)
- Fix: use Windows-compatible Clang linker flags
- Fix(llvm): correct target metadata newline handling
- Fix(llvm): emit target data layout in IR (#222)

### Documentation

- Docs: update changelog for v0.0.16-rc.1

## [0.0.16-rc.1] - 2026-11-06

### Features

- Added !tbaa, nsw, tail calls, inlining, branch weights, llvm.assume, fast-math, more extern attributes and clang flags, added optional --pgo flag

### Documentation

- Docs: update changelog for v0.0.15-rc.1

### Other Changes

- Just changed the name of a class

## [0.0.3] - 2026-11-04

### Features

- Add noalias support for mutable pointer parameters
- Add optional LLVM fast-math support

## [0.0.15-rc.1] - 2026-11-03

### Documentation

- Docs: update changelog for v0.0.14-rc.1

## [0.0.14-rc.1] - 2026-11-03

### Features

- Added real benchmark system
- Feat: added align system for more performance

### Documentation

- Docs: update changelog for main

### Other Changes

- Idk
- Gitignore for md lint
- Improve a lot performance
- Remove extra newline in README.md

## [0.0.13-rc.1] - 2026-11-03

### Bug Fixes

- Fix #170: fat strings with O(1) length
- Fix #209: bump allocator for string concatenation

### Documentation

- Docs: update changelog for v0.0.2
- Docs: update changelog for v0.0.12-rc.1

### Other Changes

- Enhance README with more badges

## [0.0.2] - 2026-11-02

### Documentation

- Docs: update changelog for v0.0.11-rc.1

### Other Changes

- Change linking from private to internal

## [0.0.11-rc.1] - 2026-11-02

### Features

- Added noalias in pointers
- Added tests

### Documentation

- Docs: update changelog for v0.1.0-rc.1

### Maintenance

- Deps: bump the maven-minor-and-patch group with 4 updates

### Other Changes

- Mcpu=native added to llvm opt passes

## [0.1.0-rc.1] - 2026-11-01

### Features

- Added variadic parameters for functions

### Refactoring

- Refactor accessibility documentation for clarity

### Documentation

- Docs: update changelog for v0.0.10-rc.1

### Other Changes

- Revise accessibility guidelines and contributor expectations

## [0.0.10-rc.1] - 2026-11-01

### Documentation

- Docs: update changelog for main

### Other Changes

- Complete parser refactor
- Formatting and compiler warnings
- Security boring alerts

## [0.0.9-rc.1] - 2026-10-31

### Features

- Add break and continue keywords (#169)

### Bug Fixes

- Fix email configuration for GitHub Actions bot
- Fix email configuration for GitHub Actions tag creation
- Fix release workflow manual tag validation fallback
- Fix release workflow tag SHA lookup for missing tags
- Fix release workflow tag rev-parse for actionlint
- Fix git fetch command in release workflow
- Fix tag retrieval and error message in release workflow
- Fix YAML lint formatting in release workflow
- Fix #175: add struct types, literals and field access
- Fix #138: add examples and syntax to README
- Fix missing LLVM target triple warning (#167)

### Documentation

- Docs: update changelog for v0.0.8-rc.1

### Other Changes

- Update release workflow with permissions and credentials
- Initial plan
- Initial plan
- Remove extra spaces in release.yml
- Enhance release workflow with manual trigger and validations
- Update release.yml

## [0.0.8-rc.1] - 2026-10-26

### Features

- Added labels for new folders
- Added functions in stdlib and added tests
- Add a std library with println (#133)
- Add documentation section to README
- Add ZType documentation

### Bug Fixes

- Fixed failed test
- Fix formatting and improve clarity in Ztype.md

### Documentation

- Docs: update changelog for v0.0.7-rc.1

### CI/CD & Build

- Ci: bump codecov/codecov-action from 5 to 7 in the github-actions group

### Maintenance

- Deps: bump the maven-minor-and-patch group with 6 updates

### Other Changes

- Should fix null references
- Should fix some code scanning alerts
- Should be fixed now

## [0.0.7-rc.1] - 2026-10-25

### Features

- Add changelog section to README

### Bug Fixes

- FIX: fix version of acquasecurity for the release workflows
- Fix #143: add multi-line comments to the lexer

### Documentation

- Docs: update changelog for v0.0.6-rc.1

### CI/CD & Build

- CI: added workflows and removed useless one

### Other Changes

- Update Discord link in README

## [0.0.5-rc.1] - 2026-10-25

### Features

- Added tests
- Add void support and 281 easter egg
- Add codecov project checks
- Add codecov config
- Add examples section to README

### Bug Fixes

- Fix formatting in README.md

## [0.0.4-rc.1] - 2026-10-25

### Features

- Add block scoping regression tests
- Added examples and tests
- Added support for varargs in extern functions

### Bug Fixes

- Fix block scoping
- Bug fixes and added tests

### Refactoring

- Renamed lexer package Lexer

### Other Changes

- Fk
- Nothing
- Array implementation: indexing (only supports numbers, no expressions). TODO: finish AST, add support for expressions when indexing
- Array implementation: indexing (only supports numbers, no expressions). TODO: finish AST, add support for expressions when indexing
- Array implementation: arrayDepth (for matrixes) support for Parser and analog changes in ArrayAST class. TODO: finish AST, and add array indexing
- Array implementation: Declaration parsing and ArrayAST template. TODO: finish AST, and add array indexing
- Simone zotti 281 aura sigma zotttttttt

## [0.0.3-rc.1] - 2026-10-24

### Features

- Add benchmarks section to README

### Bug Fixes

- Bug fixes, operators and benchmarks
- Fix #94: add Go-style pointer parameters

### Other Changes

- Correct 'benchmakrks' to 'benchmarks'

## [0.0.2-rc.1] - 2026-10-23

### Other Changes

- I'm a complete idiot
- Is it done now?
- Should be done
- Partial implementation

## [0.0.1-rc.1] - 2026-10-19

### Tests

- Tests and bug fixes

### Features

- Add deployements
- Added logging, fixed bugs and IDE warnings, comments
- Add GitHub Actions workflow for greetings
- Added tests and fixed bugs for binding extern functions
- Added untested benchmarks
- Implemented benchmark system and reassignment of variables.
- Add Lexer documentation
- Add permissions to workflows to fix security notification
- Added tests for constant, revised constant codegen
- Adding Javdocs literally everywhere
- Adding final keyword here and there
- Added simple test for codegen
- Add cn constant token, AST node, parser and tests
- Add cn constant token, AST node, parser and tests
- Add CODEOWNERS file with Tejeda009 as owner
- Add code of conduct for Z Compiler community
- Add pull request template for contributions
- Add feature request issue template
- Add contribution guidelines to CONTRIBUTING.md
- Add GNU Lesser General Public License
- Add GitHub Super Linter workflow
- Add CodeQL analysis workflow configuration
- Add stale.yml workflow for managing stale issues and PRs
- Add GitHub Actions workflow for Linux binary release
- Add CI workflow for Java build and test
- Add initial Maven POM configuration
- Add Discord link to README

### Bug Fixes

- Fix?
- Fix #97: add declared return types, including str
- Fixed CI/CD????? idk
- Fix #85: add string literals and concatenation
- Fix #63: add uint32, uint64 and str types
- Fix #2: add if/eif/else conditional statements
- Bug fixes, test fixes, added tests
- Fix #24: add for loop support
- Fix formatting and punctuation in lexer documentation
- Fix #42: parse parameter types and generate correct call IR
- Fix #49: emit alloca for variable declarations and use i32 for integer returns
- Fix bugs, add test
- Bug fixes, scaffolding for types. almost done variables implementation
- Bug fixes and scaffolding for next implementations
- Bug fixes. ig it was already fixed lol
- Bug fixes and hopefully a parser implementation
- Fix lexer tokenization, assignment operator, and comments

### Refactoring

- Move everything to src

### Documentation

- Docs: add lexer documentation

### CI/CD & Build

- Ci: bump the github-actions group with 2 updates
- CI/CD, formatting

### Maintenance

- Deps: bump the maven-minor-and-patch group with 4 updates

### Other Changes

- Hopefully a fix
- Bait
- Hopefully a fix
- Finally a working linking system Co-authored-by: my stupid brain
- Bump kotlin.version from 2.4.0 to 2.4.21
- Really partial implementation
- Clang circuit breaker fix
- Finally fucking done it (i hope so)
- Ik this doesn't work
- Complete refactoring, file division, cleaned code, bug fixing
- Gitignore fix for build artifacts
- Delete src/lexer/lexer-docs.md
- Delete z-compiler.iml
- Aura opt tools
- Fk merge conflicts again
- Fk merge conflicts
- Restore Javadoc on PrototypeAST after refactor
- Remove useless AST.iml
- Codegen bug fixes and partial implementation
- Wth is happening git?
- Fk tests and whitespaces
- Nothing seems to work, just how i like it
- Scaffolding for types, almost implementation of variables with stack allocation
- Bump org.junit:junit-bom from 5.11.0 to 6.1.3
- It can't be so difficult
- Why in the world maven?
- Basic CLI support
- Files
- Files
- Main Implementation with CLang. CodeGen for functions without types and simple binary operations. Bug fixes
- Scaffolding for code generation
- Update README.md
- Handle unknown error case in Parser
- Create codebase improvement issue template
- Create bug report issue template
- Revise vulnerability reporting instructions in SECURITY.md
- Bump org.graalvm.buildtools:native-maven-plugin from 0.10.1 to 1.1.14
- Bump org.apache.maven.plugins:maven-surefire-plugin from 3.2.5 to 3.6.0
- Bump org.apache.maven.plugins:maven-jar-plugin from 3.3.0 to 3.5.1
- Create dependabot.yml
- Remove unused import statement from Main.java
- Update sourceDirectory in pom.xml
- Ik it doesn't work. it's unfinished
- Nothing special
- May be a partial lexer
- Idk what this shit is
- Lexer ig
