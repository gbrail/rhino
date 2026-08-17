# JavaScript Threading and Workers Plan

When Rhino was first written, threads were new-ish, and Java had them. 
The threading contract for JavaScript was not as clear, so Rhino supports
a number of different ways to handle threading.

Today, JavaScript defines a memory model, and the most common use cases -- the browser
and Node.js -- explicitly support that. 

This document proposes a set of changes to allow Rhino embedders, if they wish, to
take advantage of standard JavaScript capabilities like Workers, Atomics, and message 
passing to build multithreaded systems that use Rhino in a standards-compliant way.

## Rhino Threading Invariants

Rhino supports, and must continue to support, a few basics of its threading model:

### Basics

In Rhino, a script consists of the following:

1) The script source itself and its associated AST and IR
2) Optional compiled bytecode representing the script
3) A top-level scope that is the root for all objects, including built-in types
4) A Context that maintains various aspects of execution state

Unless optional thread-safety features are used, the following rules must
be followed:

1) The script, once either rendered to an AST or optionally compiled, should be considered immutable and may be shread by many threads.
2) The top-level scope is not thread safe in any way and must be used by a single thread at a time
3) Each Context is bound to a specific thread and may not be used by another thread
4) A single JVM may contain any number of top-level scopes and contexts executing in different threads concurrently

### Single-threaded Context

The rhino Context object is the basis of the runtime engine of Rhino. It is used in
almost every internal call to maintain state. Context objects, since the beginning 
of this project, have been stored in a thread-specific data slot and are restricted
to use by a single thread at a time.

### Single-threaded objects

JavaScript objects in Rhino, including the top-level "global" object where the built-
in types are defined, are single-threaded by default. There is no guarantee of any kind
of correctness if they are shared between threads.

### Multi-threaded primitives

Any aspects of Rhino that are shared between Contexts and top-level scopes, such as 
caches, must be thread-safe so that many scripts can execute at the same time.

*TODO* is there a diagram we can insert here?

### Optional features

Rhino includes a few optional features that change some of theses invariants, which
are described in the appendix.

## Missing Features

This threading model maps well to the JavaScript memory model. In this model, multiple
scripts may execute in different threads in parallel, and there are only a few very
specific ways in which different threads may share data -- namely, shared array buffers,
the Atomics object, and message passing via the Workers API (which is technically part of
WHATWG and not ECMAScript).

The rest of this document describes what it would look like to implement the following
capabilities in Rhino. They are listed in reverse order of "what depends on what":

* Structured cloning
* Thread-capable event loop
* Inter-Context message passing 
* Workers (depends on structured cloning and an event loop)
* Shared ArrayBuffers
* Atomics class variable access and modifications
* Timer capability
* Lock support from Atomics object (depends on shared ArrayBuffers)
* Safe serialization format (depends on structured cloning)

*TODO* insert a dependency diagram or chart here

### Structured Cloning

This WHATWG specification describes a way to safely make clones of a tree of JavaScript
objects so they may be transferred to another script. It lays out how to handle the 
numerous edge cases that make up the majority of JavaScript such as native objects,
custom properties, prototypes, and the like. Implementing this in Rhino is straightforward.

### Thread-Capable Event Loop

Rhino originally had no concept of an "event loop" -- it just runs a script or calls
a Function and that's the end of it. With the advent of Promises we added a non-thread-safe
event queue tied to the Context object, and we drain this queue at the end of every
script and top-level function.

Other capabilities of all this will require a way to post events to run in a Context
in a different thread. It will require a program that embeds Rhino to be able to
pause while waiting for new messages, and also to decide when to pause.

It would not be appropriate for us to add this to the existing "microtask" queue
since that queue is not thread-safe and making it so would slow down non-threaded
applications. 

It makes sense, then, to create a top-level event queue abstraction so that Rhino
embedders can control when events are processed and include their own callbacks,
such as for network I/O.

However, the "asyncWait" functionality of the Atomics object depends on a rudimentary
timeout capability, which implies some more sophistication to the built-in Rhino
event loop, since scripts might use this that don't need an event loop. (Interestingly,
this is the only feature in ECMAScript that needs this.)

Whatever we do, we will retrofit the Rhino shell to use the new event loop, since it
already contains a simple timer capability via the setTimeout capability.

*TODO* This will take more careful design.

### Workers

The Worker API is a WHATWG (but not ECMASCript) standard that is implemented in 
browsers and in Node.js. It is not necessary for Rhino to implement all of it,
but the basic "Worker" API to launch a script in a new thread, and the postMessage
capability to send cloned objects between workers are the useful parts.

We will implement this API in a straigtforward way, building on the event loop
and structured cloning features. It should be a separate module since it is not
technically part of ECMAScript.

### Shared ArrayBuffers

Structured cloning will give us a way to send copies of buffers between scripts in
different threads via message passing. Shared array buffers give the threads a way
to share memory, with the equivalent of Java's "Atomic" family of primitive types,
a "volatile" equivalent, and a rudimentary locking capability.

As part of this we will do two other things:

First, we will modify the existing ArrayBuffer to use a ByteBuffer rather than a 
plain byte[] array. That will be necessary to support direct (off-heap) buffers.

Second, we will likely need to make shared array buffers into a direct buffers
(*TODO* a bit more research is required here). We may also want to consider 
whether other non-shared buffers, such as buffers larger than particular size,
be made direct buffers as well so that we can reduce heap pressure.

In order for shared arraybuffers to support the Java memory model, even
non-Atomics based changes to the buffer via the DataView class on numeric
types should be "tear free." That will require moving to VarHandle for those
operations rather than doing it in a bitwise way. (*TODO*) research and confirm this.

### Atomics Class (minus locking)

With shared array buffers in place (or even without) we can implement the various
methods of Atomics like "get," "set," "add," "compareExchange," and so on. The
best way to implement these in Java is to use the VarHandle type, which will allow
"tear-free" operations in all cases, and support the right memory semantics for
*some* data types.

For data types where VarHandle is incapable of atomic operations, we will have
to use good old-fashioned synchronization. This will certainly be necesary for
the UInt8 types, for example.

VarHandle is supported on more recent Android API versions -- see below.

### Timer Capability

The "waitAsync" capability of Atomics allows a thread to wait for a "notify"
to be called by returning a Promise that will be fulfilled only when the
lock is notified. This has an optional timeout, which means that we need a way
to deliver a result to a Promise after some time.

We can do this in one of two ways:

1) Spawn a separate timer service in another thread (possibly shared) that will use the inter-thread message-posting mechanism built above to deliver the notification
2) Embed a timer queue or wheel with the event loop mechanism and implement the timer within the same thread

The problem with the second mechanism is that not all Rhino embedders will use the
full event loop. There must be a way to make this work in a default WHATWG-free Rhino
environment with an optional customization capability.

### Atomics Locking Capability

With the shared buffers, atomics, and timers in place, we can implement the
"wait" and "notify" capabilities of the Atomics object without much new complication!

### Serialization Format

V8 builds on structured cloning to define a simple object serialization component that
works around the complexities and security holes of Java serialization by making it possible
to safely serialize objects that make sense to send between processes.

It would be a mistake to try and promise compatibility with V8, since its format is 
part of the implementation and not a formal spec, but we can use a very similar format.
This would provide an alternative to Java serialization which gives users a way to move
away from it and would let us even disable serialization by default.

# Open Issues

## Direct Buffers

Do we *need* direct buffers for shared ArrayBuffers to work? *TODO* research
which operations of VarHandle operate on which data types with direct vs
non-direct buffers. Once upon a time, direct buffers were slower to access
but they do reduce GC load at a certain point.

If we will never use direct buffers we could stick with byte[] as the backing
array for array buffers, with the same direct access via VarHandle.

## Endianness

Like the rest of Java, Rhino ByteBuffers default to big-endian mode. JavaScript
ArrayBuffers actually default to little-endian mode. (And that is faster, even 
in Java, on the vast majority of today's hardware now that people don't run on
Sun SPARC any more.) It is possible that existing JavaScript code may break in
Rhino if it assumes little-endian access. 

*TODO* research the implications of flipping this. 2.0 is a good time.

## Android

VarHandle is only available in Android API version 33 and up. Can we require that?
Otherwise we need a more complicated implementation.

## No-Park Mode

Browsers often support a mode in which Atomics.wait will throw an exception instead
of blocking the thread. Rhino embedders will also appreciate this! The question is,
should a blocking wait be allowed or disallowed by default?

## MemorySegment

The Java MemorySegment API may be an alternative to ByteBuffer for allocating buffers.
It's not clear whether it's a replacement or a complement.

This API is the only way in Java to implement the specified semantics of variable-length
array buffers and growable shared array buffers, which require that memory be
"reserved" but not "allocated." ByteBuffers do both, which could cause scripts
that depend on the specified behavior to throw OOM errors.

This would complicate Android support and would need a backup plan.

*TODO* understand the implications of this.

## Relaxing other Restrictions

Passing messages between scripts in many JavaScript environments seems to sometimes
require not only cloning objects, but serializing and deserializing them. (This is
what Node seems to do.) In Java, we can run many different scripts in different threads
safely in the same JVM with the same garbage collector, so we can pass cloned
objects between threads by just changing the prototype and parent scope objects.

Not all of the restrictions regarding locking with Atomics need hold in Java. 
The lock support in Atomics is designed for a world in which each lock is actually
a pointer to a "futex" structure in V8, and the lock support is all designed around
those limitations. For example, this is why locking only works with a shared array buffer.
We will not have the same restrictions in Java and might want to consider that.

Perhaps we could consider additional locking support that is more efficient because it
uses capabilties built in to Java, but that may not be worth the effort.

# Additional Data

## Appendix: Existing Rhino Threading Support

*TODO*: Research existing Rhino threading support, including the Rhino-specific
locking class (forget what it's called) and support for thread-safe objects.