# secd

A silly implementation of the
[SECD machine](https://en.wikipedia.org/wiki/SECD_machine), adapted from
_The Architecture of Symbolic Computers_.

## Ideas

Examples and exercises using the Clojure SECD

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

**NIL:**
```
s e (NIL . c) d => (nil . s) e c d
```
`NIL` is the simplest SECD instruction. It pushes `nil` onto the stack.
In SECD-land, `nil` is also the empty list. We'll use `NIL` before we
have to build up an argument list, for example, and then `CONS` elements
onto it. Stay tuned.

**LDC &mdash; Load constant:**
```
s e (LDC x . c) d => (x . s) e c d
```
`LDC` loads the next value from the code register onto the stack. For
instance, `s e (LDC 1 . c) d` would load the value 1 onto the stack. `s e
(LDC (1 2 3) . c) d` would load the list `(1 2 3)`.

**LD &mdash; Load from environment:**
```
s e (LD [y x] . c) d => (locate([y x], e) . s) e c d
```
`LD` loads a value from the current environment. I think now is a good
time for a quick aside about the environment register.

Like the other registers, the environment is a list. In fact, it's a
list of lists, and each list contained represents a scope. A possible
environment, for instance, may look like this:

```
((0  1  2)
 (10 11 12)
 (20 21 22)
 (30 31 32))
```

The earlier in the environment a list of values appears, the "closer" it
is in scope. During the execution of a function, arguments
to that function will be at the top of the environment. If that function
is inside another function, the arguments to that enclosing function
will be at index 1, and so on and so forth.

The `locate` function used in `LD`, then, suddenly makes sense: it
accepts a pair of coordinates and indexes into the 2-dimensional
environment. If we called the previous example environment `e`,
`locate([2 1], e)` would return the number 21.

TODO: More clear explanation

### Built-ins

Like all virtual machines, the SECD machine assumes a number of built-in
operations which take their arguments from the stack.

TODO: Expand?

**Unary:**
```
(x . s) e (OP . c) d => (OP(x) . s) e c d
```
Unary built-ins take one argument from the stack, evaluate the built-in
with that argument, and push the return value back on. (Notice that the
original argument as "disappeared" from the stack, and been replaced
with the result of the operation.)

Examples of unary built-ins:
```
ATOM - Returns 1 if its argument isn't a composite value (like a list), else 0
NULL - Returns 1 if its argument is nil or the empty list, else 0
CAR  - Returns the head of a list
CDR  - Returns the tail of a list
```

**Binary:**
```
(x y . s) e (OP . c) d => (OP(x,y) . s) e c d
```
Binary built-ins are exactly like their unary counterparts, except that
they take two arguments from the stack.

Examples of binary built-ins:
```
CONS - Prepend an item onto a list, returning the list
ADD  - Addition
SUB  - Subtraction
MTY  - Multiplication
DIV  - Division
EQ   - Equality of atoms
GT   - Greater than
LT   - Less than
GTE  - Greater than or equal to
LTE  - Less than or equal to
```

### Some Simple Examples

Some simple SECD examples using what we've learned, in Clojure:
```clj
;; do-secd accepts a vector representing the code register, and
;; recursively evaluates instructions until none are left. The value at
;; the top of the stack is returned.

(do-secd [NIL])
;;=> nil

(do-secd [LDC 1337])
;;=> 1337

;; (cons 1337 nil)
(do-secd [NIL LDC 1337 CONS])
;;=> (1337)

;; (cons 2448 (cons 1337 nil))
(do-secd [NIL
          LDC 1337 CONS
          LDC 2448 CONS])
;;=> (2448 1337)

;; (car (cons 2448 (cons 1337 nil)))
(do-secd [NIL
          LDC 1337 CONS
          LDC 2448 CONS
          CAR])
;;=> 2448

;; (+ 5 5)
(do-secd [LDC 5 LDC 5 ADD])
;;=> 10

;; (- 20 (+ 5 5))
(do-secd [LDC 5 LDC 5 ADD
          LDC 20
          SUB])
;;=> 10

;; (atom 1)
(do-secd [LDC 1 ATOM])
;;=> true
;; Note that the Clojure SECD implementation uses Clojure's true and
;; false to represent boolean values, instead of the traditional SECD 1
;; and 0.
```

### Branching Instructions

**SEL:**
```
(x . s) e (SEL then else . c) d => s e c? (c . d)

where c? is (if (not= x 0) then else)
```

**JOIN:**
```
s e (JOIN . c) (c' . d) => s e c' d
```

### Non-recursive Function Instructions

Explain closures.

**LDF:**
```
s e (LDF f . c) => ([f e] . s) e c d
```

**AP:**
```
([f e'] v . s) e (AP . c) d => nil (v . e') f (s e c . d)
```

**RTN:**
```
(x . z) e' (RTN . q) (s e c . d) => (x . s) e c d
```

### Recursive Function Instructions

**DUM:**
```
s e (DUM . c) d => s (nil . e) c d
```
nil is replaced by an atom in the Clojure implementation

**RAP:**
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
