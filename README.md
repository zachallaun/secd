# secd

A silly implementation of the
[SECD machine](https://en.wikipedia.org/wiki/SECD_machine), adapted from
_The Architecture of Symbolic Computers_.

## A Guide to SECD

The SECD machine is a virtual machine created by Peter Landin (of _Next
700 Langs_ fame) a long, long time ago. The machine targets functional
languages, and has a small and (relatively) simple instruction set.
Implementing and understanding the SECD machine is an interesting
exercise, and while it doesn't represent a "production-ready"
compilation target, it will force you to think differently, and is damn
fun to have around.

**SECD** stands for **S**tack, **E**nvironment, **C**ode, and **D**ump,
the four registers of the machine. Traditionally, each register points
to the head of a linked list in memory.

**S**tack: The stack is used for ephemeral storage. Functions will take
arguments from the stack and return values to the stack.

**E**nvironment: The environment contains arguments passed to functions,
as well other values within the scope of that function.

**C**ode: The code (or control) registerm contains instructions to the
machine. Executing one of these instructions can change any of the four
registers &mdash; including the code register itself. The instruction on
the top of the code register is the next to be executed, and execution
continues until the `STOP` instruction is reached.

**D**ump: The dump is used to persist copies of the registers, which, as
we'll see, allows us to create `JUMP`-like instructions without "losing
our place" in execution.

## Axiomatic Semantics

Axiomatic Semantics is an approach to describing programs in terms of
changes in state &mdash; not in terms of implementation. SECD
instructions are defined axiomatically, which gives implementors total
control over the way instructions work under the hood. (For instance, my
implementation of the SECD machine is largely functional, and does not
rely on linked-lists. Values and registers are, for the most part,
immutable.)

### An Axiomatic Notation for SECD

I will borrow Kogge's notation. Given a machine state, the next state of
the machine can be described in terms of transformations to the
registers.

```
s e c d => s' e' c' d'
```

where `s`, `e`, `c`, and `d` represent the initial state of each
register, and the primes represent the new states.

While the single letter `s` is taken to represent any possible
configuration of the stack register, we also need a way to match on more
specific states. Since each register is a list, we can _destructure_
registers to match on internal components.

`(123 . s)` matches the configuration where the top item of the stack is
the value 123.

`(x y . s) e (ADD . c) d` matches the register configuration where the
`ADD` instruction is on the top of the code register, and at least two
items, `x` and `y`, are on the stack.

Note that the `.` (period) inside of a list can be read as, "and the
rest," such that `(x y . s)` can be read as, "any x, any y, and the rest
of the stack."

Given this, an instruction `FOO` can be described axiomatically using
something similar to the following:

```
(x . s) e (FOO . c) d => (bar . s) e c d
```

## The Instruction Set

For reference, the instructions we will cover are as follows:

```
Basic instructions:
NIL    - Nil
LDC    - Load constant
LD     - Load (from environment)

Built-ins:
CAR, CDR, ATOM, et al. -  Unary built-in operations
ADD, SUB, CONS, et al. -  Binary built-in operations

Branching instructions:
SEL    - Select
JOIN   - Join

Non-recursive function instructions:
LDF    - Load function
AP     - Apply
RTN    - Return

Recursive function instructions:
DUM    - Dummy
RAP    - Recursive apply

Auxiliary instructions:
READC  - Read character
WRITEC - Write character
STOP   - Stop execution
```

### Basic Instructions

**NIL**:
```
s e (NIL . c) d => (nil . s) e c d
```

**LDC**:
```
s e (LDC x . c) d => (x . s) e c d
```

**LD**:
```
s e (LD [x y] . c) d => (locate([x y]) . s) e c d
```

### Built-ins

**Unary**:
```
(x . s) e (OP . c) d => (OP(x) . s) e c d
```

**Binary**:
```
(x y . s) e (OP . c) d => (OP(x,y) . s) e c d
```

### Branching Instructions

**SEL**:
```
(x . s) e (SEL then else . c) d => s e c? (c . d)

where c? is (if (not= x 0) then else)
```

**JOIN**:
```
s e (JOIN . c) (c' . d) => s e c' d
```

### Non-recursive Function Instructions

Explain closures.

**LDF**:
```
s e (LDF f . c) => ([f e] . s) e c d
```

**AP**:
```
([f e'] v . s) e (AP . c) d => nil (v . e') f (s e c . d)
```

**RTN**:
```
(x . z) e' (RTN . q) (s e c . d) => (x . s) e c d
```

### Recursive Function Instructions

**DUM**:
```
s e (DUM . c) d => s (nil . e) c d
```
nil is replaced by an atom in the Clojure implementation

**RAP**:
```
([f (nil.e)] v . s) (nil . e) (RAP . c) d
=>
nil (rplaca((nil . e), v) . e) f (s e c . d)
```

### Auxiliary Instructions

**WRITEC**
**READC**
**STOP**

## Writing Simple SECD Programs

## The SECD "Hello, world" &mdash; Recursion

## Means of Abstraction

Higher order functions?

Map, Reduce, Filter

## SECD as a Compilation Target

## References

Kogge, Peter M. 1991. _The Architecture of Symbolic Computers_.

## License

Copyright © 2012 Zach Allaun

Distributed under the Eclipse Public License, the same as Clojure.
