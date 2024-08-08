# Cindustry
A C-like programming language that compiles to the Mindustry processors language,
making it intuitive for developers familiar with C syntax.

## Quick Start
### 1. Main function
As in most C-like languages, Cindustry's scripts entry point is at the **main** function.
```cindustry
void main(){
    // Your code starts here
}
```
However, unlike in other languages, the main function always **cycles**.
Processors in Mindustry by design repeatedly execute their program,
which is useful for the most situations in the game.
Cindustry aims to preserve this trait with its main function.
However, if the situation requires a single run of the program,
the developer can add `stop()` function call at the end of the main function.
Return statement, on the other hand, corresponds to `end` instruction in Mindustry processors.
### 2. Variables
All variables must be preceded by its **type**.
Variables them self are not required to be initialized right-away,
but must be initialized before any operations with them.
To make variables immutable, use `const` before the variable declaration.
```cindustry
void main(){
    number a = 0;
    
    string b; 
    b = "abs";
    
    const bool c = true;
}
```
### 2.1. Types
Cindustry operates within the same types as Mindustry does,
so variables can be of these types: `number`, `string`, `building`, `content`, `unit`.
However, Cindustry adds new types in favor of type safety:
1. `any` — variables of this type will avoid type checks and can hold any value
2. `bool` — represents boolean value, corresponds to numeric 0 and 1 in Mindusty, used for conditions
3. `void` — denotes that function has no return value, cannot be a type of the variable
4. Enums — user defined enums

### 2.2 Global variables
Global variables are the variables defined outside the main function, and must be preceded with `global` keyword.
Regular variables that are defined in the main function will erase their value after the cycle.
Global variables will not.
In addition, global variables could be used in any function within the same module

```cindustry
global const number MAX = 100;
global number counter = 0;

void main(){
    ...
}
```

### 3. Loops and branching
Modern languages don't use jumps and gotos for a reason.
Cindustry features C-style loops, like while, do-while and for, as well as if and if-else statements.
In addition to them, Cindustry also has break and continue statements.
See [FizzBuzz example](https://github.com/OwnMind-ai/Cindustry/blob/fdb67966dd3383b585a4259254561b5c92335e4f/examples/fizzbuzz.cind).

### 4. Buildings and Memory
Before buildings that are connected to the processor can be used, the developer must specify their existence:
```cindusty
// use @[BUILDING NAME]
use @message1;
use @cell1;
```
Operator `@` is used to refer to the buildings with the name after the operator.
The resulting expression returns value of `building` type

```cindustry
bulding result = @message1;
```

### 4.1. Control and Sensor
Cindustry allows developers to get buildings state without `sensor` functions
and use them directly where they need them.
The syntax is following: `@buidlingName.fieldName`.
`@buildingName` can be replaced with the variable that typed as variable, or with a result of a function call.
See [Reactor Safety example](https://github.com/OwnMind-ai/Cindustry/blob/fdb67966dd3383b585a4259254561b5c92335e4f/examples/reactor.cind).

```cindustry
bool a = @switch1.enabled;
if (a && @reactor1.totalLiquids < 5)
    @reactor1.enabled = false;
```
### 4.2. Memory
Cindustry replaced `write` and `read` Mindustry's instruction with array-like syntax: 
```cindustry
number a = @cell1[0];
number b = @cell1[index];
@cell1[index] = c;
```
### 5. Function call's
Cindustry has typical for C-like languages function call syntax:
```cindustry
number a = call(param1, param2);
outer(inner(param1), param2);
```
### 5.1. Standard functions

Standard functions are functions that are available to the developer in everywhere in the code. Those include:
- `print(value)` — puts `value` into print buffer
- `printFlush(@building)` — flushes print buffer into `@buidling`
- `print(value, @buidling)` — puts `value` into print buffer and immediately flushes it into `@building`
- `read(@building, index)` — corresponds to `@building[index]`
- `write(value, @building, index)` — corresponds to `@building[index] = value`
- `getLink(number)` — returns `building` typed value, gets processor link
- `wait(number)` — waits `number` seconds
- `stop()` — stops the program

### 5.2. Built-in modules
There are currently two built-in modules present:
- `math` — contains functions for the uncommon Mindustry's math operations
- `draw` — contains functions required to work with displays

To import them, use:
```cindustry
import math;
import draw;
```

### 6. Functions
Developers can define their own functions to call in their program:
```cindustry
void main(){
    foo(getHi());
}

void foo(string message){
    print(message);
}

string getHi(){
    return "getHi";
}
```
### 7. Enums
Cindustry features enums to allow developers to create more accurate logic and functions:
```cindustry
enum Name{
    value1, value2
}

void main(){
    Name variable = Name.value1;
}
```

`draw` package and `radar` standard function use enums to allow developers to specify function's logic.
### 8. Imports
Developers can import other **.cind** files using following syntax:

```cindustry
import moduleName;  // Imports moduleName.cind
import dir.another.moduleName;  // Imports dir/another/moduleName.cind
```