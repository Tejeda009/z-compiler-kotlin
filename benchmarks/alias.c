#include <stdint.h>
#include <stdio.h>

static void step(uint64_t *restrict a, uint64_t *restrict b,
                 uint64_t *restrict c, uint64_t *restrict d) {
    *a = *a * *a + 1;
    *b = *b * *b + 2;
    *c = *c * *c + 3;
    *d = *d * *d + 4;
}

int main(int argc, char **argv) {
    (void)argv;
    const uint64_t base = (uint64_t)argc;
    uint64_t a = base + 1;
    uint64_t b = base + 2;
    uint64_t c = base + 3;
    uint64_t d = base + 4;

    for (uint64_t i = 0; i < 50000000; i++) {
        step(&a, &b, &c, &d);
    }

    printf("%llu\n", (unsigned long long)(a + b + c + d));
    return 0;
}
