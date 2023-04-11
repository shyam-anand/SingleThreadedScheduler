# SingleThreadedScheduler

This is a simple project I built, mainly as a use-case for multithreading.

The `SingleThreadedScheduler` exposes a method `submitWithDelay` that accepts the work a `Runnable` and the delay after which the work should be executed.
The class uses a Reentrant lock implement the blocking mechanism.
