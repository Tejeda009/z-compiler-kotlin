# Ztype Documentation

## Overview

## Ztype enum :
### What is the Ztype?
Represents the types supported by the Z language and provides information and operations for working with their corresponding LLVM types.

### What is the target of Ztype enum?

* Represents the types supported by the Z language and their
corresponding LLVM types.

* Each enum constant stores the type's name in Z, its corresponding LLVM
type, its initial value, its bit width, and its category.

* The enum also provides methods to classify types, convert values into
literals, unify types, and handle pointer types.

#### What is the Kind enum ?

* An inner enum that specifies the family (category) each type belongs to.

#### Ztype fields:
##### zName -> Stores the name of the type in the Z language
##### llvm  -> Stores the corresponding LLVM type
##### initial -> Stores the initial value for a variable that has not been assigned
##### bits -> Stores the width of the type in bits
##### kind -> Stores the category the type belongs to, such as SIGNED, UNSIGNED, or FLOAT

#### ZType constructer:
Initializes each enum constant with its corresponding type information
and assigns the values to the appropriate fields.

#### ZType methods:
##### `zName()`
* **Purpose:** Returns the name of the type in the Z language.
* **Why:** Allows other parts of the compiler to access the type's original name.

##### `llvm()`
* **Purpose:** Returns the corresponding LLVM type.
* **Why:** Allows the compiler to determine how the type is represented in LLVM.

##### `initial()`
* **Purpose:** Returns the initial value for a variable that has not been assigned.
* **Why:** Provides the value used to initialize an unassigned variable.

##### `bits()`
* **Purpose:** Returns the width of the type in bits.
* **Why:** Provides information about the size of the type.

##### `isIntegral()`
* **Purpose:** Checks whether the type is a signed integer, unsigned integer, or boolean.
* **Why:** Allows the compiler to identify integral types when processing expressions.

##### `isFloat()`
* **Purpose:** Checks whether the type is a floating-point type.
* **Why:** Allows the compiler to handle floating-point types appropriately.

##### `isSigned()`
* **Purpose:** Checks whether the type is a signed integer.
* **Why:** Helps the compiler choose the appropriate operations for signed values.

##### `isUnsigned()`
* **Purpose:** Checks whether the type is an unsigned integer.
* **Why:** Helps the compiler choose the appropriate operations for unsigned values.

##### `isBoolean()`
* **Purpose:** Checks whether the type is a boolean.
* **Why:** Allows the compiler to distinguish boolean values from other types.

##### `isString()`
* **Purpose:** Checks whether the type represents a string.
* **Why:** Allows the compiler to identify and handle string values.

##### `isVoid()`
* **Purpose:** Checks whether the type is void.
* **Why:**  Allows the compiler to determine whether a function returns a value

##### `fromZName(String name)`
* **Purpose:** Finds and returns the `ZType` that matches the given name in the Z language.
* **Why:** Allows the compiler to convert a type name written in the source code into its corresponding `ZType`.
* **Exception:** Throws `IllegalArgumentException` if the name does not match any supported type.

##### `isType(String name)`
* **Purpose:** Checks whether the given name matches a supported type in the Z language.
* **Why:** Allows the compiler to validate type names before using them.
* **Returns:** `true` if the name matches a supported type; otherwise, `false`.

##### `unify(ZType a, ZType b)`
* **Purpose:** Determines the resulting type when two operands are combined.
* **Why:** Ensures that both operands can be processed using a compatible type.
* **Behavior:** Handles null and void types, validates boolean, pointer, and string combinations, and selects an appropriate type for floating-point and integer operands.
* **Exception:** Throws `IllegalArgumentException` when the operand types cannot be combined.

###### Handling null types
* **Purpose:** Returns the non-null type when one of the operands is null.
* **Why:** Allows type unification to proceed when only one operand has a defined type.

###### Handling identical types
* **Purpose:** Returns the type directly when both operands have the same type.

###### Handling void types
* **Purpose:** Returns the other operand's type when one operand is void.
* **Why:** Allows the non-void type to be selected when paired with a void type.

###### Handling boolean types
* **Purpose:** Returns `BOOL` when both operands are boolean.
* **Exception:** Throws `IllegalArgumentException` when a boolean operand is combined with a different type.

###### Handling pointer types
* **Purpose:** Returns the type when both operands have the same pointer type.
* **Why:** Allows matching pointer types to be combined.
* **Exception:** Throws `IllegalArgumentException` when the pointer types differ or a pointer is combined with a non-matching type.

###### Handling string types
* **Purpose:** Rejects combinations involving a string operand.
* **Why:** Prevents a string type from being combined with any other type.
* **Exception:** Throws `IllegalArgumentException` when either operand is a string.

###### Handling two floating-point types
* **Purpose:** Returns the floating-point type with the greater bit width.
* **Why:** Selects the wider type to accommodate both operands.

###### Handling floating-point and integer types
* **Purpose:** Returns the floating-point type unless the integer has a greater bit width, in which case it returns `FLT64`.
* **Why:** Selects a floating-point type capable of accommodating the integer operand.

###### Handling integer types
* **Purpose:** Selects a signed or unsigned integer type based on the operands' bit widths and signedness.
* **Why:** Ensures that the resulting type uses the wider operand width and follows the rule that an unsigned operand produces an unsigned result.

##### `other(ZType a, ZType b)`
* **Purpose:** Returns the name of the other operand's type.
* **Why:** Helps construct error messages when certain type combinations are invalid.
