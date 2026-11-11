#include <stdint.h>
#include <stdio.h>

static uint64_t fib(uint64_t n) {
    uint64_t a = 0;
    uint64_t b = 1;
    uint64_t c = 0;

    if (n == 0) return a;

    for (uint64_t i = 2; i <= n; i++) {
        c = a + b;
        a = b;
        b = c;
    }

    return b;
}

int main(void) {
    const uint64_t passes = 20000000;
    uint64_t last = 0;

    for (uint64_t i = 0; i < passes; i++) {
        last += fib(i % 90);
    }

    printf("%llu\n", (unsigned long long)last);
    return 0;
}
