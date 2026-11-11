#include <stdint.h>
#include <stdio.h>

static int64_t fib(int64_t n) {
    if (n < 2) return n;
    return fib(n - 1) + fib(n - 2);
}

int main(int argc, char **argv) {
    (void)argv;
    const int64_t seed = argc;
    const int64_t n = 40 + seed;

    printf("%lld\n", (long long)fib(n));
    return 0;
}
