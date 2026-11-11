# Lexer Documentation

## Overview

### What is the Lexer?
The Lexer is the first component that receives the code written by the user.

if the user write : x -> 10 + 20

The Lexer does not care about the meaning of the statement or whether it is logically correct at this stage.
Its job is simply to say:
“I see certain parts of the text, and I identify what each part is called.”

```text
x    ->        10       +       20
│    │          │       │        │
ID   Assignment NUMBER  ADD    NUMBER
```

### What is the Token?
A Token tells us what type of element a part of the source code is(The Lexer divides the source code into lexemes and identifies the Token type of each lexeme.)

```text
user code
↓
(by the Lexer)
↓
Tokens
```

## Lexer Class

### Attributes

#### `LastChar`
-**type** `int`
-**purpose :** it is used to store the last char.

#### `identifierStr`
-**type** `String`
-**purpose :** Stores the text of an identifier, such as a variable or function name.

#### `NumVal`
-**type** `double`
-**purpose :**Stores the value of a number token, such as `10` or `10.5`.

#### `input`
- **type** `InputStream`
- **purpose :** Stores the input source that the Lexer reads from.

#### `keywordMap`
- **type :** `Map<String, Tokens>`
- **purpose :** Maps strings such as `"if"`, `"for"`, and `"+"` to their corresponding Tokens.

### Constructor

#### `Lexer(InputStream input)`
- **Parameter:** `InputStream input`
- **Purpose :** Initializes the Lexer with the input source that will be read by the Lexer.

### Methods
#### `GetTok()`
- **Return type:** `int`
- **Throws:** `IOException`
- **Purpose :** Reads the next element from the input and identifies its corresponding Token type.
- Skips whitespace.
- Checks for the end of the input.
- Identifies identifiers, numbers, operators, comments, and symbols.
- Returns the corresponding Token value.

## `Tokens` Enum

### What is `Tokens`?
`Tokens` is an enum that defines the different token types recognized by the Lexer.

### Attributes

#### `description`
- **Type:** `String`
- **Purpose :** Stores the text associated with the token.

#### `value`
- **Type:** `int`
- **Purpose :** Stores the integer value used to represent the token.

### Constructor

#### `Tokens(String description, int value)`

- **Parameters:** `String description`, `int value`
- **Purpose :** Initializes each token with its description and integer value.
- The constructor is called automatically by Java when the enum constants are initialized.

### Enum Initialization

The `Tokens` enum is initialized when it is first used.

In the `Lexer`, the `static` block uses `Tokens.values()` to initialize the `keywordMap`. This causes the enum constants to be initialized, and the enum constructor is called automatically for each constant.

For example:

`FUNC("proc", -1)` initializes the `FUNC` token with:
- `description = "proc"`
- `value = -1`

The enum constants are initialized only once.

### Enum Fields

| Token | Description | Value |
|---|---|---|
| `FUNC` | `proc` | `-1` |
| `INT64` | `int64` | `-2` |
| `NUMBER` | `unused` | `-3` |
| `RET` | `ret` | `-4` |
| `INT32` | `int32` | `-5` |
| `FLOAT32` | `flt32` | `-6` |
| `FLOAT64` | `flt64` | `-7` |
| `STRING` | `str` | `-8` |
| `BOOL` | `01` | `-9` |
| `UINT32` | `uint32` | `-10` |
| `UINT64` | `uint64` | `-11` |
| `MUL` | `*` | `-12` |
| `DIV` | `/` | `-13` |
| `ADD` | `+` | `-14` |
| `SUB` | `-` | `-15` |
| `ASSIGN` | `->` | `-30` |
| `IF` | `if` | `-16` |
| `EIF` | `eif` | `-17` |
| `ELSE` | `else` | `-18` |
| `FOR` | `for` | `-19` |
| `OPEN_PAR` | `(` | `-20` |
| `CLOSE_PAR` | `)` | `-21` |
| `OPEN_FUNC` | `{` | `-22` |
| `CLOSE_FUNC` | `}` | `-23` |
| `LESS` | `<` | `-24` |
| `MORE` | `>` | `-25` |
| `LE` | `<=` | `-38` |
| `GE` | `>=` | `-39` |
| `IMPORT` | `import` | `-26` |
| `ARRAY_OPEN` | `[` | `-27` |
| `ARRAY_CLOSE` | `]` | `-28` |
| `PARAMS` | `\|` | `-29` |
| `IDENTIFIER` | `id` | `-34` |
| `EOF` | `eof` | `-31` |
| `SAME` | `=` | `-32` |
| `EQ` | `==` | `-41` |
| `NE` | `!=` | `-42` |
| `MOD` | `%` | `-43` |
| `TRUE` | `true` | `-44` |
| `FALSE` | `false` | `-45` |
| `CN` | `cn` | `-35` |

### Methods

#### `fromValues(int value)`

- **Return type:** `Tokens`
- **Parameter:** `int value`
- **Purpose :** Finds and returns the token whose `value` matches the given integer.
