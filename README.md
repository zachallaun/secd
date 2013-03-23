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

**C**ode: The code (or control) register contains instructions to the
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

TODO: Formalize list dot notation

While the single letter `s` is taken to represent any possible
configuration of the stack register, we also need a way to match on more
specific states. Since each register is a list, we can _destructure_
registers to match on internal components.

`(123.s)` matches the configuration where the top item of the stack is
the value 123.

`(x y.s) e (ADD.c) d` matches the register configuration where the
`ADD` instruction is on the top of the code register, and at least two
items, `x` and `y`, are on the stack.

Note that the `.` (period) inside of a list can be read as, "and the
rest," such that `(x y.s)` can be read as, "any x, any y, and the rest
of the stack."

Given this, an instruction `FOO` can be described axiomatically using
something similar to the following:

```
(x.s) e (FOO.c) d => (bar.s) e c d
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
s e (NIL.c) d => (nil.s) e c d
```
`NIL` is the simplest SECD instruction. It pushes `nil` onto the stack.
In SECD-land, `nil` is also the empty list. We'll use `NIL` before we
have to build up an argument list, for example, and then `CONS` elements
onto it. Stay tuned.

**LDC &mdash; Load constant:**
```
s e (LDC x.c) d => (x.s) e c d
```
`LDC` loads the next value from the code register onto the stack. For
instance, `s e (LDC 1 . c) d` would load the value 1 onto the stack. `s e
(LDC (1 2 3).c) d` would load the list `(1 2 3)`.

**LD &mdash; Load from environment:**
```
s e (LD [y x].c) d => (locate([y x], e).s) e c d
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
(x.s) e (OP.c) d => (OP(x).s) e c d
```
Unary built-ins take one argument from the stack, evaluate the built-in
with that argument, and push the return value back on. (Notice that the
original argument as "disappeared" from the stack, and been replaced
with the result of the operation.)

Examples of unary built-ins:
```
ATOM - Returns true if its argument isn't a composite value (like a list), else false
NULL - Returns true if its argument is nil or the empty list, else false
CAR  - Returns the head of a list
CDR  - Returns the tail of a list
```
Note: in the original SECD machine, the boolean values true and false
where represented as 1 and 0, respectively. I've chosen to instead use
Clojure's `true` and `false` for clarity and simplicity.

**Binary:**
```
(x y.s) e (OP.c) d => (OP(x,y).s) e c d
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
```

### Branching Instructions

Now that we know how to use some simple instructions, let's take a look
at some _useful_ instructions: `SEL` and `JOIN`, which together give us
something akin to `if-then-else`.

**SEL &mdash; Select branch:**
```
(x.s) e (SEL ct cf.c) d => s e c? (c.d)
where c? is (if (not= x false) ct cf)
```
`SEL` expects to be followed by two other code lists representing the
_then_ and _else_ branches of the statement (here seen as `ct`
and `cf`). `SEL` then checks the top item on the stack, replacing the
code register with `ct` if that item is not false and with `cf` if it
is. The remainder of the code register following the two branches, `c`,
is pushed onto the dump, where it can be recovered later.

**JOIN &mdash; Join with main control:**
```
s e (JOIN.c) (c'.d) => s e c' d
```
`JOIN` is used to return control to the code register saved during the
`SEL`. It is expected, then, that the `ct` and `cf` code lists seen
above end with `JOIN`.

**Trivial branching examples:**

```clj
;; (if (= 0 (- 1 1)) true false)
(do-secd [LDC 1 LDC 1 SUB   ;; (- 1 1)
          LDC 0 EQ          ;; (= 0 0)
          SEL
          [LDC true JOIN]   ;; then-branch
          [LDC false JOIN]] ;; else-branch
;;=> true

;; (+ 10 (if (nil? nil) 10 20))
(do-secd [NIL NULL
          SEL
          [LDC 10 JOIN]
          [LDC 20 JOIN]
          LDC 10 ADD]
;;=> 20
```

### Non-recursive Function Instructions

A _function closure_ is a function that retains references to variables
in-scope during that functions definition, even after the function has
left that scope. Consider the following Clojure example:

```clj
(defn alwaysfn [x]
  (fn [] x))

(def always-five (alwaysfn 5))

(always-five)
;;=> 5
```
In this example, `alwaysfn` establishes a closure around `x` &mdash; the
function returned from it "remembers" `x`'s value, even though `x` is no
longer in scope. Because of this, we can say that the returned function
is _closed over_ `x`.

This is a useful construct, and the SECD machine supports it. Since
local variables and function arguments are accessible through the
environment register, we can easily create closures by simply packing up
functions with the environment in which they were defined into a
`[function context]` pair.

**LDF &mdash; Load function:**
```
s e (LDF f.c) => ([f e].s) e c d
```
`LDF` is used to create the `[function context]` closure, packing up
some function `f` (just a list of instructions) and the current
environment into a pair, and pushing it onto the stack.

**AP &mdash; Apply function:**
```
([f e'] v.s) e (AP.c) d => nil (v.e') f (s e c.d)
```
`AP` does the dirty work, transferring control to the function
instructions and "installing" that functions arguments and context into
the environment register. The remaining stack, environment and code
registers are also pushed onto the dump so that execution can continue
after the function has executed.

TODO: More in depth here, specifically re: function args

**RTN &mdash; Return control:**
```
(x.z) e' (RTN.q) (s e c.d) => (x.s) e c d
```
A `RTN` instruction is expected at the end of any function list, and is
used to return a value. When the `RTN` is encountered, the top value of
the stack (`x`) is saved, registers are restored from the dump, and that
saved value is pushed onto the restored stack.

### Recursive Function Instructions

**DUM &mdash; Dummy environment:**
```
s e (DUM.c) d => s (nil.e) c d
```
nil is replaced by an atom in the Clojure implementation

**RAP &mdash; Apply recursive function:**
```
([f (nil.e)] v.s) (nil.e) (RAP.c) d
=>
nil (rplaca((nil.e), v).e) f (s e c.d)
```

### Auxiliary Instructions

**WRITEC**
**READC**
**STOP**

## Writing Simple SECD Programs

## The SECD "Hello, world" &mdash; Recursion

In many modern languages, _Hello, world_ is basically the simplest a
program can get. In SECD-land, this is not so. We don't have a
`println` &mdash; we only have `WRITEC` &mdash; so we have to define our
own `println` first!

The function is quite simple, really. It will accept one argument, a
list of integers (our model of characters), print the first character
from the list, and recurse with the tail. Once the sequence is empty,
we'll print a newline character.

TODO: Explain general recursive pattern

```clj
;; This is obviously not hello world, but it should be
(def hello-world [72 101 108 108 111 44 32 119 111 114 108 100 33])

(do-secd [NIL DUM
          LDF [LD [0 0] NULL
               TEST [LDC 10 WRITEC RTN]
               LD [0 0] CAR WRITEC
               NIL LD [0 0] CDR CONS
               LD [1 0] AP
               RTN]
          CONS
          LDF [NIL LDC hello-world CONS
               LD [0 0] AP RTN]
          RAP])
;; prints "Hello, world!"
```

## Optimizations &mdash; Extending the Instruction Set

**TEST**
**DAP**
**AA**
**MA**

## Means of Abstraction

As mentioned earlier, the SECD machine is particularly well suited to
support functional programming languages. It is no surprise, then, that
common abstractions in such languages can be modeled. One such common
abstraction is reduce, or fold.

reduce abstracts over recursion

recursion seems abstract in imperative languages, but can be seen as
relatively "low level" in functional languages

this is because reduce requires a manual handling of a base-case, in the
same way that a while loop would. Handling it poorly leads to infinite
recursion.

reduce happens to be a particularly powerful function, and other common
functions, like map and filter, can be trivially defined in terms of
reduce.

Defining map and filter in terms of reduce in Clojure:

```clj
(defn reduce' [f acc sequence]
  (if (seq sequence)
    (recur f (f acc (first sequence)) (rest sequence))
    acc))

(reduce' + 0 [0 1 2 3 4])
;;=> 10

(defn map' [f sequence]
  (reduce' #(conj %1 (f %2)) [] sequence))

(map' inc [0 1 2 3 4])
;;=> [1 2 3 4 5]

(defn filter' [condition sequence]
  (reduce' #(if (condition %2) (conj %1 %2) %1) [] sequence))

(filter' even? [0 1 2 3 4])
;;=> [0 2 4]
```

Doing the same, but in SECD:

```clj
(def secd-reduce          ;; fn args: [f acc sequence]
  [LD [0 2] NULL          ;; load sequence, check if null
   TEST [LD [0 1] RTN]    ;; if null, load acc and return
   NIL                    ;; build args for recur
   LD [0 2] CDR CONS      ;; cons (rest sequence) onto recur argslist
   NIL                    ;; build args for calling f
   LD [0 2] CAR CONS      ;; cons first(sequence) onto f argslist
   LD [0 1] CONS          ;; cons acc onto f argslist
   LD [0 0] AP            ;; load f and apply
   CONS                   ;; cons result onto recur argslist
   LD [0 0] CONS          ;; cons f onto recur argslist
   LD [1 0] DAP           ;; load recursive fn and apply
   RTN])

(def secd-add [LD [0 1] LD [0 0] ADD RTN])

(do-secd [DUM NIL
          LDF secd-reduce CONS
          LDF [NIL
               LDC [0 1 2 3 4] CONS
               LDC 0 CONS
               LDF secd-add CONS
               LD [0 0] AP RTN]
          RAP])
;;=> 10

(def secd-reverse
  [DUM NIL
   LDF secd-reduce CONS
   LDF [NIL
        LD [1 0] CONS
        NIL CONS
        LDF [LD [0 0]
             LD [0 1]
             CONS
             RTN]
        CONS
        LD [0 0] DAP RTN]
   RAP])

(do-secd [NIL LDC [0 1 2 3 4] CONS
          LDF secd-reverse AP])

;; TODO: Shouldn't have to reverse result?
(def secd-map                   ;; fn args: [f sequence]
  [DUM NIL
   LDF secd-reduce CONS         ;; load recursive reduce
   LDF [NIL                     ;; nil to be used for reverse call
        NIL                     ;; build args for reduce call
        LD [1 1] CONS           ;; cons sequence onto args
        NIL CONS                ;; cons nil onto args
        LDF [LD [0 0]           ;; in fn to reduce, load accumulator
             NIL LD [0 1] CONS  ;; cons reduce item onto args for f
             LD [2 0] AP        ;; apply f
             CONS               ;; cons result onto accumulator
             RTN]
        CONS                    ;; cons reduce fn onto reduce args
        LD [0 0] AP             ;; load reduce and apply
        CONS                    ;; cons reduce value onto list
        LDF secd-reverse AP     ;; reverse
        RTN]
   RAP])

(def secd-inc [LDC 1 LD [0 0] ADD RTN])

(do-secd [NIL
          LDC [0 1 2 3 4] CONS
          LDF secd-inc CONS
          LDF secd-map AP])
;;=> (1 2 3 4 5)

(def secd-filter
  [DUM NIL
   LDF secd-reduce CONS
   LDF [NIL
        NIL
        LD [1 1] CONS
        NIL CONS
        LDF [NIL LD [0 1] CONS ;; load reduce item and cons onto
                               ;; args for condition
             LD [2 0] AP       ;; load condition and apply
             TEST [LD [0 0]
                   LD [0 1]
                   CONS RTN]
             LD [0 0] RTN]
        CONS
        LD [0 0] AP
        CONS
        LDF secd-reverse AP
        RTN]
   RAP])

(def secd-even? [LDC 0
                 LDC 2 LD [0 0] MOD
                 EQ RTN])

(do-secd [NIL
          LDC [0 1 2 3 4] CONS
          LDF secd-even? CONS
          LDF secd-filter AP])
;;=> (0 2 4)
```

## SECD as a Compilation Target

## References

Kogge, Peter M. 1991. _The Architecture of Symbolic Computers_.

## License

Copyright © 2012 Zach Allaun

Distributed under the Eclipse Public License, the same as Clojure.
